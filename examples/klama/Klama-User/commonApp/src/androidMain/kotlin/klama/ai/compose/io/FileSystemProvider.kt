package klama.ai.compose.io

import android.content.Context
import android.net.Uri
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AndroidContextProvider {
    lateinit var context: Context
}

actual class FileSystemProvider {
    actual fun getSafeZoneDirectory(): Path {
        val dir = File(AndroidContextProvider.context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.toOkioPath()
    }

    actual fun copyToSafeZone(sourceUri: String, fileName: String): Path {
        val context = AndroidContextProvider.context
        val modelsDir = File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }

        val destFile = File(modelsDir, fileName)

        // If file already exists, we assume it's the correct one.
        // In a real app, you might add checksum validation.
        if (destFile.exists() && destFile.length() > 0) {
            println("Model file already exists, skipping copy: ${destFile.absolutePath}")
            return destFile.toOkioPath()
        }

        println("Starting copy of $sourceUri to ${destFile.absolutePath}")

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                ?: throw Exception("Failed to open input stream for URI: $sourceUri")
            outputStream = FileOutputStream(destFile)
            inputStream.copyTo(outputStream)
            println("Finished copying file.")
        } catch (e: Exception) {
            // Clean up partially created file on error
            if (destFile.exists()) {
                destFile.delete()
            }
            throw Exception("Failed to copy model file: ${e.message}", e)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return destFile.toOkioPath()
    }
}