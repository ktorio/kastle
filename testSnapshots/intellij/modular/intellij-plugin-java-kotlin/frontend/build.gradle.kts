
dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)

        bundledPlugin("org.jetbrains.kotlin")
    }

    implementation(project(":shared"))
}
