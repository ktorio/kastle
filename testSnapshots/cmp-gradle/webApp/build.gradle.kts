
plugins {
    alias(libs.plugins.kotlin.multiplatform)

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
            implementation(project(":shared"))
        }

    }
}