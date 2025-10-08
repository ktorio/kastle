package org.jetbrains.kastle.templates

import io.kotest.core.spec.style.StringSpec
import kotlinx.io.files.Path
import org.jetbrains.kastle.io.resolve

class KotlinCompilerTemplateEngineTest : StringSpec({

    "gradle build does not fail" {
        val file = Path("../repository/org.gradle/gradle")
        val engine = KotlinCompilerTemplateEngine(file.parent!!)
        val ktFile = engine.ktFiles.single { it.name == "build.gradle.kts" }
        engine.read(
            file.resolve("build.gradle.kts"),
            ktFile,
            mutableListOf()
        )
    }

})