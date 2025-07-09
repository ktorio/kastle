plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core"))
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.io.core)

    implementation(libs.kaml)
    implementation(libs.kotlin.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.io)
    implementation(libs.ktoml)

    testImplementation(project(":templates"))
    testImplementation(project(":test", configuration = "commonTest"))
}