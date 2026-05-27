
dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)

        bundledModule("intellij.database.sql.frontend.core")
    }

    implementation(project(":shared"))
}
