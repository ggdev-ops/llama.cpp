package ai.kgguf

import okio.Path.Companion.toPath
import okio.buffer
import okio.FileSystem
import java.io.File

/**
 * JVM-specific extensions for GgufMetadataReader.
 */
suspend fun GgufMetadataReader.readMetadata(file: File): GgufMetadata {
    val path = file.absolutePath.toPath()
    return FileSystem.SYSTEM.source(path).buffer().use { source ->
        readStructuredMetadata(source)
    }
}

suspend fun GgufMetadataReader.checkFormat(file: File): Boolean {
    val path = file.absolutePath.toPath()
    return ensureSourceFileFormat(FileSystem.SYSTEM, path)
}
