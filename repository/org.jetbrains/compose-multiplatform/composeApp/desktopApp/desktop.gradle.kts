import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * slot://org.gradle/gradle/buildRoot
 */
fun gradleConfig() {
    compose.desktop {
        application {
            mainClass = "${_project.group}.MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = _project.group
                packageVersion = "1.0.0"
            }
        }
    }
}