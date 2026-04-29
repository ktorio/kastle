
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.acme"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    testImplementation(libs.kotlin.test)
    testImplementation(ktorLibs.server.testHost)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}
