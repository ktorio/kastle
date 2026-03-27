
plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.ktor)
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

dependencies {
    implementation(ktorLibs.server.core)
    implementation(libs.logback.classic)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.htmx)
    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.htmx.html)
}
