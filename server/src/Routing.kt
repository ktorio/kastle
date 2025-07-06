package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator

@OptIn(ExperimentalKtorApi::class)
fun Application.routing() {
    val repository: PackRepository by dependencies
    val generator: ProjectGenerator by dependencies
    val json: Json by dependencies

    routing {
        staticResources("/assets", "/assets")
        frontEnd(repository, generator)
        backEnd(repository, generator, json)
    }
}