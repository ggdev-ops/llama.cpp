#include <jni.h>
#include <iomanip>
#include <cmath>
#include <string>
#include <vector>
#include <sstream>
#include <iostream>
#include <thread>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"

// Desktop logging
#define LOGi(...) printf(" [INFO] " __VA_ARGS__); printf("\n")
#define LOGe(...) fprintf(stderr, " [ERROR] " __VA_ARGS__); fprintf(stderr, "\n")
#define LOGw(...) printf(" [WARN] " __VA_ARGS__); printf("\n")

// Static global variables to manage the Llama model and inference state across JNI calls.
// These are declared static to maintain their state throughout the application's lifecycle
// and are shared across all JNI calls from the Java/Kotlin side.
static llama_model   * g_model = nullptr;
static llama_context * g_context = nullptr;
static llama_batch     g_batch;
static common_sampler * g_sampler = nullptr;
static common_chat_templates_ptr g_chat_templates;
// State management
static std::vector<common_chat_msg> chat_msgs;
static llama_pos current_position = 0;

// State management for chat interactions.
// `chat_msgs`: Stores the history of chat messages (system and user prompts),
//              which is crucial for maintaining conversational context.
// `current_position`: Tracks the current token position within the Llama context,
//                     essential for managing sequence length and decoding.

// Callback function for Llama logging.
// This function redirects internal llama.cpp library logs to standard output,
// making them visible in the application's logs.
static void llama_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void) level; // Cast to void to suppress unused parameter warning.
    (void) user_data; // Cast to void to suppress unused parameter warning.
    fputs(text, stdout);
    fflush(stdout);
}

extern "C" {

/**
 * @brief Initializes the Llama backend and sets up a custom logging callback.
 *
 * This JNI function is the first point of contact from the Java/Kotlin application
 * to prepare the native Llama environment.
 *
 * Why `extern "C"` and JNIEXPORT/JNICALL:
 * These keywords ensure that the function is exported with C linkage,
 * making it discoverable and callable by the Java Virtual Machine (JVM)
 * through the Java Native Interface (JNI).
 *
 * @param env Pointer to the JNI environment, used for interacting with the JVM.
 * @param jobject Unused, represents the Java object that called this native method.
 */
JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_initNative(JNIEnv *env, jobject /*unused*/) {
    // Set a custom logging callback for the llama.cpp library.
    // This allows messages from the native library to be routed to the application's logging system.
    llama_log_set(llama_log_callback, nullptr);
    // Initialize the Llama backend. This sets up necessary internal structures and
    // potentially allocates resources for the underlying GGML library.
    llama_backend_init();
    LOGi("Llama backend initialized with custom logger");
}

/**
 * @brief Loads the Llama model from a specified file path.
 *
 * This function handles the loading of the neural network model into memory.
 * It's a critical step before any inference can take place.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @param jmodel_path JNI string containing the path to the Llama model file (.gguf).
 * @return jint Returns 0 on successful model loading, 1 otherwise.
 */
JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    // Convert the Java string to a C-style string for file system access.
    const char *model_path = env->GetStringUTFChars(jmodel_path, 0);
    
    // Initialize default model parameters.
    llama_model_params model_params = llama_model_default_params();
    // Enable memory mapping for the model file.
    // Why mmap: This can significantly reduce memory usage and loading times
    //           by mapping the file directly into virtual memory, allowing
    //           the OS to handle page-in/out as needed.
    model_params.use_mmap = true;
    
    // Load the Llama model from the specified path with the defined parameters.
    g_model = llama_model_load_from_file(model_path, model_params);
    
    // Release the C-style string. It's crucial to release resources obtained
    // from JNIEnv to prevent memory leaks.
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    
    // Return 0 for success (model loaded) or 1 for failure.
    return (g_model != nullptr) ? 0 : 1;
}

/**
 * @brief Prepares the Llama context for inference, including thread setup and batch initialization.
 *
 * This function sets up the operational environment for the Llama model,
 * allocating necessary resources and configuring parameters like context size and thread count.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @return jint Returns 0 on successful preparation, 1 otherwise.
 */
JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_prepare(JNIEnv *env, jobject /*unused*/) {
    // Get default context parameters.
    llama_context_params ctx_params = llama_context_default_params();
    
    // Determine the number of threads for inference.
    // Why hardware_concurrency: To utilize available CPU cores efficiently for parallel processing.
    // Why -2 if >4: This heuristic aims to leave some CPU cores free for other system tasks,
    //               preventing the inference from completely starving the system.
    uint32_t n_threads = std::thread::hardware_concurrency();
    if (n_threads > 4) n_threads -= 2;
    
    // Configure context parameters.
    // `n_ctx`: The maximum sequence length the model can handle.
    //          A larger context allows the model to remember more of the conversation history.
    ctx_params.n_ctx = 4096; 
    ctx_params.n_threads = n_threads;       // Number of threads for general inference.
    ctx_params.n_threads_batch = n_threads; // Number of threads for batch processing.
    
    LOGi("Initializing context with %u threads", n_threads);
    
    // Initialize the Llama context using the loaded model and configured parameters.
    // The context holds the state of the inference process, including KV cache.
    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) return 1; // Return error if context initialization fails.
    
    // Initialize a Llama batch.
    // Why batch: Allows processing multiple sequences or parts of a sequence efficiently.
    //            In this case, it's used for adding tokens for decoding.
    g_batch = llama_batch_init(512, 0, 1);
    
    // Initialize chat templates for formatting prompts.
    // Why chat templates: LLMs often require specific prompt formats (e.g., "[INST] user message [/INST]")
    //                     to perform optimally. This component handles that formatting.
    g_chat_templates = common_chat_templates_init(g_model, "");
    
    // Initialize default sampling parameters.
    common_params_sampling sparams;
    // Initialize the sampler with the model and sampling parameters.
    // The sampler is responsible for selecting the next token based on model outputs
    // and configured parameters like temperature, top_p, top_k.
    g_sampler = common_sampler_init(g_model, sparams);
    
    return 0; // Success.
}

