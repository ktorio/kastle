
plugins {
    alias(libs.plugins.kotlin.jvm)

    alias(libs.plugins.jetbrains.compose)

    alias(libs.plugins.compose.compiler)

}



dependencies {

    implementation(project(":shared"))




    implementation(compose.desktop.currentOs)




}
