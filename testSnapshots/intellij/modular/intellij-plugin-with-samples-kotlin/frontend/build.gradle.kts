plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)

        composeUI()

        bundledPlugin("org.jetbrains.kotlin")
    }

    implementation(project(":shared"))
}