/**
 * @brief Updates the sampling parameters used for generating tokens.
 *
 * This function allows dynamic adjustment of parameters like temperature, top_p, top_k,
 * and repetition penalty without reloading the entire model.
 * These parameters significantly influence the creativity and coherence of the model's output.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @param temp Temperature parameter: controls randomness (higher = more random).
 * @param top_p Top-P (nucleus) sampling: considers the smallest set of tokens whose
 *              cumulative probability exceeds 'top_p'.
 * @param top_k Top-K sampling: considers only the 'top_k' most likely next tokens.
 * @param penalty Repetition penalty: discourages the model from repeating tokens.
 */
JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_updateSamplingParams(
    JNIEnv *env, jobject, jfloat temp, jfloat top_p, jint top_k, jfloat penalty) {
    
    // Free the existing sampler to reinitialize with new parameters.
    if (g_sampler) {
        common_sampler_free(g_sampler);
    }
    
    // Create and populate new sampling parameters.
    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_p = top_p;
    sparams.top_k = top_k;
    sparams.penalty_repeat = penalty;
    
    LOGi("Updating Sampling Params: temp=%.2f, top_p=%.2f, top_k=%d, penalty=%.2f", 
         temp, top_p, top_k, penalty);
    
    // Initialize a new sampler with the updated parameters.
    g_sampler = common_sampler_init(g_model, sparams);
}

/**
 * @brief Retrieves system information about the Llama.cpp build.
 *
 * This can be useful for debugging or verifying the native library's capabilities.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @return jstring A JNI string containing the system information.
 */
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject) {
    // Call the native llama.cpp function to get system info and convert to JNI string.
    return env->NewStringUTF(llama_print_system_info());
}

/**
 * @brief Processes a system prompt, initializing the chat context.
 *
 * This function is typically called at the beginning of a conversation or to
 * set a specific persona/instruction for the model. It clears previous chat history.
 *
 * Why clear chat_msgs: A system prompt usually signifies a fresh start or a
 *                     change in the model's overall directive, making previous
 *                     conversation irrelevant or potentially conflicting.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @param jprompt JNI string containing the system prompt text.
 * @return jint Returns 0 on success, 1 on failure during decoding.
 */
JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_processSystemPrompt(JNIEnv *env, jobject, jstring jprompt) {
    // Convert Java string to C-style string.
    const char *prompt = env->GetStringUTFChars(jprompt, 0);
    
    // Clear previous chat messages and reset position for a new context.
    chat_msgs.clear();
    current_position = 0;
    
    // Create a new common_chat_msg for the system prompt.
    common_chat_msg msg;
    msg.role = "system"; // Mark as system message.
    msg.content = prompt;
    
    // Format the system message using chat templates.
    // Why formatting: Ensures the prompt adheres to the model's expected input structure.
    auto formatted = common_chat_format_single(g_chat_templates.get(), chat_msgs, msg, false, false);
    // Add the system message to the chat history.
    chat_msgs.push_back(msg);
    
    // Tokenize the formatted prompt.
    // `true, true`: These flags likely indicate adding beginning-of-sentence (BOS)
    //                and end-of-sentence (EOS) tokens, crucial for model understanding.
    auto tokens = common_tokenize(g_context, formatted, true, true);
    
    // Prepare the batch for decoding.
    common_batch_clear(g_batch);
    // Add tokens to the batch.
    // Why `current_position + i`: Tokens are added sequentially to maintain their position
    //                             within the overall context window.
    for (int i = 0; i < tokens.size(); i++) {
        common_batch_add(g_batch, tokens[i], current_position + i, {0}, false);
    }
    
    // Decode the batch of tokens. This performs the forward pass through the model
    // to update its internal state (KV cache) based on the prompt.
    if (llama_decode(g_context, g_batch) != 0) return 1;
    
    // Update the current position to reflect the newly processed tokens.
    current_position += tokens.size();
    // Release the C-style string resource.
    env->ReleaseStringUTFChars(jprompt, prompt);
    return 0; // Success.
}

