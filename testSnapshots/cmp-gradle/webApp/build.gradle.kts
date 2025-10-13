plugins {
    alias(libs.plugins.kotlin.multiplatform)

}


kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }


    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))
        }

    }
}