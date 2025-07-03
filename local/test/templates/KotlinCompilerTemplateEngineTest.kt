package org.jetbrains.kastle.templates

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import org.jetbrains.kastle.io.resolve
import kotlin.test.Test

class KotlinCompilerTemplateEngineTest {

    @Test
    fun testGradleBuild() = runTest {
        val file = Path("../repository/org.gradle/gradle")
        val engine = KotlinCompilerTemplateEngine(file.parent!!)
        val ktFile = engine.ktFiles.single { it.name == "build.gradle.kts" }
        val template = engine.read(
            file.resolve("build.gradle.kts"),
            ktFile,
            mutableListOf()
        )
    }

}