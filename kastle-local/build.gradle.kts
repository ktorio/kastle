import io.kotest.framework.gradle.tasks.KotestJvmTask
import org.gradle.internal.classpath.Instrumented.systemProperty

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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.io)
    implementation(libs.ktoml)

    compileOnly(libs.kotlin.compiler)

    testImplementation(libs.kotlin.compiler)
    testImplementation(project(":kastle-templates"))
    testImplementation(project(":kastle-test"))
    testImplementation(libs.kotest.junit5)
}

tasks {
    test {
        useJUnitPlatform()
    }

    val updateSnapshots by registering {
        description = "Run tests and update snapshots"
        group = "verification"

        doFirst {
            // Set system property that will be inherited by the jvmKotest task
            System.setProperty("UPDATE_GENERATOR_SNAPSHOTS", "true")
        }
        doFirst {
            // Configure the test task to pass the property
            named<KotestJvmTask>("jvmKotest").configure {
                systemProperty("UPDATE_GENERATOR_SNAPSHOTS", "true")
            }
        }

        finalizedBy("jvmKotest")
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            artifact(sourcesJar)
        }
    }
}
