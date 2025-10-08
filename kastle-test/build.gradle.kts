plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotest)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kastle-core"))
            api(libs.kotlinx.io.core)
            api(libs.kotest.framework)
            api(libs.kotest.assertions)
        }
    }

}