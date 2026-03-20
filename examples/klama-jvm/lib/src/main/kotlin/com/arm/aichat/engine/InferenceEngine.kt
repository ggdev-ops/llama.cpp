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
     * Parameters to control the "creativity" and deterministic nature of the model.
     */
    data class SamplingParams(
        val temp: Float = 0.8f,
        val topP: Float = 0.95f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f
    )

    val state: StateFlow<State>

    suspend fun loadModel(pathToModel: String)
    suspend fun setSystemPrompt(prompt: String)
    
    /**
     * Send a prompt to the model with optional sampling parameters.
     */
    fun sendUserPrompt(
        message: String, 
        predictLength: Int = 128,
        samplingParams: SamplingParams = SamplingParams()
    ): Flow<String>
    
    fun cleanUp()
    fun destroy()
}
