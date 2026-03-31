import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}


compose.desktop {
    application {
        mainClass = "com.acme.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.acme"
            packageVersion = "1.0.0"
        }
    }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(composeLibs.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    testImplementation(kotlin("test"))
}
