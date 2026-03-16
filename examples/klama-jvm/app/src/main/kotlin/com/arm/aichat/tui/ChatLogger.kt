package com.arm.aichat.tui

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChatLogger(private val logFile: String) {

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        val file = File(logFile)
        file.appendText("\n--- Session started at ${timestampFormatter.format(Instant.now())} ---\n")
    }

    fun log(message: ChatMessage) {
        val timestamp = timestampFormatter.format(Instant.now())
        // For multi-line messages, indent subsequent lines to align with the start of the first line's content.
        val indentation = " ".repeat("[$timestamp] ${message.role}: ".length)
        val formattedContent = message.content.lines().joinToString("\n$indentation")
        val formattedMessage = "[$timestamp] ${message.role}: $formattedContent\n"
        File(logFile).appendText(formattedMessage)
    }
}
