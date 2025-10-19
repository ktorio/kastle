plugins {
    alias(libs.plugins.kotlin.multiplatform)

}


dependencies {
    debugImplementation(compose.uiTooling)
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