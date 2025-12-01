
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.multiplatformLibrary) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

subprojects {
    group = "com.acme"
    version = "1.0.0-SNAPSHOT"
}