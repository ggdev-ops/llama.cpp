package ai.kgguf.example

import ai.kgguf.GgufMetadataReader
import ai.kgguf.GgufModel
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: metadata-reader <path-to-gguf-file>")
        return
    }

    val filePath = args[0].toPath()
    val reader = GgufMetadataReader.create()

    runBlocking {
        println("Reading GGUF: $filePath")
        try {
            val fileSystem = FileSystem.SYSTEM
            fileSystem.read(filePath) {
                runBlocking {
                    // 1. Low-level parse
                    val metadata = reader.readStructuredMetadata(this@read)
                    
                    // 2. High-level analysis
                    val model = GgufModel(metadata)
                    val cap = model.inferCapabilities()
                    
                    println("\n--- High-Level Model Summary ---")
                    println(model.toString())
                    
                    println("\n--- Detailed Capabilities ---")
                    println("Instruct Model:   ${cap.isInstruct}")
                    println("Tool Support:     ${cap.hasToolSupport}")
                    println("JSON Support:     ${cap.hasJsonSupport}")
                    println("Vision Support:   ${cap.isVision}")
                    println("Quantization:     ${cap.quantization?.label ?: "Unknown"}")

                    println("\n--- Technical Metadata Sample ---")
                    println("Arch:             ${metadata.architecture?.architecture}")
                    println("Tensors:          ${metadata.tensorCount}")
                    println("Raw Keys:         ${metadata.raw.size}")
                    
                    println("\n----------------------------")
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
