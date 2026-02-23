val fus_version: String by project

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":kastle-core"))
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("com.typesafe:config:1.4.6")
    implementation("com.jetbrains.fus.reporting:api:${fus_version}")
    implementation("com.jetbrains.fus.reporting:ap-validation-all:${fus_version}")
    implementation("com.jetbrains.fus.reporting:serialization-kotlin:${fus_version}")
    implementation("com.jetbrains.fus.reporting:scheme:${fus_version}")
}
