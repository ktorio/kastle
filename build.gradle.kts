plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

subprojects {
    group = "com.jetbrains.kastle"
    version = "1.0.0-SNAPSHOT"

    plugins.withId("maven-publish") {
        afterEvaluate {
            configure<PublishingExtension> {
                publications.withType<MavenPublication> {
                    pom {
                        artifactId = "kastle-${project.name}"
                        group = "org.jetbrains.kastle"
                        version = "1.0.0-SNAPSHOT"
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