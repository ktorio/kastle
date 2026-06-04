@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.testBalloon)
    alias(libs.plugins.ksp)
}

kotlin {
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
            implementation(project(":kastle-core"))
            api(libs.kotlinx.io.core)
            api(libs.testBalloon.framework.core)
            api(libs.testBalloon.kotest.assertions)
        }
    }
}
