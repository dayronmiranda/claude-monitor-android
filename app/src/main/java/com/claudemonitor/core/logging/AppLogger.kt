package com.claudemonitor.core.logging

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logging interface for the application.
 */
interface AppLogger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)

    // Structured logging
    fun log(level: LogLevel, tag: String, message: String, data: Map<String, Any>? = null)

    // Analytics events
    fun event(name: String, params: Map<String, Any> = emptyMap())

    // Screen tracking
    fun screen(screenName: String, screenClass: String? = null)

    // User properties
    fun setUserProperty(name: String, value: String?)
    fun setUserId(userId: String?)
}

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}

/**
 * Default logger implementation using Android Log.
 */
@Singleton
class AppLoggerImpl @Inject constructor(
    private val fileLogger: FileLogger?,
    private val crashReporter: CrashReporter?
) : AppLogger {

    // TODO: Inject from build config
    private val isDebug = true

    override fun v(tag: String, message: String) {
        if (isDebug) {
            Log.v(tag, message)
            fileLogger?.log(LogLevel.VERBOSE, tag, message)
        }
    }

    override fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d(tag, message)
            fileLogger?.log(LogLevel.DEBUG, tag, message)
        }
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
        fileLogger?.log(LogLevel.INFO, tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        fileLogger?.log(LogLevel.WARNING, tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            crashReporter?.recordException(throwable, mapOf("tag" to tag, "message" to message))
        } else {
            Log.e(tag, message)
        }
        fileLogger?.log(LogLevel.ERROR, tag, message, throwable)
    }

    override fun log(level: LogLevel, tag: String, message: String, data: Map<String, Any>?) {
        val formattedMessage = if (data != null) {
            "$message | data=$data"
        } else {
            message
        }

        when (level) {
            LogLevel.VERBOSE -> v(tag, formattedMessage)
            LogLevel.DEBUG -> d(tag, formattedMessage)
            LogLevel.INFO -> i(tag, formattedMessage)
            LogLevel.WARNING -> w(tag, formattedMessage)
            LogLevel.ERROR -> e(tag, formattedMessage)
        }
    }

    override fun event(name: String, params: Map<String, Any>) {
        d("Analytics", "Event: $name, params: $params")
        // Firebase Analytics would go here
        // Firebase.analytics.logEvent(name, Bundle().apply { ... })
    }

    override fun screen(screenName: String, screenClass: String?) {
        d("Analytics", "Screen: $screenName, class: $screenClass")
        // Firebase Analytics screen tracking would go here
    }

    override fun setUserProperty(name: String, value: String?) {
        d("Analytics", "UserProperty: $name = $value")
        // Firebase Analytics would go here
    }

    override fun setUserId(userId: String?) {
        d("Analytics", "UserId: $userId")
        // Firebase Analytics would go here
    }
}

/**
 * File logger for debugging.
 */
class FileLogger(
    private val logDir: File,
    private val maxFileSizeMb: Int = 5,
    private val maxFiles: Int = 3
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        logDir.mkdirs()
        cleanOldLogs()
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logLine = buildString {
            append("$timestamp ${level.name.first()} $tag: $message")
            if (throwable != null) {
                append("\n${throwable.stackTraceToString()}")
            }
            append("\n")
        }

        try {
            val logFile = getCurrentLogFile()
            logFile.appendText(logLine)
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to write log", e)
        }
    }

    private fun getCurrentLogFile(): File {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val file = File(logDir, "app-$today.log")

        // Rotate if too large
        if (file.exists() && file.length() > maxFileSizeMb * 1024 * 1024) {
            val rotatedFile = File(logDir, "app-$today-${System.currentTimeMillis()}.log")
            file.renameTo(rotatedFile)
        }

        return file
    }

    private fun cleanOldLogs() {
        val files = logDir.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        files.drop(maxFiles).forEach { it.delete() }
    }

    fun getLogFiles(): List<File> {
        return logDir.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearLogs() {
        logDir.listFiles()?.forEach { it.delete() }
    }
}

/**
 * Crash reporter interface.
 */
interface CrashReporter {
    fun recordException(throwable: Throwable, data: Map<String, Any>? = null)
    fun log(message: String)
    fun setCustomKey(key: String, value: String)
    fun setUserId(userId: String?)
}

/**
 * No-op crash reporter for when Firebase isn't configured.
 */
class NoOpCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable, data: Map<String, Any>?) {}
    override fun log(message: String) {}
    override fun setCustomKey(key: String, value: String) {}
    override fun setUserId(userId: String?) {}
}

/**
 * Extension for logging with a class tag.
 */
inline fun <reified T> AppLogger.forClass(): TaggedLogger {
    return TaggedLogger(this, T::class.java.simpleName)
}

class TaggedLogger(
    private val logger: AppLogger,
    private val tag: String
) {
    fun v(message: String) = logger.v(tag, message)
    fun d(message: String) = logger.d(tag, message)
    fun i(message: String) = logger.i(tag, message)
    fun w(message: String, throwable: Throwable? = null) = logger.w(tag, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = logger.e(tag, message, throwable)
}
