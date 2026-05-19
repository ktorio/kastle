val addSampleCode: String by _properties
if (addSampleCode) {plugins {
        id("org.jetbrains.kotlin.plugin.compose")
    }
}
dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        if (addSampleCode) {

            composeUI()
        }
        if (_slots.contains("frontendBuildScriptDependencies")) {

            _slots("frontendBuildScriptDependencies")
        }
    }

    implementation(project(":shared"))
}
