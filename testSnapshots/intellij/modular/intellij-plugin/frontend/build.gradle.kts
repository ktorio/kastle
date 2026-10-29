dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
    }

    implementation(project(":shared"))
}
