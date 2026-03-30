package ai.llm.domain

import ai.kgguf.GgufMetadataReader
import ai.llm.data.ModelStatus
import ai.llm.data.SettingsRepository
import ai.llm.engine.EngineBridge
import ai.llm.io.FileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

class ModelManager(
    private val fileSystemProvider: FileSystemProvider,
    private val settingsRepository: SettingsRepository,
    internal val engineBridge: EngineBridge
) {
    companion object {
        fun createDefault(): ModelManager {
            val fileSystemProvider = FileSystemProvider()
            val settingsRepository = SettingsRepository(fileSystemProvider)
            val engineBridge = ai.llm.engine.createEngineBridge()
            return ModelManager(fileSystemProvider, settingsRepository, engineBridge)
        }
    }

    /**
     * Imports a model from a BufferedSource (from file picker), copies it to a safe location,
     * reads its metadata, saves its status, and loads it into the engine.
     */
    suspend fun importAndLoadModel(source: BufferedSource, originalFileName: String) = withContext(Dispatchers.Default) {
        // Get the safe zone directory and create a destination path
        val safeDir = fileSystemProvider.getSafeZoneDirectory()
        val destFile = safeDir.resolve(originalFileName)

        // Copy the source to the safe zone
        FileSystem.SYSTEM.sink(destFile).buffer().use { sink ->
            sink.writeAll(source)
        }

        val modelPathString = destFile.toString()

        // Now that we have a real file, read its metadata
        val metadata = FileSystem.SYSTEM.source(destFile).buffer().use {
            GgufMetadataReader.create().readStructuredMetadata(it)
        }

        // Save status to DataStore
        val modelStatus = ModelStatus(
            modelPath = modelPathString,
            modelName = metadata.basic.name ?: originalFileName,
            architecture = metadata.architecture?.architecture ?: "Unknown",
            chatHistory = null // Chat history will be managed separately
        )
        settingsRepository.saveModelStatus(modelStatus)

        // Actually load the model into the engine
        engineBridge.loadModel(modelPathString)
    }
    
    suspend fun initializeExistingModel(modelPath: String) {
        engineBridge.loadModel(modelPath)
    }
}
