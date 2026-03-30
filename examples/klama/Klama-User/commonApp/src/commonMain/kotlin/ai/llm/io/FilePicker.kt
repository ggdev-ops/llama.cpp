package ai.llm.io

import okio.BufferedSource

/**
 * Platform-independent file picker blueprint.
 */
expect class FilePicker() {
    /**
     * Launch the platform-specific picker to select a .gguf file.
     * Returns a BufferedSource for the Librarian to read.
     */
    fun pickGguf(onResult: (BufferedSource?) -> Unit)
}
