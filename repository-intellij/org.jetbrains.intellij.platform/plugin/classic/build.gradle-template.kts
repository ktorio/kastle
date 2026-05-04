import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    for (plugin in _project.gradle.plugins) {
    id(plugin.id)
    }
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        val platformVersion: String by _properties
        intellijIdea(platformVersion)
        testFramework(TestFrameworkType.Platform)

        if (_slots.contains("buildScriptDependencies")) {
            // Add plugin dependencies for compilation here:
            _slots("buildScriptDependencies")
        } else {
            // Add plugin dependencies for compilation here, for example:
            // bundledPlugin("com.intellij.java")
        }
    }
}
