
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(ktorLibs.plugins.ktor)
}


kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.htmx.html)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.server.htmx)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    testImplementation(libs.kotlin.test)
    testImplementation(ktorLibs.server.testHost)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}
val copyWebDistToServerResources by tasks.registering(Copy::class) {
    dependsOn(project(":web").tasks.named("jsBrowserDistribution"))
    from(project(":web").layout.buildDirectory.dir("dist/js/productionExecutable"))
    into(layout.buildDirectory.dir("resources/main/web"))
}

tasks.named("processResources") {
    dependsOn(copyWebDistToServerResources)
}
