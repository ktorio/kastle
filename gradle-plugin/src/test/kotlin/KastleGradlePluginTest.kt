package org.jetbrains.kastle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KastleGradlePluginTest {

    private lateinit var testProjectDir: File

    @BeforeTest
    fun setup() {
        testProjectDir = Files.createTempDirectory("test").toFile()
    }

    @Test
    fun `plugin registers extension and task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()
        project.plugins.apply("org.jetbrains.kastle")

        // Verify the extension exists
        val extension = project.extensions.findByType(KastleExtension::class.java)
        assertTrue(extension != null, "Plugin should register a 'kastle' extension")

        // Verify the task exists
        val task = project.tasks.findByName("buildKastle")
        assertTrue(task != null, "Plugin should register a 'buildKastle' task")
    }

    @Test
    fun `extension correctly configures plugin properties`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()
        project.plugins.apply("org.jetbrains.kastle")

        // Create test directories and files
        val versionsCatalogFile = File(testProjectDir, "custom-versions.toml").apply { createNewFile() }
        val repoDir = File(testProjectDir, "test-repo").apply { mkdirs() }
        val outputDir = File(testProjectDir, "test-output").apply { mkdirs() }

        // Configure the extension
        val extension = project.extensions.getByType(KastleExtension::class.java)
        extension.versionsCatalog.set(versionsCatalogFile)
        extension.repositoryPath.set(repoDir)
        extension.outputPath.set(outputDir)

        // Verify the configuration values are set correctly
        assertEquals(
            versionsCatalogFile.absolutePath,
            extension.versionsCatalog.get().asFile.absolutePath,
            "versionsCatalog should be set correctly"
        )
        assertEquals(
            repoDir.absolutePath,
            extension.repositoryPath.get().asFile.absolutePath,
            "repositoryPath should be set correctly"
        )
        assertEquals(
            outputDir.absolutePath,
            extension.outputPath.get().asFile.absolutePath,
            "outputPath should be set correctly"
        )
    }
}