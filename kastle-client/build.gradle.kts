plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotest)
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kastle-core"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.ktoml)
        }
        commonTest.dependencies {
            implementation(project(":kastle-test"))
            implementation(project(":kastle-server"))
            implementation(project(":kastle-local"))
            implementation(libs.kotlin.test)
            implementation(libs.ktor.server.test.host)
            implementation(libs.ktor.server.di)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.junit5)
        }
    }
}