package com.arm.aichat.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InferenceEngine {

    sealed class State {
        object Uninitialized : State()
        object Initializing : State()
        object Initialized : State()
        object LoadingModel : State()
        object ModelReady : State()
        object ProcessingSystemPrompt : State()
        object ProcessingUserPrompt : State()
        object Generating : State()
        data class Error(val throwable: Throwable) : State()
    }

    /**
     * Log levels for native logging
     */
    enum class LogLevel(val value: Int) {
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4)
    }

    /**
     * Parameters to control the "creativity" and deterministic nature of the model.
     */
    data class SamplingParams(
        val temp: Float = 0.8f,
        val topP: Float = 0.95f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f
    )

    /**
     * Configuration for model initialization
     */
    data class ModelConfig(
        val contextSize: Int = 4096,
        val maxPredictLength: Int = 512,
        val logLevel: LogLevel = LogLevel.INFO
    )

    val state: StateFlow<State>

    /**
     * Initialize the engine with custom configuration
     */
    suspend fun initialize(config: ModelConfig = ModelConfig())

    /**
     * Load a model from the specified path
     */
    suspend fun loadModel(pathToModel: String)

    /**
     * Set the system prompt for the conversation
     */
    suspend fun setSystemPrompt(prompt: String)

    /**
     * Reset the conversation context (clears history and KV cache)
     */
    suspend fun resetContext()

    /**
     * Get system information about the llama.cpp build and available backends
     */
    suspend fun getSystemInfo(): String

    /**
     * Run a benchmark on the loaded model
     * @param pp Prompt processing tokens
     * @param tg Text generation tokens
     * @param pl Parallel generations
     * @param nr Number of runs
     * @return Benchmark results as a formatted string
     */
    suspend fun benchmark(pp: Int = 512, tg: Int = 128, pl: Int = 1, nr: Int = 3): String

    /**
     * Get the current assistant message being generated
     * Useful for UI updates during streaming
     */
    fun getCurrentAssistantMessage(): String?  // Removed suspend modifier

    /**
     * Send a prompt to the model with optional sampling parameters
     */
    fun sendUserPrompt(
        message: String,
        predictLength: Int = 128,
        samplingParams: SamplingParams = SamplingParams()
    ): Flow<String>

    /**
     * Clean up resources (unloads model but keeps backend initialized)
     */
    fun cleanUp()

    /**
     * Destroy the engine and free all resources
     */
    fun destroy()
}
