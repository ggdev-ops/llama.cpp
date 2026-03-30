package ai.llm.io

import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class FilePicker actual constructor() {
    actual fun pickGguf(onResult: (BufferedSource?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Select GGUF Model", FileDialog.LOAD)
        dialog.file = "*.gguf"
        dialog.isVisible = true
        
        val directory = dialog.directory
        val fileName = dialog.file
        
        if (directory != null && fileName != null) {
            val file = File(directory, fileName)
            val path = file.absolutePath.toPath()
            onResult(FileSystem.SYSTEM.source(path).buffer())
        } else {
            onResult(null)
        }
    }
}
