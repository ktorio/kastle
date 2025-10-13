plugins {
    alias(libs.plugins.kotlin.multiplatform)

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