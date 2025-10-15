plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
}

tasks.named("buildOpenApi") {
    enabled = false
}

tasks.jib {
    // this will generate the exported repository automatically
    dependsOn("kotest")
}

jib {
    from { image = "amazoncorretto:21" }
    to { image = "registry.jetbrains.team/p/kastle/containers/kastle:latest" }
    extraDirectories {
        paths {
            path {
                setFrom("../export")
                into = "/repository"
            }
        }
    }
    container {
        ports = listOf("2626")
        environment = mapOf("REPOSITORY_PATH" to "/repository")
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}

dependencies {
    implementation(project(":kastle-core"))
    implementation(project(":kastle-local"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.htmx)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.htmx.html)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.json)
    implementation(libs.logback.classic)
    implementation(libs.commonmark)
    implementation(libs.mcp.sdk)
    implementation(libs.ktoml)
    testImplementation(project(":kastle-client"))
    testImplementation(project(":kastle-test"))
    testImplementation(libs.ktor.server.test.host)
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}