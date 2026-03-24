package ai.klama.gguf

import okio.BufferedSource
import okio.Path

/**
 * Pure KMP Interface for reading GGUF metadata.
 * No java.io or Android dependencies allowed.
 */
interface GgufMetadataReader {
    /**
     * Reads the magic number using the provided FileSystem and Path.
     */
    suspend fun ensureSourceFileFormat(fileSystem: okio.FileSystem, path: Path): Boolean

    /**
     * Reads the magic number from the provided BufferedSource.
     */
    suspend fun ensureSourceFileFormat(source: BufferedSource): Boolean

    /**
     * Reads and parses GGUF metadata from an Okio BufferedSource.
     */
    suspend fun readStructuredMetadata(source: BufferedSource): GgufMetadata

    companion object {
        private val DEFAULT_SKIP_KEYS = setOf(
            "tokenizer.chat_template",
            "tokenizer.ggml.scores",
            "tokenizer.ggml.tokens",
            "tokenizer.ggml.token_type"
        )

        fun create(
            skipKeys: Set<String> = DEFAULT_SKIP_KEYS,
            arraySummariseThreshold: Int = 1_000
        ): GgufMetadataReader = ai.klama.internal.gguf.GgufMetadataReaderImpl(
            skipKeys = skipKeys,
            arraySummariseThreshold = arraySummariseThreshold
        )
    }
}

class InvalidFileFormatException : Exception()
class KlamaIOException(message: String) : Exception(message)
