package com.arm.aichat.main

import com.arm.aichat.tui.ChatOrchestrator
import com.arm.aichat.tui.ChatTui
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println("""
            ╔══════════════════════════════════════════════════════════╗
            ║     C/C++ Programming Tutor - llama.cpp TUI Client       ║
            ╚══════════════════════════════════════════════════════════╝
            
            Usage: gradle run --args='/path/to/your/model.gguf'
            
            Example: gradle run --args='~/models/llama-2-7b-chat.gguf'
            
            Available commands once running:
              /help     - Show all commands
              /bench    - Run performance benchmark
              /stats    - Show chat statistics
              /settings - View current settings
              /clear    - Clear screen
              /exit     - Quit
        """.trimIndent())
        return@runBlocking
    }

    val modelPath = args[0]
    
    try {
        println("🚀 Initializing C/C++ Programming Tutor...")
        println("📁 Model path: $modelPath")
        
        // Initialize the engine
        val engine = ChatOrchestrator(modelPath)
        
        // Load the model with progress indication
        println("📦 Loading model (this may take a moment)...")
        engine.initialize().onFailure { e ->
            println("❌ Failed to load model: ${e.message}")
            e.printStackTrace()
            return@runBlocking
        }
        
        // Get and display system info
        println("ℹ️  System info: ${engine.getSystemInfo()}")
        
        // Set system prompt
        println("⚙️  Setting up system prompt...")
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
        
        engine.setSystemPrompt(systemPrompt).onSuccess {
            println("✅ System prompt set successfully!")
            delay(1000)
        }.onFailure { e ->
            println("⚠️  Warning: Could not set system prompt: ${e.message}")
        }
        
        // Optional: Run quick benchmark on startup
        println("📊 Running quick benchmark (3 runs)...")
        engine.runBenchmark(pp = 256, tg = 64, nr = 3).onSuccess { results ->
            println("✅ Benchmark complete!")
            println(results)
            delay(2000)
        }.onFailure { e ->
            println("⚠️  Benchmark skipped: ${e.message}")
        }
        
        // Start the TUI
        println("🎨 Launching TUI interface...")
        delay(1000)
        
        val tui = ChatTui(engine)
        tui.start()
        
    } catch (e: Exception) {
        println("❌ Fatal error during initialization: ${e.message}")
        e.printStackTrace()
    } finally {
        println("👋 Goodbye!")
    }
}
