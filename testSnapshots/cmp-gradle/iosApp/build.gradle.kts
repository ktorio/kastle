plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
}


kotlin {
    iosArm64()
    iosSimulatorArm64()


    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.ui)
        }

    }
}