
plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.$libs.androidApplication)
    alias(libs.plugins.$libs.composeMultiplatform)
    alias(libs.plugins.$libs.composeCompiler)
}


android {
    namespace = "com.acme"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.acme"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {

    sourceSets {
        androidMain.dependencies {
            implementation(project(":sharedUI"))
            implementation(libs.androidx.activity.compose)
        }

    }
}