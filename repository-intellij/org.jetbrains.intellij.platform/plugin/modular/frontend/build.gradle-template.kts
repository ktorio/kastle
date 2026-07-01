dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
        if (_slots.contains("frontendBuildScriptDependencies")) {

            _slots("frontendBuildScriptDependencies")
        }
    }

    implementation(project(":shared"))
}
