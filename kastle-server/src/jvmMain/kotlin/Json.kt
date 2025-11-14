package org.jetbrains.kastle.server

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import kotlinx.serialization.json.Json

fun Application.json() {
    dependencies {
        provide {
            Json {
                encodeDefaults = false
                ignoreUnknownKeys = true
                prettyPrint = true
            }
        }
    }
}