package klama.ai.compose.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Android-specific bridge implementation.
 * Since commonApp can't see InferenceEngine in androidApp, we'll use a functional bridge
 * that will be wired up in the app module.
 */

private val _androidEngineState = MutableStateFlow<EngineState>(EngineState.Uninitialized)

/**
 * These are the hooks that the androidApp module will use to wire up the actual implementation.
 */
object AndroidEngineBridgeHooks {
    var loadModelFn: (suspend (String) -> Unit)? = null
    var setSystemPromptFn: (suspend (String) -> Unit)? = null
    var sendUserPromptFn: ((String, Int, EngineSamplingParams) -> Flow<String>)? = null
    var cleanUpFn: (() -> Unit)? = null
    var destroyFn: (() -> Unit)? = null
    val stateFlow = _androidEngineState
}

actual fun createEngineBridge(): EngineBridge = object : EngineBridge {
    override val state: StateFlow<EngineState> = AndroidEngineBridgeHooks.stateFlow

    override suspend fun loadModel(pathToModel: String) {
        AndroidEngineBridgeHooks.loadModelFn?.invoke(pathToModel)
    }

    override suspend fun setSystemPrompt(prompt: String) {
        AndroidEngineBridgeHooks.setSystemPromptFn?.invoke(prompt)
    }

    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
        samplingParams: EngineSamplingParams
    ): Flow<String> {
        return AndroidEngineBridgeHooks.sendUserPromptFn?.invoke(message, predictLength, samplingParams)
            ?: kotlinx.coroutines.flow.emptyFlow()
    }

    override fun cleanUp() {
        AndroidEngineBridgeHooks.cleanUpFn?.invoke()
    }

    override fun destroy() {
        AndroidEngineBridgeHooks.destroyFn?.invoke()
    }
}

/**
 * Helper to update state from the androidApp module.
 */
fun updateAndroidEngineState(newState: EngineState) {
    _androidEngineState.value = newState
}
