plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

tasks.jib {
    // this will generate the exported repository automatically
    dependsOn("test")
}

jib {
    from {
        image = "amazoncorretto:21"
    }
    to {
        image = "registry.jetbrains.team/p/kastle/containers/kastle:latest"
    }
    extraDirectories {
        paths {
            path {
                setFrom(layout.projectDirectory.dir("../export"))
                into = "/repository"
            }
        }
    }
    container {
        ports = listOf("2626")
        environment = mapOf("REPOSITORY_PATH" to "/repository")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":local"))

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
    
    testImplementation(project(":client"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}