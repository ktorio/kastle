
plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}


application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(libs.logback.classic)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
}
