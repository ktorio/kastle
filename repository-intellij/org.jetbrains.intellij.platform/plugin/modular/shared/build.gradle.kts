plugins {
    id("rpc")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellij.platform)
        _slots("sharedBuildScriptDependencies")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
