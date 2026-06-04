@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.testBalloon)
    alias(libs.plugins.ksp)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled = true
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    js {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kastle-core"))
            implementation(ktorLibs.client.core)
            implementation(ktorLibs.client.cio)
            implementation(ktorLibs.client.contentNegotiation)
            implementation(ktorLibs.serialization.kotlinx.json)
            implementation(libs.ktoml)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(ktorLibs.server.testHost)
            implementation(ktorLibs.server.di)
        }
        jvmTest.dependencies {
            implementation(project(":kastle-test"))
            implementation(project(":kastle-server"))
            implementation(project(":kastle-local"))
            implementation(libs.testBalloon.framework.core)
            implementation(libs.testBalloon.kotest.assertions)
        }
    }
}
