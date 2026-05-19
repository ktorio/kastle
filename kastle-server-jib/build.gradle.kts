plugins {
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
}

val exportDir = rootDir.resolve("export")

val exportPluginRepository by tasks.registering(Exec::class) {
    workingDir = rootDir.resolve("repository-intellij")
    commandLine("./gradlew", "kslExportToCbor", "-PexportPath=${exportDir.absolutePath}")
}

tasks.jib {
    dependsOn("exportPluginRepository")
}
tasks.jibBuildTar {
    dependsOn("exportPluginRepository")
}

jib {
    from { image = "amazoncorretto:21" }
    to { image = "registry.jetbrains.team/p/kastle/containers/kastle-intellij-plugins:latest" }
    extraDirectories {
        paths {
            path {
                setFrom(exportDir)
                into = "/repository"
            }
        }
    }
    container {
        ports = listOf("2626")
        environment = mapOf("REPOSITORY_PATH" to "/repository")
        creationTime = "USE_CURRENT_TIMESTAMP"
        jvmFlags = listOf(
            "-Dlogback.configurationFile=logback-cloud.xml"
        )
    }
}

dependencies {
    implementation(project(":kastle-server"))
    api(ktorLibs.server.core)
    api(ktorLibs.server.di)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.sse)
    implementation(ktorLibs.server.htmx)
    implementation(ktorLibs.htmx.html)
    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.commonmark)
    implementation(libs.mcp.sdk)
    implementation(libs.ktoml)
    testImplementation(project(":kastle-client"))
    testImplementation(project(":kastle-test"))
    testImplementation(ktorLibs.server.testHost)
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}
