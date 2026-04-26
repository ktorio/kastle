
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlin-serialization)
}


dependencies {
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
}

