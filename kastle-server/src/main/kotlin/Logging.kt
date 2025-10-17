package org.jetbrains.kastle.server

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.kastle.logging.LogLevel
import org.jetbrains.kastle.logging.Logger
import org.slf4j.LoggerFactory

fun Application.logging() {
    val ktorLogger = LoggerFactory.getLogger("Generator")
    dependencies {
        provide { KtorLogger(ktorLogger) }
    }
}

class KtorLogger(val base: io.ktor.util.logging.Logger): Logger {
    override var level: LogLevel = when {
        base.isTraceEnabled -> LogLevel.TRACE
        base.isDebugEnabled -> LogLevel.DEBUG
        base.isInfoEnabled -> LogLevel.INFO
        base.isWarnEnabled -> LogLevel.WARN
        else -> LogLevel.ERROR
    }

    override fun log(
        level: LogLevel,
        exception: Throwable?,
        message: () -> String
    ) {
        when (level) {
            LogLevel.TRACE -> base.trace(message(), exception)
            LogLevel.DEBUG -> base.debug(message(), exception)
            LogLevel.INFO -> base.info(message(), exception)
            LogLevel.WARN -> base.warn(message(), exception)
            LogLevel.ERROR -> base.error(message(), exception)
        }
    }
}