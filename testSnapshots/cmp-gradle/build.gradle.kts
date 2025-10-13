
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

subprojects {
    group = "com.acme"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}