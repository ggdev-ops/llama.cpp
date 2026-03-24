package klama.ai.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import klama.ai.compose.io.FilePicker
import androidx.lifecycle.lifecycleScope
import klama.ai.compose.engine.AndroidEngineBridgeHooks
import klama.ai.compose.engine.EngineSamplingParams
import klama.ai.compose.engine.EngineState
import klama.ai.compose.engine.InferenceEngine
import klama.ai.compose.engine.internal.InferenceEngineImpl
import klama.ai.compose.engine.updateAndroidEngineState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        FilePicker.handleResult(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Initialize the static launcher for our common code
        FilePicker.launcher = pickerLauncher
        klama.ai.compose.io.AndroidContextProvider.context = applicationContext

        // Wire up the "ugly" bridge
        val engine = InferenceEngineImpl.getInstance(applicationContext)
        
        AndroidEngineBridgeHooks.loadModelFn = { path -> engine.loadModel(path) }
        AndroidEngineBridgeHooks.setSystemPromptFn = { prompt -> engine.setSystemPrompt(prompt) }
        AndroidEngineBridgeHooks.sendUserPromptFn = { message, predictLength, params ->
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
        AndroidEngineBridgeHooks.cleanUpFn = { engine.cleanUp() }
        AndroidEngineBridgeHooks.destroyFn = { engine.destroy() }

        // Sync state
        lifecycleScope.launch {
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
                updateAndroidEngineState(engineState)
            }
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
