plugins {
    id("java")
    for (item in _module.gradle.plugins) {
        alias(_unsafe("libs.plugins.${item}"))
    }
}

group = _project.group
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        if (_slots.contains("buildScriptDependencies")) {
            // Add plugin dependencies for compilation here:
            _slots("buildScriptDependencies")
        } else {
            // Add plugin dependencies for compilation here, for example:
            // bundledPlugin("com.intellij.java")
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
