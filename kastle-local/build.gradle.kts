import org.gradle.internal.classpath.Instrumented.systemProperty

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.testBalloon)
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.io)
    implementation(libs.ktoml)

    compileOnly(libs.kotlin.compiler)

    testImplementation(libs.kotlin.compiler)
    testImplementation(project(":kastle-templates"))
    testImplementation(project(":kastle-test"))
    testImplementation(kotlin("test"))
}

tasks {
    val updateSnapshots by registering {
        description = "Run tests and update snapshots"
        group = "verification"

        doFirst {
            systemProperty("UPDATE_GENERATOR_SNAPSHOTS", "true")
        }

        finalizedBy("test")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
