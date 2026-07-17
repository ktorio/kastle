
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlinx.rpc)
}


kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.logback.classic)
    implementation(projects.core)
    testImplementation(libs.kotlin.test)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlinx.rpc.client)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}
