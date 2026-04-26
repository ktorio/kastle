import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}


dependencies {
    implementation(project(":sharedUI"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    testImplementation(kotlin("test"))
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
