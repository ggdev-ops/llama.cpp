package com.arm.aichat.main

import com.arm.aichat.tui.ChatOrchestrator
import com.arm.aichat.tui.ChatTui
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println("Usage: gradle run --args='/path/to/your/model.gguf'")
        return@runBlocking
    }

    val modelPath = args[0]
    
    try {
        // Initialize the engine
        val engine = ChatOrchestrator(modelPath)
        
        // Load the model
        println("Loading model...")
        engine.initialize().onFailure { e ->
            println("Failed to load model: ${e.message}")
            return@runBlocking
        }
        
        // Set system prompt
        println("Setting system prompt...")
        val systemPrompt = """
            You are a knowledgeable programming tutor specializing in C and C++.
            You provide detailed, educational responses.
            When asked about C/C++ concepts, you explain them thoroughly with:
            - Clear explanations
            - Best practices
            Keep responses focused and educational.
        """.trimIndent()
        
        engine.setSystemPrompt(systemPrompt).onSuccess {
            println("System prompt set successfully!")
            delay(1000)
        }.onFailure { e ->
            println("Warning: Could not set system prompt: ${e.message}")
        }
        
        // Start the TUI
        val tui = ChatTui(engine)
        tui.start()
        
    } catch (e: Exception) {
        println("Error during initialization: ${e.message}")
        e.printStackTrace()
    }
}
