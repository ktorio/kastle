plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
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
    testImplementation(project(":test"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.junit5)
}

tasks {
    test {
        useJUnitPlatform()
    }

    register<Test>("updateSnapshots") {
        group = "verification"
        useJUnitPlatform()
        environment("UPDATE_GENERATOR_SNAPSHOTS", "true")
        include("**/*ProjectGeneratorTest.class")
    }
}
