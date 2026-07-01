dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledModule("JavaScript")
        bundledModule("com.intellij.modules.json")
        bundledModule("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.xml")
        bundledModule("com.intellij.properties")
        bundledModule("org.intellij.plugins.markdown")
        bundledModule("intellij.database.sql")
        bundledModule("intellij.database.sql.core.impl")
    }

    implementation(project(":shared"))
}
