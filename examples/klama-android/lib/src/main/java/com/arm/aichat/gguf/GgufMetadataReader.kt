package com.arm.aichat.gguf

import okio.BufferedSource
import java.io.File
import java.io.IOException

/**
 * Interface for reading GGUF metadata using Okio for cross-platform compatibility.
 */
interface GgufMetadataReader {
    /**
     * Reads the magic number from the specified file.
     */
    suspend fun ensureSourceFileFormat(file: File): Boolean

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
        ): GgufMetadataReader = com.arm.aichat.internal.gguf.GgufMetadataReaderImpl(
            skipKeys = skipKeys,
            arraySummariseThreshold = arraySummariseThreshold
        )
    }
}

class InvalidFileFormatException : IOException()
