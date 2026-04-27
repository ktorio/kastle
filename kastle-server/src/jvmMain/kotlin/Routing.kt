package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.analytics.AnalyticsRepository
import org.jetbrains.kastle.server.ui.wizard.wizardFrontEnd

@OptIn(ExperimentalKtorApi::class)
fun Application.routing() {
    val repository: PackRepository by dependencies
    val generator: ProjectGenerator by dependencies
    val analytics: AnalyticsRepository by dependencies
    val json: Json by dependencies
    val basePath = propertyOrNull<String>("frontEnd.basePath") ?: ""
    val googleTagManagerId = propertyOrNull<String>("frontEnd.googleTagManagerId")

    routing {
        staticResources("/assets", "/assets")
        staticResources("$basePath/assets", "/assets")
        frontEnd(repository, generator, analytics, basePath)
        backEnd(repository, generator, analytics, json)
        wizardFrontEnd(repository, generator, analytics, basePath, googleTagManagerId)

        // health check needed for cloud deployment
        get("/healthz") {
            call.respondText("OK")
        }
    }
}
