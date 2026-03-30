package ai.kgguf

import android.content.Context
import android.net.Uri
import okio.buffer
import okio.source

/**
 * Android-specific extensions for GgufMetadataReader to handle Uris.
 */
suspend fun GgufMetadataReader.readMetadata(context: Context, uri: Uri): GgufMetadata {
    val inputStream = context.contentResolver.openInputStream(uri) 
        ?: throw Exception("Failed to open Uri: $uri")
    
    return inputStream.source().buffer().use { source ->
        readStructuredMetadata(source)
    }
}

suspend fun GgufMetadataReader.checkFormat(context: Context, uri: Uri): Boolean {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return false
    return inputStream.source().buffer().use { source ->
        ensureSourceFileFormat(source)
    }
}
