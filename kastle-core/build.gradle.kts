@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
    `maven-publish`
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
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }


        val nonJvmMain by creating {
            dependsOn(commonMain.get())
        }

        val nonJvmTest by creating {
            dependsOn(commonTest.get())
        }

        val jvmMain by getting
        val jvmTest by getting

        val iosArm64Main by getting {
            dependsOn(nonJvmMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(nonJvmMain)
        }
        val jsMain by getting {
            dependsOn(nonJvmMain)
        }
        val wasmJsMain by getting {
            dependsOn(nonJvmMain)
        }

        val iosArm64Test by getting {
            dependsOn(nonJvmTest)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(nonJvmTest)
        }
        val jsTest by getting {
            dependsOn(nonJvmTest)
        }
        val wasmJsTest by getting {
            dependsOn(nonJvmTest)
        }

        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }
    }
}
