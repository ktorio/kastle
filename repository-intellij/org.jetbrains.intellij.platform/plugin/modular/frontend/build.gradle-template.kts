plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)

        composeUI()
        if (_slots.contains("frontendBuildScriptDependencies")) {

            _slots("frontendBuildScriptDependencies")
        }
    }

    implementation(project(":shared"))
}
