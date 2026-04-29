
plugins {
    alias(libs.plugins.kotlinx.rpc)
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
            implementation(ktorLibs.client.core)
            implementation(libs.kotlinx.rpc.client)
            implementation(projects.core)
        }

    }
}

