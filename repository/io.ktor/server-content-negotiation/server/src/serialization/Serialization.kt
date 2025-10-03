package kastle.serialization

import kastle.*
import io.ktor.server.application.Application
import io.ktor.server.application.install

import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        _slots("install")
    }
}

