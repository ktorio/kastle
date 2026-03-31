package com.acme

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.html.respondHtml
import io.ktor.server.html.respondHtmlFragment
import io.ktor.server.http.content.staticResources
import kotlinx.html.*
import kotlin.random.Random

fun Application.configureRouting() {
    routing {
        val random = Random(System.currentTimeMillis())
        
        staticResources("/", "/web")
        
        get("/") {
            call.respondHtml {
                leaderboardPage(random)
            }
        }
        
        get("/more-rows") {
            call.respondHtmlFragment {
                table {
                    tbody {
                        randomRows(random)
                    }
                }
            }
        }
    }
}