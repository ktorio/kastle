plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
    alias(libs.plugins.android.application)
}


kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }


    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.android.activity.compose)
        }

    }
}