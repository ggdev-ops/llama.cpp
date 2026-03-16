package com.arm.aichat.tui

import com.arm.aichat.engine.InferenceEngine
import com.arm.aichat.engine.InferenceEngineImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ChatSettings(
    var temperature: Float = 0.7f,
    var topP: Float = 0.9f,
    var topK: Int = 40,
    var repeatPenalty: Float = 1.1f,
    var maxTokens: Int = 512,
    var contextSize: Int = 4096
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
    val content: String,
    val timestamp: Instant = Instant.now(),
    val tokensGenerated: Int = 0
)

sealed class ChatCommand {
    data object Exit : ChatCommand()
    data object ToggleDebug : ChatCommand()
    data object ToggleHelp : ChatCommand()
    data object Reset : ChatCommand()
    data object ShowSettings : ChatCommand()
    data object ShowStats : ChatCommand()
    data object Benchmark : ChatCommand()
    data object ClearScreen : ChatCommand()
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
                trimmed.equals("/stats", ignoreCase = true) -> ShowStats
                trimmed.equals("/bench", ignoreCase = true) -> Benchmark
                trimmed.equals("/clear", ignoreCase = true) -> ClearScreen
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

class ChatOrchestrator(private val modelPath: String) {
    private val inferenceEngine: InferenceEngine = InferenceEngineImpl.getInstance()
    private var isInitialized = false
    val settings = ChatSettings()
    
    var debugMode = false
        private set
    
    var benchmarkResults: String? = null
        private set
    
    var stats = ChatStats()
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
            // First initialize the engine with configuration
            inferenceEngine.initialize(
                InferenceEngine.ModelConfig(
                    contextSize = settings.contextSize,
                    maxPredictLength = settings.maxTokens,
                    logLevel = if (debugMode) InferenceEngine.LogLevel.DEBUG 
                              else InferenceEngine.LogLevel.INFO
                )
            )
            
            // Then load the model
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
    
    suspend fun runBenchmark(pp: Int = 512, tg: Int = 128, pl: Int = 1, nr: Int = 3): Result<String> = runCatching {
        benchmarkResults = inferenceEngine.benchmark(pp, tg, pl, nr)
        benchmarkResults ?: "Benchmark failed to produce results"
    }
    
    suspend fun getSystemInfo(): String = runCatching {
        inferenceEngine.getSystemInfo()
    }.getOrElse { "Failed to get system info: ${it.message}" }
    
suspend fun sendMessage(message: String, onTokenReceived: (String) -> Unit = {}): Result<String> = runCatching {
    val responseBuilder = StringBuilder()
    var tokenCount = 0
    val startTime = System.currentTimeMillis()
    
    inferenceEngine.sendUserPrompt(
        message, 
        settings.maxTokens, 
        settings.toSamplingParams()
    )
    .collect { token ->
        responseBuilder.append(token)
        tokenCount++
        // Immediately emit token to UI
        onTokenReceived(token)
    }
    
    val elapsedMs = System.currentTimeMillis() - startTime
    stats.update(tokenCount, elapsedMs)
    
    responseBuilder.toString()
}

    suspend fun resetConversation(): Result<Unit> = runCatching {
        inferenceEngine.resetContext()
        stats = ChatStats() // Reset stats
    }
    
    suspend fun getCurrentAssistantMessage(): String? = runCatching {
        inferenceEngine.getCurrentAssistantMessage()
    }.getOrNull()
    
    fun destroy() {
        inferenceEngine.destroy()
    }
    
    fun validateParameter(param: String, value: String): Result<Any> = runCatching {
        when (param) {
            "temp" -> {
                val v = value.toFloat()
                require(v in 0.0f..2.0f) { "Temperature must be between 0.0 and 2.0" }
                settings.temperature = v
                v
            }
            "top_p" -> {
                val v = value.toFloat()
                require(v in 0.0f..1.0f) { "Top_p must be between 0.0 and 1.0" }
                settings.topP = v
                v
            }
            "top_k" -> {
                val v = value.toInt()
                require(v in 1..100) { "Top_k must be between 1 and 100" }
                settings.topK = v
                v
            }
            "penalty" -> {
                val v = value.toFloat()
                require(v in 0.0f..2.0f) { "Repeat penalty must be between 0.0 and 2.0" }
                settings.repeatPenalty = v
                v
            }
            "maxtokens" -> {
                val v = value.toInt()
                require(v in 64..2048) { "Max tokens must be between 64 and 2048" }
                settings.maxTokens = v
                v
            }
            "ctxsize" -> {
                val v = value.toInt()
                require(v in 512..8192) { "Context size must be between 512 and 8192" }
                settings.contextSize = v
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
            "maxtokens" -> settings.maxTokens = value as Int
            "ctxsize" -> settings.contextSize = value as Int
        }
    }
}

data class ChatStats(
    var totalTokensGenerated: Int = 0,
    var totalTimeMs: Long = 0,
    var lastSpeedTokensPerSec: Double = 0.0,
    var averageSpeedTokensPerSec: Double = 0.0,
    var messageCount: Int = 0
) {
    fun update(tokens: Int, elapsedMs: Long) {
        messageCount++
        totalTokensGenerated += tokens
        totalTimeMs += elapsedMs
        
        lastSpeedTokensPerSec = tokens / (elapsedMs / 1000.0)
        averageSpeedTokensPerSec = totalTokensGenerated / (totalTimeMs / 1000.0)
    }
    
    fun getFormattedStats(): String {
        return """
            📊 Chat Statistics:
              Messages: $messageCount
              Total tokens: $totalTokensGenerated
              Last speed: ${"%.1f".format(lastSpeedTokensPerSec)} tokens/sec
              Avg speed: ${"%.1f".format(averageSpeedTokensPerSec)} tokens/sec
              Total time: ${"%.1f".format(totalTimeMs / 1000.0)}s
        """.trimIndent()
    }
}
