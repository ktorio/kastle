plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)

        composeUI()

        bundledPlugin("org.jetbrains.kotlin")
        bundledModule("intellij.javascript.frontend")
        bundledModule("intellij.markdown.frontend")
        bundledModule("intellij.database.sql.frontend.core")
        bundledModule("intellij.database.sql.frontend.impl")
    }

    implementation(project(":shared"))
}