/**
 * @brief Processes a user prompt, appending it to the chat context.
 *
 * This function takes user input, formats it, tokenizes it, and feeds it into the Llama model
 * to prepare for generating a response.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @param jprompt JNI string containing the user prompt text.
 * @param n_predict Unused in this function's scope, likely intended for response generation.
 * @return jint Returns 0 on success, 1 on failure during decoding.
 */
JNIEXPORT jint JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_processUserPrompt(JNIEnv *env, jobject, jstring jprompt, jint n_predict) {
    // Convert Java string to C-style string.
    const char *prompt = env->GetStringUTFChars(jprompt, 0);
    
    // Create a new common_chat_msg for the user prompt.
    common_chat_msg new_msg;
    new_msg.role = "user"; // Mark as user message.
    new_msg.content = prompt;
    
    // Format the user message and existing chat history using chat templates.
    // Why `true, false` for last two args: The `true` likely indicates that
    // this is a "full" format including history, and `false` might mean not to
    // add an EOS token yet as the user is still conversing.
    auto formatted = common_chat_format_single(g_chat_templates.get(), chat_msgs, new_msg, true, false);
    // Add the new user message to the chat history.
    chat_msgs.push_back(new_msg);
    
    // Tokenize the formatted prompt.
    // `true, true`: Similar to system prompt, likely adds BOS/EOS tokens for parsing.
    auto tokens = common_tokenize(g_context, formatted, true, true);
    
    // Prepare the batch for decoding.
    common_batch_clear(g_batch);
    // Add tokens to the batch.
    // Why `i == tokens.size() - 1`: The last token in the batch is marked as a "prompt"
    //                              token, meaning its logits will be computed for sampling.
    //                              Other tokens are typically "past" tokens.
    for (int i = 0; i < tokens.size(); i++) {
        common_batch_add(g_batch, tokens[i], current_position + i, {0}, i == tokens.size() - 1);
    }
    
    // Decode the batch of tokens to update the model's internal state.
    if (llama_decode(g_context, g_batch) != 0) return 1;
    
    // Update the current position.
    current_position += tokens.size();
    // Release the C-style string resource.
    env->ReleaseStringUTFChars(jprompt, prompt);
    return 0; // Success.
}

/**
 * @brief Generates the next token in the sequence.
 *
 * This function performs the core "thinking" step of the model, sampling a new token
 * based on the current context and sampling parameters.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 * @return jstring A JNI string representing the generated token piece, or nullptr if EOG is reached or decode fails.
 */
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_generateNextToken(JNIEnv *env, jobject) {
    // Sample the next token ID from the model's output logits.
    // `g_sampler`: Manages sampling strategy (temp, top_p, top_k, etc.).
    // `g_context`: Provides the model's current state and logits.
    // `-1`: Likely refers to the last token in the batch for which logits were computed.
    const auto id = common_sampler_sample(g_sampler, g_context, -1);
    
    // Accept the sampled token, potentially updating internal sampler state.
    common_sampler_accept(g_sampler, id, true);
    
    // Check if the sampled token is an End-Of-Generation (EOG) token.
    // Why EOG check: To know when the model has finished its response and stop generation.
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), id)) return nullptr;
    
    // Prepare a new batch containing only the newly generated token.
    common_batch_clear(g_batch);
    // Add the generated token to the batch.
    // `true`: Mark this as a "prompt" token so its logits will be computed for the *next* sampling step.
    common_batch_add(g_batch, id, current_position, {0}, true);
    
    // Decode the single-token batch. This updates the KV cache with the new token,
    // preparing the model for the next sampling step.
    if (llama_decode(g_context, g_batch) != 0) return nullptr;
    
    // Increment the current position in the context.
    current_position++;
    
    // Convert the token ID back into a human-readable string piece.
    auto piece = common_token_to_piece(g_context, id);
    // Return the string piece as a JNI string.
    return env->NewStringUTF(piece.c_str());
}

/**
 * @brief Unloads the Llama model and frees associated resources.
 *
 * This function is crucial for releasing memory and other system resources
 * when the model is no longer needed, preventing resource leaks.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 */
JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_unload(JNIEnv *env, jobject) {
    // Free resources in reverse order of allocation to ensure dependencies are met.
    if (g_sampler) common_sampler_free(g_sampler);
    if (g_context) llama_free(g_context);
    if (g_model) llama_model_free(g_model);
    llama_batch_free(g_batch); // g_batch is not a pointer, so direct free.
    
    // Reset global pointers to nullptr to indicate resources are freed and prevent dangling pointers.
    g_sampler = nullptr;
    g_context = nullptr;
    g_model = nullptr;
}

/**
 * @brief Shuts down the Llama backend.
 *
 * This is the final cleanup step for the native library, releasing any
 * global resources initialized by `llama_backend_init()`.
 *
 * @param env Pointer to the JNI environment.
 * @param jobject Unused.
 */
JNIEXPORT void JNICALL
Java_com_arm_aichat_engine_InferenceEngineImpl_shutdown(JNIEnv *env, jobject) {
    // Perform final cleanup of the llama.cpp backend.
    llama_backend_free();
}

} // extern "C"
