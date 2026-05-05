dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")

        bundledModule("intellij.java.frontback.psi")
        bundledModule("intellij.java.frontback.impl")
        bundledPlugin("org.jetbrains.kotlin")
        bundledModule("intellij.javascript.common")
        bundledModule("intellij.json")
        bundledModule("intellij.yaml")
        bundledModule("intellij.xml.psi")
        bundledModule("intellij.xml.psi.impl")
        bundledModule("intellij.properties")
        bundledModule("intellij.properties.psi")
        bundledModule("intellij.markdown")
        bundledModule("intellij.database.sql")
        bundledModule("intellij.database.sql.core.impl")
    }

    implementation(project(":shared"))
}
