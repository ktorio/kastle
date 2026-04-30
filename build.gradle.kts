
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

val refType: String? = System.getenv("GITHUB_REF_TYPE")
val refName: String? = System.getenv("GITHUB_REF_NAME")

group = "org.jetbrains"
version = when {
    refType == "tag" && refName?.startsWith("release-") == true -> {
        refName.removePrefix("release-").also {
            println("Building release $it")
        }
    }
    else -> "1.0.0-SNAPSHOT"
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    // kotest problems
    tasks.withType<Test> {
        failOnNoDiscoveredTests = false
    }

    plugins.withId("maven-publish") {
        afterEvaluate {
            configure<PublishingExtension> {
                repositories {
                    maven {
                        url = uri("https://packages.jetbrains.team/maven/p/kastle/maven")
                        credentials {
                            username = System.getenv("SPACE_USERNAME")
                            password = System.getenv("SPACE_PASSWORD")
                        }
                    }

                }
                publications.withType<MavenPublication> {
                    pom {
                        url = "https://github.com/ktorio/kastle"

                        licenses {
                            license {
                                name = "The Apache Software License, Version 2.0"
                                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                                distribution = "repo"
                            }
                        }
                        developers {
                            developer {
                                id = "JetBrains"
                                name = "Jetbrains Team"
                                organization = "JetBrains"
                                organizationUrl = "https://www.jetbrains.com"
                            }
                        }
                        scm {
                            url = "https://github.com/ktorio/kastle.git"
                        }
                    }
                }
            }
        }
    }
}
