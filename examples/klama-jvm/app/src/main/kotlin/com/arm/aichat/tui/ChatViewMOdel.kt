package com.arm.aichat.tui

import com.arm.aichat.engine.InferenceEngine
import com.arm.aichat.engine.InferenceEngineImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.io.File

data class ChatSettings(
    var temperature: Float = 0.7f,
    var topP: Float = 0.9f,
    var topK: Int = 40,
    var repeatPenalty: Float = 1.1f,
    var maxTokens: Int = 512
) {
    fun toSamplingParams() = InferenceEngine.SamplingParams(
        temp = temperature,
        topP = topP,
        topK = topK,
        repeatPenalty = repeatPenalty
    )
}

data class ChatMessage(
    val role: String,
    val content: String
)

sealed class ChatCommand {
    data object Exit : ChatCommand()
    data object ToggleDebug : ChatCommand()
    data object ToggleHelp : ChatCommand()
    data object Reset : ChatCommand()
    data object ShowSettings : ChatCommand()
    data class SetParameter(val param: String, val value: String) : ChatCommand()
    data class SetSystemPrompt(val prompt: String) : ChatCommand()
    data class UserMessage(val content: String) : ChatCommand()
    
    companion object {
        fun parse(input: String): ChatCommand? {
            val trimmed = input.trim()
            
            return when {
                trimmed.equals("/exit", ignoreCase = true) -> Exit
                trimmed.equals("/debug", ignoreCase = true) -> ToggleDebug
                trimmed.equals("/help", ignoreCase = true) -> ToggleHelp
                trimmed.equals("/reset", ignoreCase = true) -> Reset
                trimmed.equals("/settings", ignoreCase = true) -> ShowSettings
                trimmed.startsWith("/sysprompt ", ignoreCase = true) -> {
                    val prompt = trimmed.substring(10).trim()
                    if (prompt.isNotEmpty()) SetSystemPrompt(prompt) else null
                }
                trimmed.startsWith("/set ", ignoreCase = true) -> {
                    val parts = trimmed.substring(5).trim().split("\\s+".toRegex())
                    if (parts.size == 2) {
                        SetParameter(parts[0].lowercase(), parts[1])
                    } else null
                }
                trimmed.isNotEmpty() -> UserMessage(trimmed)
                else -> null
            }
        }
    }
}

import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import okio.Path.Companion.toPath
import okio.buffer
import okio.FileSystem

class ChatOrchestrator(private val modelPath: String) {
    private val inferenceEngine: InferenceEngine = InferenceEngineImpl.getInstance()
    private var isInitialized = false
    val settings = ChatSettings()
    
    var metadata: GgufMetadata? = null
        private set
    
    var debugMode = false
        private set
    
    val isModelLoaded: Boolean
        get() = isInitialized
    
    init {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found: $modelPath")
        }
    }
    
    suspend fun initialize(): Result<Unit> = runCatching {
        if (!isInitialized) {
            // 1. Read Metadata (The Librarian Step)
            val path = modelPath.toPath()
            val reader = GgufMetadataReader.create()
            FileSystem.SYSTEM.source(path).buffer().use { source ->
                metadata = reader.readStructuredMetadata(source)
            }

            // 2. Load Model (The Scientist Step)
            inferenceEngine.loadModel(modelPath)
            isInitialized = true
        }
    }
    
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }
    
    fun toggleDebugMode() {
        debugMode = !debugMode
    }
    
    suspend fun setSystemPrompt(prompt: String): Result<Unit> = runCatching {
        inferenceEngine.setSystemPrompt(prompt)
    }
    
    suspend fun sendMessage(message: String, onTokenReceived: (String) -> Unit = {}): Result<String> = runCatching {
    val responseBuilder = StringBuilder()
    
    inferenceEngine.sendUserPrompt(
        message, 
        settings.maxTokens, 
        settings.toSamplingParams()
    )
    .onEach { token ->
        responseBuilder.append(token)
        onTokenReceived(token)
    }
    .collect { token ->
        // The collect function needs a collector parameter
        // This is just a no-op since we're already handling tokens in onEach
    }
    
    responseBuilder.toString()
}
    
    fun resetConversation() {
        // Clear any conversation state if needed
        // Note: You might want to reinitialize the inference engine or clear context
    }
    
    fun destroy() {
        inferenceEngine.destroy()
    }
    
    fun validateParameter(param: String, value: String): Result<Any> = runCatching {
        when (param) {
            "temp" -> {
                val v = value.toFloat()
                require(v in 0.0f..2.0f) { "Temperature must be between 0.0 and 2.0" }
                v
            }
            "top_p" -> {
                val v = value.toFloat()
                require(v in 0.0f..1.0f) { "Top_p must be between 0.0 and 1.0" }
                v
            }
            "top_k" -> {
                val v = value.toInt()
                require(v in 1..100) { "Top_k must be between 1 and 100" }
                v
            }
            "penalty" -> {
                val v = value.toFloat()
                require(v in 0.0f..2.0f) { "Repeat penalty must be between 0.0 and 2.0" }
                v
            }
            else -> throw IllegalArgumentException("Unknown parameter: $param")
        }
    }
    
    fun updateParameter(param: String, value: Any) {
        when (param) {
            "temp" -> settings.temperature = value as Float
            "top_p" -> settings.topP = value as Float
            "top_k" -> settings.topK = value as Int
            "penalty" -> settings.repeatPenalty = value as Float
        }
    }
}
