package klama.ai.compose.io

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

actual class FileSystemProvider {
    actual fun getSafeZoneDirectory(): Path {
        val dir = System.getProperty("user.home").toPath().resolve(".klama")
        FileSystem.SYSTEM.createDirectories(dir)
        return dir
    }

    actual fun copyToSafeZone(sourceUri: String, fileName: String): Path {
        // For JVM, we assume the uriString is a direct file path and just copy it
        val sourcePath = sourceUri.toPath()
        val destDir = getSafeZoneDirectory().resolve("models")
        FileSystem.SYSTEM.createDirectories(destDir)
        val destFile = destDir.resolve(fileName)
        
        // If file already exists, we assume it's the correct one.
        if (FileSystem.SYSTEM.exists(destFile) && FileSystem.SYSTEM.metadata(destFile).size?.let { it > 0 } == true) {
            println("Model file already exists, skipping copy: ${destFile.toFile().absolutePath}")
            return destFile
        }
        
        println("Starting copy of $sourceUri to ${destFile.toFile().absolutePath}")
        FileSystem.SYSTEM.copy(sourcePath, destFile)
        println("Finished copying file.")
        
        return destFile
    }
}
