plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.ktor)
}


application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

}