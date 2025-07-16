plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}


kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.material3)
        }

    }
}