package klama.ai.compose.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InferenceEngine {

    sealed class State {
        object Uninitialized : State()
        object Initializing : State()
        object Initialized : State()
        object LoadingModel : State()
        object UnloadingModel : State()
        object ModelReady : State()
        object Benchmarking : State()
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
    /**
     * Sends a system prompt to the loaded model.
     */
    suspend fun setSystemPrompt(prompt: String = "You are a helpful assistant")
    
    /**
     * Send a prompt to the model with optional sampling parameters.
     */
    fun sendUserPrompt(
        message: String, 
        predictLength: Int = DEFAULT_PREDICT_LENGTH,
        samplingParams: SamplingParams = SamplingParams()
    ): Flow<String>
    
    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String
    
    fun cleanUp()
    fun destroy()

    companion object {
        const val DEFAULT_PREDICT_LENGTH = 1024
    }
}

class UnsupportedArchitectureException : Exception()

val InferenceEngine.State.isUninterruptible
    get() = this is InferenceEngine.State.Initializing ||
        this is InferenceEngine.State.LoadingModel ||
        this is InferenceEngine.State.UnloadingModel ||
        this is InferenceEngine.State.Benchmarking ||
        this is InferenceEngine.State.ProcessingSystemPrompt ||
        this is InferenceEngine.State.ProcessingUserPrompt

val InferenceEngine.State.isModelLoaded: Boolean
    get() = this is InferenceEngine.State.ModelReady ||
        this is InferenceEngine.State.Benchmarking ||
        this is InferenceEngine.State.ProcessingSystemPrompt ||
        this is InferenceEngine.State.ProcessingUserPrompt ||
        this is InferenceEngine.State.Generating
