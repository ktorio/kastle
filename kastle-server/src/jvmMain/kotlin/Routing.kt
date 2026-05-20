package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator

fun Application.routing() {
    val repository: PackRepository by dependencies
    val generator: ProjectGenerator by dependencies
    val json: Json by dependencies
    val basePath = propertyOrNull<String>("frontEnd.basePath") ?: ""

    routing {
        staticResources("/assets", "/assets")
        frontEnd(repository, generator, basePath)
        backEnd(repository, generator, json)

        // health check needed for cloud deployment
        get("/healthz") {
            call.respondText("OK")
        }
    }
}
