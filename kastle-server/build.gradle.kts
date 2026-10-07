import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.testBalloon)
    alias(libs.plugins.ksp)
    `maven-publish`
}

ktor {
    openApi {
        enabled = true
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvm {
        mainRun {
            mainClass = "org.jetbrains.kastle.server.ApplicationKt"
        }
    }
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kastle-core"))
            }
        }

        jsMain {
            dependencies {
                implementation(npm("htmx.org", "2.0.11"))
            }
        }

        jvmMain {
            dependencies {
                implementation(project(":kastle-local"))

                api(ktorLibs.server.core)
                api(ktorLibs.server.di)

                implementation(ktorLibs.server.cio)
                implementation(ktorLibs.server.callLogging)
                implementation(ktorLibs.server.contentNegotiation)
                implementation(ktorLibs.server.statusPages)
                implementation(ktorLibs.server.compression)
                implementation(ktorLibs.server.sse)
                implementation(ktorLibs.server.htmx)
                implementation(ktorLibs.htmx.html)
                implementation(ktorLibs.server.htmlBuilder)
                implementation(ktorLibs.serialization.kotlinx.json)
                implementation(ktorLibs.server.swagger)

                implementation(libs.logback.classic)
                implementation(libs.commonmark)
                implementation(libs.mcp.sdk)
                implementation(libs.ktoml)
                runtimeOnly(libs.kotlin.compiler)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":kastle-client"))
                implementation(project(":kastle-test"))
                implementation(ktorLibs.server.testHost)
                implementation(ktorLibs.client.contentNegotiation)
            }
        }
    }
}

// Copy JS distribution to resources so it can be served by the JVM server
val copyJsDistribution = tasks.register<Copy>("copyJsDistribution") {
    description = "Moves browser distribution to server resources"
    dependsOn("jsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/js/productionExecutable"))
    into(layout.buildDirectory.dir("processedResources/jvm/main/assets"))
}

tasks.named("jvmProcessResources") {
    dependsOn(copyJsDistribution)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
