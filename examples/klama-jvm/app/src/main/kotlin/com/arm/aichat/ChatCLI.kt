package com.arm.aichat

import com.arm.aichat.engine.InferenceEngine
import com.arm.aichat.engine.InferenceEngineImpl
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Instant = Instant.now()
)

class ChatCLI {
    private val engine = InferenceEngineImpl.getInstance()
    private val messages = mutableListOf<ChatMessage>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    
    // Settings
    var temperature = 0.7f
    var topP = 0.9f
    var topK = 40
    var repeatPenalty = 1.1f
    var maxTokens = 512
    var contextSize = 4096
    var debug = false
    
    private var isInitialized = false
    
    suspend fun initialize(modelPath: String): Result<Unit> = runCatching {
        println("╔══════════════════════════════════════════════════════════╗")
        println("║     C/C++ Programming Tutor - llama.cpp CLI             ║")
        println("╚══════════════════════════════════════════════════════════╝")
        println()
        println("🚀 Initializing...")
        println("📁 Model: $modelPath")
        
        // Initialize engine with config
        engine.initialize(
            InferenceEngine.ModelConfig(
                contextSize = contextSize,
                maxPredictLength = maxTokens,
                logLevel = if (debug) InferenceEngine.LogLevel.DEBUG else InferenceEngine.LogLevel.INFO
            )
        )
        
        // Load model
        engine.loadModel(modelPath)
        
        // Set system prompt
        val systemPrompt = """
            You are a knowledgeable programming tutor specializing in C and C++.
            You provide detailed, educational responses with practical examples.
            
            Guidelines:
            - Explain concepts clearly and thoroughly
            - Provide code examples when relevant
            - Mention best practices and potential pitfalls
            - Keep responses focused and educational
            - If you're not sure about something, be honest about it
            
            Remember: You're here to teach and help understand C/C++ programming.
        """.trimIndent()
        
        engine.setSystemPrompt(systemPrompt)
        isInitialized = true
        
        // Show system info
        println("ℹ️  System: ${engine.getSystemInfo().take(80)}...")
        println("✅ Ready! Type /help for commands\n")
    }
    
    fun printHelp() {
        println("""
            ╔══════════════════════════════════════════════════════════╗
            ║                        COMMANDS                          ║
            ╠══════════════════════════════════════════════════════════╣
            ║  /help, /h        - Show this help message               ║
            ║  /exit, /quit, /q - Exit the program                     ║
            ║  /clear, /cls     - Clear screen                         ║
            ║  /reset           - Reset conversation                   ║
            ║  /stats           - Show statistics                      ║
            ║  /bench           - Run performance benchmark            ║
            ║  /settings, /s    - Show current settings                ║
            ║                                                          ║
            ║  PARAMETERS:                                              ║
            ║  /temp <0.0-2.0>   - Set temperature                     ║
            ║  /top_p <0.0-1.0>  - Set top-p sampling                  ║
            ║  /top_k <1-100>    - Set top-k sampling                  ║
            ║  /penalty <0.0-2.0> - Set repeat penalty                 ║
            ║  /maxtokens <n>    - Set max tokens (64-2048)            ║
            ║  /ctxsize <n>      - Set context size (512-8192)         ║
            ║  /debug            - Toggle debug mode                   ║
            ║  /system <prompt>  - Change system prompt                ║
            ╚══════════════════════════════════════════════════════════╝
            
            Just type your message and press Enter to chat!
        """.trimIndent())
        println()
    }
    
    fun showSettings() {
        println("""
            ╔══════════════════════════════════════════════════════════╗
            ║                     CURRENT SETTINGS                     ║
            ╠══════════════════════════════════════════════════════════╣
            ║  Temperature : ${"%.2f".format(temperature).padEnd(6)} (0.0-2.0)                         ║
            ║  Top-p       : ${"%.2f".format(topP).padEnd(6)} (0.0-1.0)                         ║
            ║  Top-k       : ${topK.toString().padEnd(6)} (1-100)                           ║
            ║  Penalty     : ${"%.2f".format(repeatPenalty).padEnd(6)} (0.0-2.0)                         ║
            ║  Max tokens  : ${maxTokens.toString().padEnd(6)} (64-2048)                        ║
            ║  Context size: ${contextSize.toString().padEnd(6)} (512-8192)                       ║
            ╚══════════════════════════════════════════════════════════╝
        """.trimIndent())
    }
    
