package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.export
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val INTELLIJ_DEFAULT_NAME = "sample"
private const val INTELLIJ_DEFAULT_GROUP = "com.acme"

private val intellijTestScope = CoroutineScope(CoroutineName("intellij-generator-test"))

fun IntellijProjectGeneratorTest(
    createRepository: suspend () -> PackRepository,
) : StringSpec.() -> Unit = {
    val snapshots = Path("../testSnapshots")
    val repository: Deferred<PackRepository> =
        intellijTestScope.async(start = CoroutineStart.LAZY) {
            createRepository()
        }

    @OptIn(ExperimentalTime::class)
    fun randomString() =
        Random(Clock.System.now().toEpochMilliseconds()).nextLong(111, 999).toString(36)

    suspend fun generateAndValidateSnapshot(
        snapshotName: String,
        packs: List<String>,
        properties: Map<VariableId, String> = emptyMap(),
    ) {
        val outputDir = Path(SystemTemporaryDirectory, "generated", snapshotName, randomString())
        deleteRecursively(outputDir)

        ProjectGenerator(
            repository = repository.await(),
        ).generate(
            ProjectDescriptor(
                name = snapshotName,
                group = INTELLIJ_DEFAULT_GROUP,
                properties = properties,
                packs = packs.map(PackId.Companion::parse),
            )
        ).export(outputDir)

        assertFilesAreEqualWithSnapshot(
            "$snapshots/$snapshotName",
            outputDir.toString(),
        )
    }

    "intellij-plugin" {
        generateAndValidateSnapshot(
            "intellij-plugin",
            listOf("org.jetbrains.intellij.platform/plugin"),
        )
    }

    "intellij-plugin-with-samples" {
        generateAndValidateSnapshot(
            "intellij-plugin-with-samples",
            listOf("org.jetbrains.intellij.platform/plugin"),
            properties = mapOf(
                VariableId.parse("org.jetbrains.intellij.platform/plugin/addSampleCode") to "true",
            ),
        )
    }

    "intellij-plugin-compose" {
        generateAndValidateSnapshot(
            "intellij-plugin-compose",
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.dependencies/compose",
            ),
        )
    }

    "intellij-plugin-compose-with-samples" {
        generateAndValidateSnapshot(
            "intellij-plugin-compose-with-samples",
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.dependencies/compose",
            ),
            properties = mapOf(
                VariableId.parse("org.jetbrains.intellij.platform/plugin/addSampleCode") to "true",
            ),
        )
    }

    "intellij-plugin-java-kotlin" {
        generateAndValidateSnapshot(
            "intellij-plugin-java-kotlin",
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.plugins/java",
                "org.jetbrains.intellij.platform.plugins/kotlin",
            ),
        )
    }

    "intellij-plugin-lsp" {
        generateAndValidateSnapshot(
            "intellij-plugin-lsp",
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.dependencies/lsp",
            ),
        )
    }

    "intellij-plugin-all-packs-enabled" {
        generateAndValidateSnapshot(
            "intellij-plugin-all-packs-enabled",
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.plugins/java",
                "org.jetbrains.intellij.platform.plugins/kotlin",
                "org.jetbrains.intellij.platform.plugins/javascript",
                "org.jetbrains.intellij.platform.plugins/json",
                "org.jetbrains.intellij.platform.plugins/yaml",
                "org.jetbrains.intellij.platform.plugins/xml",
                "org.jetbrains.intellij.platform.plugins/properties",
                "org.jetbrains.intellij.platform.plugins/markdown",
                "org.jetbrains.intellij.platform.plugins/database",
                "org.jetbrains.intellij.platform.dependencies/compose",
                "org.jetbrains.intellij.platform.dependencies/lsp",
                "org.jetbrains.intellij.platform.vcs/git",
            ),
        )
    }

    "intellij-theme" {
        generateAndValidateSnapshot(
            "intellij-theme",
            listOf("org.jetbrains.intellij.platform/theme"),
        )
    }
}
