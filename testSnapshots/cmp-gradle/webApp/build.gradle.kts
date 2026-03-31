
plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}


kotlin {
    js {
        browser()
        binaries.executable()
    
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    
    }

    sourceSets {
        webMain.dependencies {
            implementation(project(":sharedUI"))
        }

    }
}
