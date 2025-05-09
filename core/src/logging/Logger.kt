package org.jetbrains.kastle.logging

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.toLocalDateTime
import java.io.PrintStream
import java.time.format.DateTimeFormatter

interface Logger {
    companion object {
        var level: LogLevel = LogLevel.DEBUG
    }

    var level: LogLevel

    fun trace(message: () -> String) = log(LogLevel.TRACE, message = message)
    fun debug(message: () -> String) = log(LogLevel.DEBUG, message = message)
    fun info(message: () -> String) = log(LogLevel.INFO, message = message)
    fun warn(exception: Throwable? = null, message: () -> String) = log(LogLevel.WARN, exception, message)
    fun error(exception: Throwable? = null, message: () -> String) = log(LogLevel.ERROR, exception, message)
    fun log(level: LogLevel, exception: Throwable? = null, message: () -> String)
}

class ConsoleLogger(
    override var level: LogLevel = Logger.level,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val timeFormat: DateTimeFormat<LocalTime> = LocalTime.Formats.ISO,
): Logger {
    override fun log(
        level: LogLevel,
        exception: Throwable?,
        message: () -> String
    ) {
        if (level < this.level) return
        println(buildString {
            append(clock.now().toLocalDateTime(timeZone).time.format(timeFormat))
            append(" [")
            append(level.name)
            append("] ")
            if (level.name.length < 5)
                append(' ')
            append(message())
        })
    }
}

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}