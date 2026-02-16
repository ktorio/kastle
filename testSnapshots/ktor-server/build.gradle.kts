
plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.$libs.ktor)
}


application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(libs.logback.classic)
    implementation(ktorLibs.server.netty)
}