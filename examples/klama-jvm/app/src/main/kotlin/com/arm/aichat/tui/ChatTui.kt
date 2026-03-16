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

class ChatTui(private val engine: ChatOrchestrator) {
    
    fun start() = session {
        val scope = CoroutineScope(Dispatchers.IO)
        val thinkingAnim = renderAnimOf(4, 500.milliseconds) { frameIndex ->
            text(".".repeat(frameIndex))
        }
        
        // UI State
        val messages = liveListOf<ChatMessage>()
        var isThinking by liveVarOf(false)
        var showHelp by liveVarOf(false)
        
        section {
            // Header
            cyan { bold { textLine("🤖 C/C++ Programming Tutor") } }
            
            // Model Info Section
            engine.metadata?.let { meta ->
                yellow { text("📦 Model: ") }
                white { text("${meta.basic.name ?: "Unknown"} ") }
                dim { text("[Arch: ${meta.architecture?.architecture ?: "???"} | Tensors: ${meta.tensorCount}]") }
                textLine()
            }

            textLine("Type '/help' for commands, '/exit' to quit")
            
            if (engine.debugMode) {
                red { bold { textLine("🔧 DEBUG MODE ENABLED") } }
            }
            
            textLine()
            
            // Settings
            yellow { bold { text("⚙️ Current Settings: ") } }
            text("temp=")
            cyan { text("%.2f".format(engine.settings.temperature)) }
            text(", top_p=")
            cyan { text("%.2f".format(engine.settings.topP)) }
            text(", top_k=")
            cyan { text(engine.settings.topK.toString()) }
            text(", repeat_penalty=")
            cyan { text("%.2f".format(engine.settings.repeatPenalty)) }
            textLine()
            
            textLine()
            
            // Messages
            messages.forEach { message ->
                when (message.role) {
                    "user" -> {
                        green { bold { text("You: ") } }
                        textLine(message.content)
                    }
                    "assistant" -> {
                        blue { bold { text("Tutor: ") } }
                        message.content.lines().forEachIndexed { index, line ->
                            if (index == 0) textLine(line) else {
                                text("       ")
                                textLine(line)
                            }
                        }
                        textLine()
                    }
                    "system" -> {
                        yellow { textLine("[System: ${message.content}]") }
                    }
                }
            }
            
            // Help
            if (showHelp) {
                yellow { bold { textLine("\n📚 Available Commands:") } }
                textLine("  /exit              - Quit the application")
                textLine("  /reset             - Clear conversation history")
                textLine("  /settings          - Show current settings")
                textLine("  /set temp <value>  - Set temperature (0.0-2.0)")
                textLine("  /set top_p <value> - Set top_p (0.0-1.0)")
                textLine("  /set top_k <value> - Set top_k (1-100)")
                textLine("  /set penalty <val> - Set repeat penalty (0.0-2.0)")
                textLine("  /debug             - Toggle debug mode")
                textLine("  /sysprompt <text>  - Change system prompt")
                textLine()
            }
            
            // Input area
            if (isThinking) {
                text("Tutor is thinking")
                thinkingAnim(this)
            } else {
                text("> ")
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
                    
                    is com.arm.aichat.tui.ChatCommand.Reset -> {
                        messages.clear()
                        engine.resetConversation()
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.ShowSettings -> {
                        // Settings are shown automatically
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.SetSystemPrompt -> {
                        scope.launch {
                            isThinking = true
                            try {
                                engine.setSystemPrompt(command.prompt).onSuccess {
                                    messages.add(ChatMessage("system", "System prompt updated"))
                                }.onFailure { e ->
                                    messages.add(ChatMessage("system", "Failed to update system prompt: ${e.message}"))
                                }
                            } finally {
                                isThinking = false
                                clearInput()
                            }
                        }
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.SetParameter -> {
                        engine.validateParameter(command.param, command.value).onSuccess { validatedValue ->
                            engine.updateParameter(command.param, validatedValue)
                            if (engine.debugMode) {
                                println("[DEBUG] Updated ${command.param} to $validatedValue")
                            }
                        }.onFailure { e ->
                            println("[ERROR] ${e.message}")
                        }
                        clearInput()
                    }
                    
                    is com.arm.aichat.tui.ChatCommand.UserMessage -> {
                        val userMessage = command.content
                        messages.add(ChatMessage("user", userMessage))
                        clearInput()
                        
                        scope.launch {
                            isThinking = true
                            
                            try {
                                val userMessageIndex = messages.lastIndex
                                var currentResponse = ""
                                
                                val result = engine.sendMessage(userMessage) { token ->
                                    currentResponse += token
                                    if (userMessageIndex + 1 < messages.size) {
                                        messages[userMessageIndex + 1] = ChatMessage("assistant", currentResponse)
                                    } else {
                                        messages.add(ChatMessage("assistant", currentResponse))
                                    }
                                }
                                
                                result.onFailure { e ->
                                    val errorMsg = "[Error: ${e.message}]"
                                    if (userMessageIndex + 1 < messages.size) {
                                        messages[userMessageIndex + 1] = ChatMessage("assistant", errorMsg)
                                    } else {
                                        messages.add(ChatMessage("assistant", errorMsg))
                                    }
                                }
                            } finally {
                                isThinking = false
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
