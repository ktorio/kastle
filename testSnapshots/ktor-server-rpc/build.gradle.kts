
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.rpc) apply false
}

subprojects {
    group = "com.acme"
    version = "1.0.0-SNAPSHOT"
}
