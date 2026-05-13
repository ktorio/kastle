package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.export
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val INTELLIJ_DEFAULT_GROUP = "com.acme"

private val intellijTestScope = CoroutineScope(CoroutineName("intellij-generator-test"))

private enum class PluginLayout(val snapshotSubDir: String, val extraPacks: List<String>) {
    Classic("intellij/classic", emptyList()),
    Modular("intellij/modular", listOf("org.jetbrains.intellij.platform.architecture/modular")),
}

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
        snapshotSubDir: String,
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
            "$snapshots/$snapshotSubDir/$snapshotName",
            outputDir.toString(),
        )
    }

    suspend fun testPlugin(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin",
            layout.snapshotSubDir,
            listOf("org.jetbrains.intellij.platform/plugin") + layout.extraPacks,
        )
    }

    suspend fun testPluginWithSamples(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-with-samples",
            layout.snapshotSubDir,
            listOf("org.jetbrains.intellij.platform/plugin") + layout.extraPacks,
            properties = mapOf(
                VariableId.parse("org.jetbrains.intellij.platform/plugin/addSampleCode") to "true",
            ),
        )
    }

    suspend fun testPluginCompose(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-compose",
            layout.snapshotSubDir,
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.dependencies/compose",
            ) + layout.extraPacks,
        )
    }

    suspend fun testPluginComposeWithSamples(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-compose-with-samples",
            layout.snapshotSubDir,
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.dependencies/compose",
            ) + layout.extraPacks,
            properties = mapOf(
                VariableId.parse("org.jetbrains.intellij.platform/plugin/addSampleCode") to "true",
            ),
        )
    }

    suspend fun testPluginKotlinWithSamples(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-kotlin-with-samples",
            layout.snapshotSubDir,
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.plugins/kotlin",
            ) + layout.extraPacks,
            properties = mapOf(
                VariableId.parse("org.jetbrains.intellij.platform/plugin/addSampleCode") to "true",
            ),
        )
    }

    suspend fun testPluginJavaKotlin(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-java-kotlin",
            layout.snapshotSubDir,
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.plugins/java",
                "org.jetbrains.intellij.platform.plugins/kotlin",
            ) + layout.extraPacks,
        )
    }

    suspend fun testPluginLsp(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-lsp",
            layout.snapshotSubDir,
            listOf(
                "org.jetbrains.intellij.platform/plugin",
                "org.jetbrains.intellij.platform.dependencies/lsp",
            ) + layout.extraPacks,
        )
    }

    suspend fun testPluginAllPacksEnabled(layout: PluginLayout) {
        generateAndValidateSnapshot(
            "intellij-plugin-all-packs-enabled",
            layout.snapshotSubDir,
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
                "org.jetbrains.intellij.platform.dependencies/lsp",
                "org.jetbrains.intellij.platform.vcs/git",
            ) + layout.extraPacks,
        )
    }

    "intellij-plugin (classic)" { testPlugin(PluginLayout.Classic) }
    "intellij-plugin (modular)" { testPlugin(PluginLayout.Modular) }

    "intellij-plugin-with-samples (classic)" { testPluginWithSamples(PluginLayout.Classic) }
    "intellij-plugin-with-samples (modular)" { testPluginWithSamples(PluginLayout.Modular) }

    "intellij-plugin-compose (classic)" { testPluginCompose(PluginLayout.Classic) }
    "intellij-plugin-compose (modular)" { testPluginCompose(PluginLayout.Modular) }

    "intellij-plugin-compose-with-samples (classic)" { testPluginComposeWithSamples(PluginLayout.Classic) }
    "intellij-plugin-compose-with-samples (modular)" { testPluginComposeWithSamples(PluginLayout.Modular) }

    "intellij-plugin-kotlin-with-samples (classic)" { testPluginKotlinWithSamples(PluginLayout.Classic) }
    "intellij-plugin-kotlin-with-samples (modular)" { testPluginKotlinWithSamples(PluginLayout.Modular) }

    "intellij-plugin-java-kotlin (classic)" { testPluginJavaKotlin(PluginLayout.Classic) }
    "intellij-plugin-java-kotlin (modular)" { testPluginJavaKotlin(PluginLayout.Modular) }

    "intellij-plugin-lsp (classic)" { testPluginLsp(PluginLayout.Classic) }
    "intellij-plugin-lsp (modular)" { testPluginLsp(PluginLayout.Modular) }

    "intellij-plugin-all-packs-enabled (classic)" { testPluginAllPacksEnabled(PluginLayout.Classic) }
    "intellij-plugin-all-packs-enabled (modular)" { testPluginAllPacksEnabled(PluginLayout.Modular) }

}
