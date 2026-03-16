package com.arm.aichat.tui

import com.arm.aichat.tui.ChatOrchestrator
import com.arm.aichat.tui.ChatMessage
import com.varabyte.kotter.foundation.*
import com.varabyte.kotter.foundation.anim.*
import com.varabyte.kotter.foundation.collections.liveListOf
import com.varabyte.kotter.foundation.input.*
import com.varabyte.kotter.foundation.text.*
import com.varabyte.kotter.runtime.Session
import com.varabyte.kotter.runtime.render.RenderScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.time.format.DateTimeFormatter

class ChatTui(private val engine: ChatOrchestrator) {
    
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    
    fun start() = session {
        val logger = ChatLogger("history.log")
        val scope = CoroutineScope(Dispatchers.IO)
        val thinkingAnim = renderAnimOf(4, 500.milliseconds) { frameIndex ->
            text(".".repeat(frameIndex))
        }
        
        val spinnerFrames = listOf("⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷")
        val thinkingSpinner = renderAnimOf(spinnerFrames.size, 100.milliseconds) { frameIndex ->
            text(spinnerFrames[frameIndex])
        }
        
        // UI State
        val messages = liveListOf<ChatMessage>()
        var isThinking by liveVarOf(false)
        var showHelp by liveVarOf(false)
        var showStats by liveVarOf(false)
        var systemInfo by liveVarOf("")
        var currentSpeed by liveVarOf(0.0)
        
        // Load system info on startup
        scope.launch {
            systemInfo = engine.getSystemInfo()
        }
        
        section {
            // Header with system info
            cyan { bold { textLine("🤖 C/C++ Programming Tutor - llama.cpp") } }
            white { textLine(systemInfo.take(80)) }
            textLine("Type '/help' for commands, '/exit' to quit")
            
            if (engine.debugMode) {
                red { bold { textLine("🔧 DEBUG MODE ENABLED - Verbose logging active") } }
            }
            
            textLine()
            
            // Settings bar
            yellow { bold { text("⚙️ ") } }
            text("temp=")
            cyan { text("%.2f".format(engine.settings.temperature)) }
            text(" top_p=")
            cyan { text("%.2f".format(engine.settings.topP)) }
            text(" top_k=")
            cyan { text(engine.settings.topK.toString()) }
            text(" penalty=")
            cyan { text("%.2f".format(engine.settings.repeatPenalty)) }
            text(" max=")
            cyan { text(engine.settings.maxTokens.toString()) }
            text(" ctx=")
            cyan { text(engine.settings.contextSize.toString()) }
            
            if (currentSpeed > 0) {
                text(" | ")
                green { text("${"%.1f".format(currentSpeed)} t/s") }
            }
            textLine()
            
            textLine()
            
            // Messages area - THIS IS WHERE MESSAGES ARE DISPLAYED
            if (messages.isEmpty()) {
                white { textLine("No messages yet. Type your message below...") }
                textLine()
            } else {
                messages.forEach { message ->
                    val timeStr = timeFormatter.format(message.timestamp)
                    white { text("[$timeStr] ") }
                    
                    when (message.role) {
                        "user" -> {
                            green { bold { text("You: ") } }
                            textLine(message.content)
                        }
                        "assistant" -> {
                            blue { bold { text("Tutor: ") } }
                            if (message.tokensGenerated > 0) {
                                white { text("[${message.tokensGenerated} tokens] ") }
                            }
                            // Handle multi-line messages properly
                            message.content.lines().forEachIndexed { index, line ->
                                if (index == 0) {
                                    textLine(line)
                                } else {
                                    text("       ") // Indent continuation lines
                                    textLine(line)
                                }
                            }
                            textLine() // Add spacing between messages
                        }
                        "system" -> {
                            yellow { textLine("📢 ${message.content}") }
                        }
                    }
                }
            }
            
            // Stats display
            if (showStats) {
                textLine()
                cyan { textLine(engine.stats.getFormattedStats()) }
                textLine()
            }
            
            // Benchmark results
            engine.benchmarkResults?.let { results ->
                textLine()
                green { bold { textLine("📊 Benchmark Results:") } }
                results.lines().forEach { line ->
                    if (line.startsWith("|")) {
                        when {
                            line.contains("pp") -> cyan { textLine(line) }
                            line.contains("tg") -> magenta { textLine(line) }
                            else -> white { textLine(line) }
                        }
                    } else {
                        textLine(line)
                    }
                }
                textLine()
            }
            
            // Help
            if (showHelp) {
                yellow { bold { textLine("\n📚 Available Commands:") } }
                textLine("  /exit              - Quit the application")
                textLine("  /reset             - Clear conversation history")
                textLine("  /settings          - Show current settings")
                textLine("  /stats             - Show chat statistics")
                textLine("  /bench             - Run performance benchmark")
                textLine("  /clear             - Clear screen")
                textLine("  /set temp <value>  - Set temperature (0.0-2.0)")
                textLine("  /set top_p <value> - Set top_p (0.0-1.0)")
                textLine("  /set top_k <value> - Set top_k (1-100)")
                textLine("  /set penalty <val> - Set repeat penalty (0.0-2.0)")
                textLine("  /set maxtokens <n> - Set max tokens (64-2048)")
                textLine("  /set ctxsize <n>   - Set context size (512-8192)")
                textLine("  /debug             - Toggle debug mode")
                textLine("  /sysprompt <text>  - Change system prompt")
                textLine()
            }
            
            // Input area
            if (isThinking) {
                text("Tutor is thinking ")
                thinkingSpinner(this)
                text(" ")
                thinkingAnim(this)
            } else {
                green { text("> ") }
                input()
            }
        }.runUntilSignal {
            onInputEntered {
                val trimmed = input.trim()
                
                val command = com.arm.aichat.tui.ChatCommand.parse(trimmed)
                
                when (command) {
                    is com.arm.aichat.tui.ChatCommand.Exit -> signal()
                    
                    is com.arm.aichat.tui.ChatCommand.ToggleDebug -> {
                        engine.toggleDebugMode()
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.ToggleHelp -> {
                        showHelp = !showHelp
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.ShowStats -> {
                        showStats = !showStats
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.ClearScreen -> {
                        messages.clear()
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.Benchmark -> {
                        clearInput()
                        scope.launch {
                            isThinking = true
                            try {
                                val msg = ChatMessage("system", "Running benchmark (pp=512, tg=128, pl=1, nr=3)...")
                                messages.add(msg)
                                
                                val results = engine.runBenchmark()
                                results.onSuccess {
                                    val resultMsg = ChatMessage("system", "Benchmark complete")
                                    messages.add(resultMsg)
                                }.onFailure { e ->
                                    val errorMsg = ChatMessage("system", "Benchmark failed: ${e.message}")
                                    messages.add(errorMsg)
                                }
                            } finally {
                                isThinking = false
                            }
                        }
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.Reset -> {
                        scope.launch {
                            isThinking = true
                            try {
                                engine.resetConversation()
                                engine.initialize()
                                messages.clear()
                                val msg = ChatMessage("system", "Conversation reset and model re-initialized")
                                messages.add(msg)
                                logger.log(msg)
                            } catch (e: Exception) {
                                val errorMsg = ChatMessage("system", "Reset failed: ${e.message}")
                                messages.add(errorMsg)
                                logger.log(errorMsg)
                            } finally {
                                isThinking = false
                            }
                        }
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.ShowSettings -> {
                        // Settings are shown automatically
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.SetSystemPrompt -> {
                        val sysPrompt = command.prompt
                        clearInput()
                        scope.launch {
                            isThinking = true
                            try {
                                engine.setSystemPrompt(sysPrompt).onSuccess {
                                    val msg = ChatMessage("system", "System prompt updated")
                                    messages.add(msg)
                                    logger.log(msg)
                                }.onFailure { e ->
                                    val msg = ChatMessage("system", "Failed to update system prompt: ${e.message}")
                                    messages.add(msg)
                                    logger.log(msg)
                                }
                            } finally {
                                isThinking = false
                            }
                        }
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.SetParameter -> {
                        scope.launch {
                            engine.validateParameter(command.param, command.value).onSuccess { validatedValue ->
                                val msg = ChatMessage("system", "✅ Updated ${command.param} to $validatedValue")
                                messages.add(msg)
                                logger.log(msg)
                            }.onFailure { e ->
                                val msg = ChatMessage("system", "❌ Error: ${e.message}")
                                messages.add(msg)
                                logger.log(msg)
                            }
                        }
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.UserMessage -> {
                        val userMessageContent = command.content
                        val userMessage = ChatMessage("user", userMessageContent)
                        messages.add(userMessage)
                        logger.log(userMessage)
                        clearInput()
                        
                        scope.launch {
                            isThinking = true
                            
                            try {
                                val userMessageIndex = messages.lastIndex
                                var currentResponse = ""
                                var tokenCount = 0
                                
                                val result = engine.sendMessage(userMessageContent) { token ->
                                    currentResponse += token
                                    tokenCount++
                                    currentSpeed = engine.stats.lastSpeedTokensPerSec
                                    
                                    if (userMessageIndex + 1 < messages.size) {
                                        messages[userMessageIndex + 1] = ChatMessage(
                                            "assistant", 
                                            currentResponse,
                                            tokensGenerated = tokenCount
                                        )
                                    } else {
                                        messages.add(ChatMessage(
                                            "assistant", 
                                            currentResponse,
                                            tokensGenerated = tokenCount
                                        ))
                                    }
                                }
                                
                                result.onSuccess { fullResponse ->
                                    // Ensure final message is in history
                                    if (messages.isNotEmpty()) {
                                        val lastIndex = messages.lastIndex
                                        logger.log(messages[lastIndex])
                                    }
                                }.onFailure { e ->
                                    val errorMsg = "❌ Error: ${e.message}"
                                    if (messages.isNotEmpty()) {
                                        val lastIndex = messages.lastIndex
                                        messages[lastIndex] = ChatMessage("assistant", errorMsg)
                                    } else {
                                        messages.add(ChatMessage("assistant", errorMsg))
                                    }
                                    logger.log(ChatMessage("assistant", errorMsg))
                                }
                            } finally {
                                isThinking = false
                                currentSpeed = 0.0
                            }
                        }
                    }
                    
                    else -> {
                        // Ignore empty input
                        clearInput()
                    }
                }
            }
        }
    }
}
