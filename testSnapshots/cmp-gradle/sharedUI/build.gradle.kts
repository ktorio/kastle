
plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}


dependencies {
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}

kotlin {
    androidLibrary {
        namespace = "com.acme"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedUI"
            isStatic = true
        }
    }
    js {
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            api(libs.compose.uiToolingPreview)
            api(libs.compose.components.resources)
            api(libs.androidx.lifecycle.viewmodelCompose)
            api(libs.androidx.lifecycle.runtimeCompose)
        }

        commonTest.dependencies {
            kotlin("test")
        
        }
    }
}
