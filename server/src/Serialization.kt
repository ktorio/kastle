package org.jetbrains.kastle

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.serialization() {
    install(ContentNegotiation) {
        json()
    }
}