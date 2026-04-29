
plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

subprojects {
    group = "com.acme"
    version = "1.0.0-SNAPSHOT"
}