    private fun printMessage(message: ChatMessage) {
        val timeStr = timeFormatter.format(message.timestamp)
        when (message.role) {
            "user" -> {
                println("\u001B[32m[$timeStr] You:\u001B[0m ${message.content}")
            }
            "assistant" -> {
                println("\u001B[34m[$timeStr] Tutor:\u001B[0m")
                message.content.lines().forEach { line ->
                    println("  $line")
                }
                println()
            }
            "system" -> {
                println("\u001B[33m[$timeStr] ${message.content}\u001B[0m")
            }
        }
    }
    
    suspend fun processCommand(input: String): Boolean {
        val trimmed = input.trim()
        
        when {
            trimmed.equals("/help", ignoreCase = true) || trimmed.equals("/h", ignoreCase = true) -> {
                printHelp()
                return false
            }
            trimmed.equals("/exit", ignoreCase = true) || trimmed.equals("/quit", ignoreCase = true) || 
            trimmed.equals("/q", ignoreCase = true) -> {
                return true // Signal exit
            }
            trimmed.equals("/clear", ignoreCase = true) || trimmed.equals("/cls", ignoreCase = true) -> {
                repeat(50) { println() }
                println("\u001B[33mScreen cleared\u001B[0m\n")
                return false
            }
            trimmed.equals("/reset", ignoreCase = true) -> {
                engine.resetContext()
                messages.clear()
                println("\u001B[33mConversation reset\u001B[0m\n")
                return false
            }
            trimmed.equals("/stats", ignoreCase = true) -> {
                // Get stats from engine
                println("Statistics will be shown here")
                return false
            }
            trimmed.equals("/settings", ignoreCase = true) || trimmed.equals("/s", ignoreCase = true) -> {
                showSettings()
                return false
            }
            trimmed.equals("/bench", ignoreCase = true) -> {
                println("📊 Running benchmark (this may take a moment)...")
                try {
                    val results = engine.benchmark(pp = 512, tg = 128, pl = 1, nr = 2)
                    println(results)
                } catch (e: Exception) {
                    println("❌ Benchmark failed: ${e.message}")
                }
                println()
                return false
            }
            trimmed.equals("/debug", ignoreCase = true) -> {
                debug = !debug
                println("Debug mode is now ${if (debug) "ON" else "OFF"}")
                println("Note: Debug mode change requires restart to take full effect")
                return false
            }
            trimmed.startsWith("/system ") -> {
                val prompt = trimmed.substring(8).trim()
                if (prompt.isNotEmpty()) {
                    engine.setSystemPrompt(prompt)
                    messages.add(ChatMessage("system", "System prompt updated"))
                    println("\u001B[33mSystem prompt updated\u001B[0m\n")
                }
                return false
            }
            trimmed.startsWith("/temp ") -> {
                try {
                    val value = trimmed.substring(6).trim().toFloat()
                    require(value in 0.0f..2.0f)
                    temperature = value
                    println("\u001B[32mTemperature set to $value\u001B[0m\n")
                } catch (e: Exception) {
                    println("\u001B[31mInvalid temperature value. Use 0.0-2.0\u001B[0m\n")
                }
                return false
            }
            trimmed.startsWith("/top_p ") -> {
                try {
                    val value = trimmed.substring(7).trim().toFloat()
                    require(value in 0.0f..1.0f)
                    topP = value
                    println("\u001B[32mTop_p set to $value\u001B[0m\n")
                } catch (e: Exception) {
                    println("\u001B[31mInvalid top_p value. Use 0.0-1.0\u001B[0m\n")
                }
                return false
            }
            trimmed.startsWith("/top_k ") -> {
                try {
                    val value = trimmed.substring(7).trim().toInt()
                    require(value in 1..100)
                    topK = value
                    println("\u001B[32mTop_k set to $value\u001B[0m\n")
                } catch (e: Exception) {
                    println("\u001B[31mInvalid top_k value. Use 1-100\u001B[0m\n")
                }
                return false
            }
            trimmed.startsWith("/penalty ") -> {
                try {
                    val value = trimmed.substring(9).trim().toFloat()
                    require(value in 0.0f..2.0f)
                    repeatPenalty = value
                    println("\u001B[32mRepeat penalty set to $value\u001B[0m\n")
                } catch (e: Exception) {
                    println("\u001B[31mInvalid penalty value. Use 0.0-2.0\u001B[0m\n")
                }
                return false
            }
            trimmed.startsWith("/maxtokens ") -> {
                try {
                    val value = trimmed.substring(11).trim().toInt()
                    require(value in 64..2048)
                    maxTokens = value
                    println("\u001B[32mMax tokens set to $value\u001B[0m")
                    println("\u001B[33mNote: New value will be used for next message\u001B[0m\n")
                } catch (e: Exception) {
                    println("\u001B[31mInvalid max tokens value. Use 64-2048\u001B[0m\n")
                }
                return false
            }
            trimmed.startsWith("/ctxsize ") -> {
                try {
                    val value = trimmed.substring(9).trim().toInt()
                    require(value in 512..8192)
                    contextSize = value
                    println("\u001B[32mContext size set to $value\u001B[0m")
                    println("\u001B[33mNote: Context size change requires restart\u001B[0m\n")
                } catch (e: Exception) {
                    println("\u001B[31mInvalid context size value. Use 512-8192\u001B[0m\n")
                }
                return false
            }
            trimmed.isNotEmpty() && !trimmed.startsWith("/") -> {
                // Regular message
                return false
            }
        }
        return false
    }
    
