package org.jetbrains.kastle.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText

fun Application.errorHandling() {
    install(StatusPages) {
        exception<Exception> { call, cause ->
            call.application.environment.log.error("Error in endpoint", cause)
            call.respondText(ContentType.Text.Html, HttpStatusCode.InternalServerError) {
                "<h1>${cause::class.simpleName}</h1><p>${cause.localizedMessage}</p>"
            }
        }
    }
}