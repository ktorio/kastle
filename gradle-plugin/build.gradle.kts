plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pluginPublish)
}

group = libs.plugins.kastle.get().pluginId
version = libs.plugins.kastle.get().version

dependencies {
    implementation(gradleApi())
    compileOnly(libs.gradlePlugin.kotlin)
    implementation(project(":core"))
    implementation(project(":local"))
    implementation(libs.kotlin.compiler)

    testImplementation(libs.gradlePlugin.kotlin)
    testImplementation(libs.kotlin.test)
}

gradlePlugin {
    website = "https://jetbrains.org"
    vcsUrl = "https://github.com/ktorio/kastle"

    plugins {
        create("kastle") {
            id = "org.jetbrains.kastle"
            displayName = "Kastle Gradle Plugin"
            implementationClass = "org.jetbrains.kastle.KastleGradlePlugin"
            description = "Allows export and publishing of custom KASTLE repositories"
            tags = setOf("kotlin")
        }
    }
}

val setupPluginUploadFromEnvironment = tasks.register("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("GRADLE_PUBLISH_KEY and/or GRADLE_PUBLISH_SECRET are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}

tasks.named("publishPlugins") {
    dependsOn("test", setupPluginUploadFromEnvironment)
}