dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")

        bundledPlugin("org.jetbrains.kotlin")
    }

    implementation(project(":shared"))
}
