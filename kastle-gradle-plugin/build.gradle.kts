plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pluginPublish)
}

group = libs.plugins.kastle.get().pluginId
version = libs.plugins.kastle.get().version

dependencies {
    implementation(gradleApi())
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.androidApplication)
    implementation(libs.gradlePlugin.androidMultiplatformLibrary)
    implementation(libs.gradlePlugin.composeCompiler)
    implementation(libs.gradlePlugin.composeMultiplatform)

    compileOnly(libs.kotlin.compiler)

    implementation(project(":kastle-core"))
    implementation(project(":kastle-local"))
    implementation(project(":kastle-server"))
    implementation(ktorLibs.server.cio)
    implementation(libs.kaml)

    testImplementation(libs.gradlePlugin.kotlin)
    testImplementation(libs.kotlin.test)
}

gradlePlugin {
    website = "https://jetbrains.org"
    vcsUrl = "https://github.com/ktorio/kastle"

    plugins {
        create("kastleSettings") {
            id = "org.jetbrains.kastle"
            displayName = "Kastle Gradle Plugin"
            implementationClass = "org.jetbrains.kastle.KastleGradlePlugin"
            description = "Configures all submodules in a KASTLE repository"
            tags = setOf("kotlin", "multiplatform")
        }
    }
}
