// Use version catalog
plugins {
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.compose") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("com.android.application") apply false
}

subprojects {
    group = "com.acme"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}