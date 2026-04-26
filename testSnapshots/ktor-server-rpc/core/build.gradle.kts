
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinx.rpc)
    alias(libs.plugins.kotlin.serialization)
}


kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.rpc.core)
            api(libs.kotlinx.rpc.serializationJson)
        }

        commonTest.dependencies {
            kotlin("test")
        }
    }
}

