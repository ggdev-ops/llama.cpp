#include <jni.h>
#include <iomanip>
#include <cmath>
#include <string>
#include <vector>
#include <sstream>
#include <iostream>
#include <thread>
#include <unistd.h>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"

// Desktop logging (mimicking Android's log levels)
#define LOGv(...) do { if (g_log_level >= 0) printf(" [VERBOSE] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGd(...) do { if (g_log_level >= 1) printf(" [DEBUG] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGi(...) printf(" [INFO] " __VA_ARGS__); printf("\n")
#define LOGw(...) printf(" [WARN] " __VA_ARGS__); printf("\n")
#define LOGe(...) fprintf(stderr, " [ERROR] " __VA_ARGS__); fprintf(stderr, "\n")

// Global log level (0=verbose, 1=debug, 2=info, 3=warn, 4=error)
static int g_log_level = 2; // Default to INFO

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources and configuration
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 8;
constexpr int   N_THREADS_HEADROOM      = 1;  // Leave one core for system

constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;
constexpr float DEFAULT_SAMPLER_TOP_P   = 0.95f;
constexpr int   DEFAULT_SAMPLER_TOP_K   = 40;
constexpr float DEFAULT_SAMPLER_PENALTY  = 1.1f;

static llama_model   * g_model = nullptr;
static llama_context * g_context = nullptr;
static llama_batch     g_batch;
static common_sampler * g_sampler = nullptr;
static common_chat_templates_ptr g_chat_templates;

// Store context parameters for recreation
static llama_context_params g_ctx_params;

/**
 * Long-term states for conversation management
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_position = 0;
static llama_pos current_position = 0;

/**
 * Short-term states for generation loop
 */
static llama_pos stop_generation_position = 0;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

// Track the last position in KV cache
static llama_pos last_kv_pos = -1;

static void llama_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void) user_data;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: fprintf(stderr, "[GGML ERROR] %s", text); break;
        case GGML_LOG_LEVEL_WARN:  fprintf(stdout, "[GGML WARN] %s", text); break;
        case GGML_LOG_LEVEL_INFO:  fprintf(stdout, "[GGML INFO] %s", text); break;
        case GGML_LOG_LEVEL_DEBUG: if (g_log_level <= 1) fprintf(stdout, "[GGML DEBUG] %s", text); break;
        default:                   fprintf(stdout, "[GGML] %s", text); break;
    }
    fflush(stdout);
}

/**
 * Recreate context (replaces old KV cache clear functions)
 */
static bool recreate_context() {
    if (!g_model) {
        LOGe("Cannot recreate context: model not loaded");
        return false;
    }
    
    LOGi("Recreating context...");
    
    // Free old context if it exists
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    
    // Create new context with same parameters
    g_context = llama_init_from_model(g_model, g_ctx_params);
    if (!g_context) {
        LOGe("Failed to recreate context");
        return false;
    }
    
    // Reinitialize batch
    llama_batch_free(g_batch);
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    
    // Reset position tracking
    last_kv_pos = -1;
    
    LOGi("Context recreated successfully");
    return true;
}

/**
 * Reset long-term states (conversation history and KV cache)
 */
static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");

    if (clear_kv_cache && g_context) {
        // Recreate context to clear KV cache
        if (!recreate_context()) {
            LOGe("Failed to recreate context during reset");
        }
    }
    last_kv_pos = -1;
}

/**
 * Reset short-term states (generation loop)
 */
static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

/**
 * Format and add a message to chat history
 */
static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, false);
    chat_msgs.push_back(new_msg);
    LOGd("Formatted and added %s message: %s", role.c_str(), formatted.c_str());
    return formatted;
}

/**
 * Decode tokens in batches with proper position management
 */
