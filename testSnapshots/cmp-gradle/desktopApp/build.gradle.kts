plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
}


dependencies {
    debugImplementation(compose.uiTooling)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}