    suspend fun sendMessage(content: String) {
        val userMessage = ChatMessage("user", content)
        messages.add(userMessage)
        printMessage(userMessage)
        
        print("\u001B[33m") // Yellow color for thinking
        var dotCount = 0
        val thinkingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                print("\rTutor is thinking" + ".".repeat(dotCount) + " ".repeat(3 - dotCount))
                System.out.flush()
                dotCount = (dotCount + 1) % 4
                delay(200)
            }
        }
        
        try {
            val response = StringBuilder()
            var firstToken = true
            
            val params = InferenceEngine.SamplingParams(
                temp = temperature,
                topP = topP,
                topK = topK,
                repeatPenalty = repeatPenalty
            )
            
            engine.sendUserPrompt(content, maxTokens, params).collect { token ->
                if (firstToken) {
                    thinkingJob.cancel()
                    print("\r\u001B[K") // Clear the thinking line
                    print("\u001B[34m") // Blue color for response
                    firstToken = false
                }
                print(token)
                response.append(token)
                System.out.flush()
            }
            
            println("\u001B[0m") // Reset color
            val assistantMessage = ChatMessage("assistant", response.toString())
            messages.add(assistantMessage)
            println() // Add spacing
            
        } catch (e: Exception) {
            thinkingJob.cancel()
            print("\r\u001B[K")
            println("\u001B[31m❌ Error: ${e.message}\u001B[0m\n")
        }
    }
    
    fun cleanup() {
        engine.cleanUp()
    }
    
    fun destroy() {
        engine.destroy()
    }
}

suspend fun main(args: Array<String>) {
    val modelPath = if (args.isNotEmpty()) {
        args[0]
    } else {
        print("Enter path to model file: ")
        readlnOrNull() ?: run {
            println("No model path provided")
            return
        }
    }
    
    val modelFile = File(modelPath)
    if (!modelFile.exists()) {
        println("❌ Model file not found: $modelPath")
        return
    }
    
    val cli = ChatCLI()
    
    // Initialize
    cli.initialize(modelPath).onFailure { e ->
        println("❌ Failed to initialize: ${e.message}")
        e.printStackTrace()
        return
    }
    
    // Show help on start
    cli.printHelp()
    
    // Main chat loop
    while (true) {
        print("\u001B[32m> \u001B[0m")
        val input = readlnOrNull() ?: break
        
        if (input.isBlank()) continue
        
        val shouldExit = cli.processCommand(input)
        if (shouldExit) break
        
        // If not a command, send as message
        if (!input.startsWith("/")) {
            cli.sendMessage(input)
        }
    }
    
    // Cleanup
    cli.cleanup()
    cli.destroy()
    println("\n👋 Goodbye!")
}
