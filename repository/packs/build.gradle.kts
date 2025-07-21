plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("org.jetbrains.kastle")
}

kotlin {
    jvm()
}

kastle {
    // Other configuration...
}