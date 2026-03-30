package ai.llm.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common bridge interface to allow UI/ViewModel to talk to platform-specific InferenceEngines.
 */
interface EngineBridge {
    val state: StateFlow<EngineState>
    
    suspend fun loadModel(pathToModel: String)
    suspend fun setSystemPrompt(prompt: String = "You are a helpful assistant")
    
    fun sendUserPrompt(
        message: String,
        predictLength: Int = 1024,
        samplingParams: EngineSamplingParams = EngineSamplingParams()
    ): Flow<String>
    
    fun cleanUp()
    fun destroy()
}

data class EngineSamplingParams(
    val temp: Float = 0.8f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f
)

sealed class EngineState {
    object Uninitialized : EngineState()
    object Initializing : EngineState()
    object Initialized : EngineState()
    object LoadingModel : EngineState()
    object UnloadingModel : EngineState()
    object ModelReady : EngineState()
    object Benchmarking : EngineState()
    object ProcessingSystemPrompt : EngineState()
    object ProcessingUserPrompt : EngineState()
    object Generating : EngineState()
    data class Error(val message: String?) : EngineState()
}

/**
 * Factory function to create the platform-specific bridge.
 */
expect fun createEngineBridge(): EngineBridge

/**
 * A manual implementation of EngineBridge that delegates to a platform-specific InferenceEngine.
 * This is used because commonApp cannot directly see the InferenceEngine interface if it's
 * defined in the app modules, so we'll pass it in via createEngineBridge.
 * 
 * Wait, actually, the app modules depend on commonApp. 
 * So InferenceEngine MUST be in commonApp if commonApp code needs to reference it.
 * 
 * However, the user wants it in the app modules. 
 * If it's in the app modules, commonApp can only see it if it's passed as a generic or Any,
 * or if we define a duplicate interface in commonApp.
 * 
 * Let's assume the user wants the "ugly" bridge to be the only thing commonApp sees.
 */
class ManualEngineBridge(
    private val engine: Any, // We'll cast this in platform-specific code or use reflection/dynamic calls if needed, 
                             // but better to have a common interface.
    private val loadModelFn: suspend (String) -> Unit,
    private val setSystemPromptFn: suspend (String) -> Unit,
    private val sendUserPromptFn: (String, Int, EngineSamplingParams) -> Flow<String>,
    private val stateFlow: StateFlow<EngineState>,
    private val cleanUpFn: () -> Unit,
    private val destroyFn: () -> Unit
) : EngineBridge {
    override val state: StateFlow<EngineState> = stateFlow

    override suspend fun loadModel(pathToModel: String) = loadModelFn(pathToModel)

    override suspend fun setSystemPrompt(prompt: String) = setSystemPromptFn(prompt)

    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
        samplingParams: EngineSamplingParams
    ): Flow<String> = sendUserPromptFn(message, predictLength, samplingParams)

    override fun cleanUp() = cleanUpFn()

    override fun destroy() = destroyFn()
}
