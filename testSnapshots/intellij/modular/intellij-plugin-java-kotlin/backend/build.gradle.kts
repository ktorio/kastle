dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")

        bundledModule("intellij.java.frontback.psi")
        bundledModule("intellij.java.frontback.impl")
        bundledPlugin("org.jetbrains.kotlin")
    }

    implementation(project(":shared"))
}
