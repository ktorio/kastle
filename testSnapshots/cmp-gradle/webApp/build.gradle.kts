
plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.$libs.composeMultiplatform)
    alias(libs.plugins.$libs.composeCompiler)
}


kotlin {
    js {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        webMain.dependencies {
            implementation(project(":sharedUI"))
        }

    }
}