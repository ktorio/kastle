dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")
        if (_slots.contains("backendBuildScriptDependencies")) {

            _slots("backendBuildScriptDependencies")
        }
    }

    implementation(project(":shared"))
}
