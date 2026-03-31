
plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.acme"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
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
