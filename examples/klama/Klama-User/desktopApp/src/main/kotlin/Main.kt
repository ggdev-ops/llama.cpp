package ai.llm

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.llm.engine.EngineSamplingParams
import ai.llm.engine.EngineState
import klama.ai.engine.InferenceEngine
import ai.llm.engine.JvmEngineBridgeHooks
import klama.ai.engine.InferenceEngineImpl
import ai.llm.engine.updateJvmEngineState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

fun main() {
    val engine = InferenceEngineImpl.getInstance()

    JvmEngineBridgeHooks.loadModelFn = { path -> engine.loadModel(path) }
    JvmEngineBridgeHooks.setSystemPromptFn = { prompt -> engine.setSystemPrompt(prompt) }
    JvmEngineBridgeHooks.sendUserPromptFn = { message, predictLength, params ->
        engine.sendUserPrompt(
            message, 
            predictLength, 
            InferenceEngine.SamplingParams(
                temp = params.temp,
                topP = params.topP,
                topK = params.topK,
                repeatPenalty = params.repeatPenalty
            )
        )
    }
    JvmEngineBridgeHooks.cleanUpFn = { engine.cleanUp() }
    JvmEngineBridgeHooks.destroyFn = { engine.destroy() }

    val scope = MainScope()
    scope.launch {
        engine.state.collect { state ->
            val engineState = when (state) {
                is InferenceEngine.State.Uninitialized -> EngineState.Uninitialized
                is InferenceEngine.State.Initializing -> EngineState.Initializing
                is InferenceEngine.State.Initialized -> EngineState.Initialized
                is InferenceEngine.State.LoadingModel -> EngineState.LoadingModel
                is InferenceEngine.State.UnloadingModel -> EngineState.UnloadingModel
                is InferenceEngine.State.ModelReady -> EngineState.ModelReady
                is InferenceEngine.State.Benchmarking -> EngineState.Benchmarking
                is InferenceEngine.State.ProcessingSystemPrompt -> EngineState.ProcessingSystemPrompt
                is InferenceEngine.State.ProcessingUserPrompt -> EngineState.ProcessingUserPrompt
                is InferenceEngine.State.Generating -> EngineState.Generating
                is InferenceEngine.State.Error -> EngineState.Error(state.exception.message)
            }
            updateJvmEngineState(engineState)
        }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Klama-User",
        ) {
            App()
        }
    }
}