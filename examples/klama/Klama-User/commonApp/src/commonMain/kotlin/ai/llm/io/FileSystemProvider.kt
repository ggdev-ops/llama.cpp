package ai.llm.io

import okio.Path

expect class FileSystemProvider() {
    fun getSafeZoneDirectory(): Path
    fun copyToSafeZone(sourceUri: String, fileName: String): Path
}