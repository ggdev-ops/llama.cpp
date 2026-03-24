package klama.ai.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import klama.ai.compose.domain.ChatViewModel
import klama.ai.compose.domain.ModelManager
import klama.ai.compose.engine.EngineBridge
import klama.ai.compose.io.FilePicker
import kotlinx.coroutines.launch

@Composable
fun App(
    modelManager: ModelManager = remember { ModelManager.createDefault() }
) {
    val chatViewModel = remember(modelManager) { ChatViewModel(modelManager.engineBridge) }
    var isModelReady by remember { mutableStateOf(false) }

    MaterialTheme {
        Crossfade(targetState = isModelReady) { ready ->
            if (ready) {
                ChatScreen(chatViewModel)
            } else {
                ModelLoaderScreen(
                    onModelLoaded = { isModelReady = true },
                    modelManager = modelManager
                )
            }
        }
    }
}

@Composable
fun ModelLoaderScreen(
    onModelLoaded: () -> Unit,
    modelManager: ModelManager
) {
    val scope = rememberCoroutineScope()
    var isCopying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "klama.ai",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Professional AI Programming Tutor",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isCopying) {
                CircularProgressIndicator()
                Text("Importing model to Safe Zone...", modifier = Modifier.padding(top = 16.dp))
            } else {
                Button(
                    onClick = {
                        FilePicker().pickGguf { source ->
                            if (source != null) {
                                scope.launch {
                                    isCopying = true
                                    error = null
                                    try {
                                        // Extract filename from the source if possible, or use a default
                                        val fileName = "model.gguf" // Default name, platform implementations could provide better names
                                        modelManager.importAndLoadModel(source, fileName)
                                        onModelLoaded()
                                    } catch (e: Exception) {
                                        error = "Failed to import model: ${e.message}"
                                        e.printStackTrace()
                                    } finally {
                                        isCopying = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Select & Load GGUF Model")
                }
            }

            error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
