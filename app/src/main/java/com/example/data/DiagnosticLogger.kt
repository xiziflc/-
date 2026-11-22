package com.example.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight ring-buffer diagnostic event logger persisted to internal filesDir.
 * Captures service lifecycle, WakeLock state, sampling gaps, idle auto-stop,
 * Shizuku hardening commands, and display refresh rate negotiation for debugging.
 */
object DiagnosticLogger {
    private const val LOG_FILE_NAME = "voltpulse_diag_events.log"
    private const val MAX_LINES = 300

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_LINES + 16)
    private var initialized = false
    private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
            logFile = file
            if (file.exists()) {
                runCatching {
                    file.readLines().takeLast(MAX_LINES).forEach { line ->
                        if (line.isNotBlank()) buffer.addLast(line)
                    }
                }
            }
            initialized = true
        }
    }

    fun log(tag: String, message: String) {
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val line = "[$stamp] [$tag] $message"
        synchronized(lock) {
            if (buffer.size >= MAX_LINES) {
                buffer.removeFirst()
            }
            buffer.addLast(line)
            logFile?.let { file ->
                runCatching {
                    if (buffer.size == MAX_LINES) {
                        file.writeText(buffer.joinToString("\n") + "\n")
                    } else {
                        file.appendText(line + "\n")
                    }
                }
            }
        }
    }

    fun getEventsSnapshot(limit: Int = 200): List<String> {
        synchronized(lock) {
            return buffer.toList().takeLast(limit)
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            logFile?.let { runCatching { it.writeText("") } }
        }
    }
}
