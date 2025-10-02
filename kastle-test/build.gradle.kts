plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotest)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kastle-core"))
//            implementation(libs.kotlin.test)
//            implementation(libs.kotlinx.coroutines.test)
            api(libs.kotlinx.io.core)
            api(libs.kotest.framework)
            api(libs.kotest.assertions)
        }
    }

}