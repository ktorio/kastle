plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotest)
    `maven-publish`
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    api(project(":kastle-core"))
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.io.core)

    implementation(libs.kaml)
    implementation(libs.kotlin.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.io)
    implementation(libs.ktoml)

    testImplementation(project(":kastle-templates"))
    testImplementation(project(":kastle-test"))
    testImplementation(libs.kotest.junit5)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
