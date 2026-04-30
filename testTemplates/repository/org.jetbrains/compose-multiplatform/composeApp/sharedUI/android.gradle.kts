androidLibrary {
    namespace = "${_project.group}"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
    androidResources {
        enable = true
    }
    withHostTest {
        isIncludeAndroidResources = true
    }
}