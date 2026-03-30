package ai.llm.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

/**
 * JVM-specific bridge implementation.
 */

private val _jvmEngineState = MutableStateFlow<EngineState>(EngineState.Uninitialized)

object JvmEngineBridgeHooks {
    var loadModelFn: (suspend (String) -> Unit)? = null
    var setSystemPromptFn: (suspend (String) -> Unit)? = null
    var sendUserPromptFn: ((String, Int, EngineSamplingParams) -> Flow<String>)? = null
    var cleanUpFn: (() -> Unit)? = null
    var destroyFn: (() -> Unit)? = null
    val stateFlow = _jvmEngineState
}

actual fun createEngineBridge(): EngineBridge = object : EngineBridge {
    override val state: StateFlow<EngineState> = JvmEngineBridgeHooks.stateFlow

    override suspend fun loadModel(pathToModel: String) {
        JvmEngineBridgeHooks.loadModelFn?.invoke(pathToModel)
    }

    override suspend fun setSystemPrompt(prompt: String) {
        JvmEngineBridgeHooks.setSystemPromptFn?.invoke(prompt)
    }

    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
        samplingParams: EngineSamplingParams
    ): Flow<String> {
        return JvmEngineBridgeHooks.sendUserPromptFn?.invoke(message, predictLength, samplingParams)
            ?: kotlinx.coroutines.flow.emptyFlow()
    }

    override fun cleanUp() {
        JvmEngineBridgeHooks.cleanUpFn?.invoke()
    }

    override fun destroy() {
        JvmEngineBridgeHooks.destroyFn?.invoke()
    }
}

/**
 * Helper to update state from the desktopApp module.
 */
fun updateJvmEngineState(newState: EngineState) {
    _jvmEngineState.value = newState
}
