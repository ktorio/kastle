plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
    `maven-publish`
}

kotlin {
    jvm()
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
                implementation(npm("htmx.org", "2.0.8"))
            }
        }

        jvmMain {
            dependencies {
                implementation(project(":kastle-local"))
                implementation(project(":kastle-analytics-fus"))

                api(libs.ktor.server.core)
                api(libs.ktor.server.di)

                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.call.logging)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.server.compression)
                implementation(libs.ktor.server.sse)
                implementation(libs.ktor.server.htmx)
                implementation(libs.ktor.htmx.html)
                implementation(libs.ktor.server.html.builder)
                implementation(libs.ktor.json)
                implementation(libs.logback.classic)
                implementation(libs.commonmark)
                implementation(libs.mcp.sdk)
                implementation(libs.ktoml)
                implementation(libs.kotlin.compiler)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":kastle-client"))
                implementation(project(":kastle-test"))
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.content.negotiation)
            }
        }
    }
}

// Copy JS distribution to resources so it can be served by the JVM server
val copyJsDistribution by tasks.registering(Copy::class) {
    dependsOn("jsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/js/productionExecutable"))
    into(layout.buildDirectory.dir("processedResources/jvm/main/js"))
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
