@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.testBalloon)
    alias(libs.plugins.ksp)
    `maven-publish`
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {}

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                withJs()
                withWasmJs()
                withIos()
            }
        }
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
            api(project(":kastle-templates"))
            api(libs.kotlinx.coroutines)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.cbor)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.serialization.json.io)

            implementation(libs.kaml)
            implementation(libs.ktoml)
        }
        commonTest.dependencies {
            implementation(libs.testBalloon.framework.core)
            implementation(libs.testBalloon.kotest.assertions)
        }
    }
}