static int decode_tokens_in_batches(
        const std::vector<llama_token> &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    
    LOGd("Decoding %d tokens starting at position %d", (int) tokens.size(), start_pos);
    
    // CRITICAL FIX: Verify position continuity
    if (last_kv_pos >= 0 && start_pos != last_kv_pos + 1) {
        LOGw("Position discontinuity detected! last_kv_pos=%d, start_pos=%d", last_kv_pos, start_pos);
        // If we have a discontinuity, we need to recreate context or adjust
        if (!recreate_context()) {
            LOGe("Failed to recover from position discontinuity");
            return 1;
        }
        // Reset positions after context recreation
        current_position = 0;
        last_kv_pos = -1;
    }
    
    common_batch_clear(g_batch);
    
    // Add tokens to batch with sequential positions
    for (int i = 0; i < (int) tokens.size(); i++) {
        const llama_token token_id = tokens[i];
        // Ensure positions are sequential from last_kv_pos + 1
        const llama_pos position = (last_kv_pos >= 0) ? last_kv_pos + 1 + i : start_pos + i;
        const bool want_logit = compute_last_logit && (i == (int) tokens.size() - 1);
        common_batch_add(g_batch, token_id, position, {0}, want_logit);
        LOGv("Added token %d at position %d", token_id, position);
    }
    
    // Decode batch
    if (llama_decode(g_context, g_batch) != 0) {
        LOGe("llama_decode failed");
        return 1;
    }
    
    // Update last known KV cache position
    last_kv_pos = (last_kv_pos >= 0) ? last_kv_pos + tokens.size() : start_pos + tokens.size() - 1;
    LOGd("Updated last_kv_pos to %d", last_kv_pos);
    
    return 0;
}

/**
 * UTF-8 validation
 */
static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

/**
 * Get available backends (for benchmarking)
 */
static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_initNative(JNIEnv *env, jobject /*unused*/, jint log_level) {
    g_log_level = log_level;
    llama_log_set(llama_log_callback, nullptr);
    llama_backend_init();
    LOGi("llama.cpp backend initialized with log level %d", log_level);
}

JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    const char *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGi("Loading model from: %s", model_path);
    
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    
    g_model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    
    if (!g_model) {
        LOGe("Failed to load model");
        return 1;
    }
    
    LOGi("Model loaded successfully");
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_prepare(JNIEnv *env, jobject /*unused*/, 
                                                      jint context_size, jint n_predict) {
    if (!g_model) {
        LOGe("Model not loaded");
        return 1;
    }
    
    // Thread configuration
    int n_threads = std::thread::hardware_concurrency();
    n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, n_threads - N_THREADS_HEADROOM));
    LOGi("Using %d threads", n_threads);
    
    // Context parameters
    g_ctx_params = llama_context_default_params();
    const int trained_ctx = llama_model_n_ctx_train(g_model);
    const int requested_ctx = (context_size > 0) ? context_size : DEFAULT_CONTEXT_SIZE;
    
    if (requested_ctx > trained_ctx) {
        LOGw("Model trained with %d context, requesting %d - may cause issues", 
             trained_ctx, requested_ctx);
    }
    
    g_ctx_params.n_ctx = requested_ctx;
    g_ctx_params.n_batch = BATCH_SIZE;
    g_ctx_params.n_ubatch = BATCH_SIZE;
    g_ctx_params.n_threads = n_threads;
    g_ctx_params.n_threads_batch = n_threads;
    
    g_context = llama_init_from_model(g_model, g_ctx_params);
    if (!g_context) {
        LOGe("Failed to create context");
        return 1;
    }
    
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    
    // Initialize sampler with defaults
    common_params_sampling sparams;
    sparams.temp = DEFAULT_SAMPLER_TEMP;
    sparams.top_p = DEFAULT_SAMPLER_TOP_P;
    sparams.top_k = DEFAULT_SAMPLER_TOP_K;
    sparams.penalty_repeat = DEFAULT_SAMPLER_PENALTY;
    g_sampler = common_sampler_init(g_model, sparams);
    
    // Reset position tracking
    last_kv_pos = -1;
    
    LOGi("Context prepared successfully");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_resetContextNative(JNIEnv *env, jobject) {
    reset_long_term_states(true);
    LOGi("Context reset complete");
}

JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_updateSamplingParams(
    JNIEnv *env, jobject, jfloat temp, jfloat top_p, jint top_k, jfloat penalty) {
    
    if (g_sampler) {
        common_sampler_free(g_sampler);
    }
    
    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_p = top_p;
    sparams.top_k = top_k;
    sparams.penalty_repeat = penalty;
    
    g_sampler = common_sampler_init(g_model, sparams);
    LOGd("Sampling params updated: temp=%.2f, top_p=%.2f, top_k=%d, penalty=%.2f", 
         temp, top_p, top_k, penalty);
}

JNIEXPORT jstring JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, 
                                                          jint pp, jint tg, jint pl, jint nr) {
    if (!g_model) {
        return env->NewStringUTF("Model not loaded");
    }
    
    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;
    
    for (int nri = 0; nri < nr; nri++) {
        LOGi("Benchmark run %d/%d: pp=%d, tg=%d", nri + 1, nr, pp, tg);
        
        // Create fresh context for each benchmark run
        llama_context_params ctx_params = g_ctx_params;
        ctx_params.n_ctx = pp + tg * pl + 64; // Ensure enough context
        llama_context *ctx = llama_init_from_model(g_model, ctx_params);
        if (!ctx) {
            LOGe("Failed to create benchmark context");
            continue;
        }
        
        llama_batch bench_batch = llama_batch_init(BATCH_SIZE, 0, 1);
        
        // Prompt processing benchmark
        common_batch_clear(bench_batch);
        for (int i = 0; i < pp; i++) {
            common_batch_add(bench_batch, 0, i, {0}, false);
        }
        bench_batch.logits[bench_batch.n_tokens - 1] = true;
        
        auto t_pp_start = ggml_time_us();
        if (llama_decode(ctx, bench_batch) != 0) {
            LOGe("llama_decode failed during prompt processing");
        }
        auto t_pp_end = ggml_time_us();
        
        // Text generation benchmark
        auto t_tg_start = ggml_time_us();
        for (int i = 0; i < tg; i++) {
            common_batch_clear(bench_batch);
            for (int j = 0; j < pl; j++) {
                common_batch_add(bench_batch, 0, pp + i, {j}, true);
            }
            if (llama_decode(ctx, bench_batch) != 0) {
                LOGe("llama_decode failed during text generation");
            }
        }
        auto t_tg_end = ggml_time_us();
        
        llama_batch_free(bench_batch);
        llama_free(ctx);
        
        double t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        double t_tg = double(t_tg_end - t_tg_start) / 1000000.0;
        
        double speed_pp = double(pp) / t_pp;
        double speed_tg = double(pl * tg) / t_tg;
        
        pp_avg += speed_pp;
        tg_avg += speed_tg;
        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;
        
        LOGi("Run %d: pp %.2f t/s, tg %.2f t/s", nri + 1, speed_pp, speed_tg);
    }
    
    // Calculate averages and standard deviations
    pp_avg /= double(nr);
    tg_avg /= double(nr);
    
    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    }
    
    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));
    
    double model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    double model_n_params = double(llama_model_n_params(g_model)) / 1e9;
    
    std::string backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    
    return env->NewStringUTF(result.str().c_str());
}

JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_processSystemPrompt(JNIEnv *env, jobject, jstring jprompt) {
    const char *prompt = env->GetStringUTFChars(jprompt, 0);
    LOGi("Processing system prompt");
    
    // Reset everything for new conversation
    reset_long_term_states(true);
    
    // Format system prompt
    std::string formatted_prompt(prompt);
    bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    
    if (has_chat_template) {
        formatted_prompt = chat_add_and_format(ROLE_SYSTEM, prompt);
    }
    
    // Tokenize
    auto tokens = common_tokenize(g_context, formatted_prompt, has_chat_template, has_chat_template);
    
    // Check context size
    const int max_size = g_ctx_params.n_ctx - OVERFLOW_HEADROOM;
    if ((int) tokens.size() > max_size) {
        LOGe("System prompt too long: %d tokens, max %d", (int) tokens.size(), max_size);
        env->ReleaseStringUTFChars(jprompt, prompt);
        return 1;
    }
    
    // Decode starting from position 0
    if (decode_tokens_in_batches(tokens, 0, false) != 0) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return 2;
    }
    
    // Update positions
    current_position = tokens.size();
    system_prompt_position = current_position;
    
    env->ReleaseStringUTFChars(jprompt, prompt);
    LOGi("System prompt processed, position: %d", current_position);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_processUserPrompt(JNIEnv *env, jobject, 
                                                                 jstring jprompt, jint n_predict) {
    const char *prompt = env->GetStringUTFChars(jprompt, 0);
    LOGi("Processing user prompt, n_predict=%d, current_position=%d", n_predict, current_position);
    
    // Reset short-term states
    reset_short_term_states();
    
    // Format user prompt
    std::string formatted_prompt(prompt);
    bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    
    if (has_chat_template) {
        formatted_prompt = chat_add_and_format(ROLE_USER, prompt);
    }
    
    // Tokenize
    auto tokens = common_tokenize(g_context, formatted_prompt, has_chat_template, has_chat_template);
    
    // Truncate if too long
    const int max_size = g_ctx_params.n_ctx - current_position - OVERFLOW_HEADROOM;
    if ((int) tokens.size() > max_size) {
        int skipped = tokens.size() - max_size;
        tokens.resize(max_size);
        LOGw("User prompt truncated, skipped %d tokens", skipped);
    }
    
    // CRITICAL FIX: Decode with last logit for generation, starting from current_position
    if (decode_tokens_in_batches(tokens, current_position, true) != 0) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return 2;
    }
    
    // Update positions
    current_position += tokens.size();
    stop_generation_position = current_position + n_predict;
    
    env->ReleaseStringUTFChars(jprompt, prompt);
    LOGi("User prompt processed, current position: %d, stop at: %d, last_kv_pos: %d", 
         current_position, stop_generation_position, last_kv_pos);
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_generateNextToken(JNIEnv *env, jobject) {
    // Check if we've reached stop position
    if (stop_generation_position > 0 && current_position >= stop_generation_position) {
        LOGi("Reached stop position %d", stop_generation_position);
        return nullptr;
    }
    
    // CRITICAL FIX: Verify position continuity before generation
    if (last_kv_pos >= 0 && current_position != last_kv_pos + 1) {
        LOGw("Position mismatch before generation: last_kv_pos=%d, current_position=%d", 
             last_kv_pos, current_position);
        // Adjust current_position to match KV cache
        current_position = last_kv_pos + 1;
    }
    
    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);
    
    // Check for end of generation
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("EOG token generated, stopping");
        if (assistant_ss.tellp() > 0) {
            chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        }
        return nullptr;
    }
    
    // Decode the new token at the correct position
    common_batch_clear(g_batch);
    common_batch_add(g_batch, new_token_id, current_position, {0}, true);
    
    LOGv("Generating token %d at position %d", new_token_id, current_position);
    
    if (llama_decode(g_context, g_batch) != 0) {
        LOGe("llama_decode failed for generated token");
        return nullptr;
    }
    
    // Update position tracking
    last_kv_pos = current_position;
    current_position++;
    
    // Convert token to text with UTF-8 handling
    auto new_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_chars;
    
    // Return valid UTF-8 strings
    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
        LOGv("Generated token: %s", result ? "valid UTF-8" : "null");
    } else {
        // Return empty string for partial UTF-8
        result = env->NewStringUTF("");
        LOGv("Partial UTF-8, caching");
    }
    
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_getCurrentAssistantMessageNative(JNIEnv *env, jobject) {
    std::string current = assistant_ss.str();
    if (!current.empty()) {
        return env->NewStringUTF(current.c_str());
    }
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_unload(JNIEnv *env, jobject) {
    LOGi("Unloading model and freeing resources");
    
    reset_long_term_states(false);
    
    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    
    g_chat_templates.reset();
    
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    llama_batch_free(g_batch);
    current_position = 0;
    last_kv_pos = -1;
    
    LOGi("Unload complete");
}

JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_shutdown(JNIEnv *env, jobject) {
    LOGi("Shutting down llama.cpp backend");
    llama_backend_free();
}

} // extern "C"
