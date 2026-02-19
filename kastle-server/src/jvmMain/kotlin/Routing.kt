package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.config.property
import io.ktor.server.config.propertyOrNull
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.logging.Logger

@OptIn(ExperimentalKtorApi::class)
fun Application.routing() {
    val repository: PackRepository by dependencies
    val generator: ProjectGenerator by dependencies
    val json: Json by dependencies
    val logger: Logger by dependencies
    val basePath = propertyOrNull<String>("frontEnd.basePath") ?: ""

    routing {
        staticResources("/assets", "/assets")
        frontEnd(repository, generator, logger, basePath)
        backEnd(repository, generator, json, logger)

        // health check needed for cloud deployment
        get("/healthz") {
            call.respondText("OK")
        }
    }
}
