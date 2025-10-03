package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.gen.ProjectResolver
import org.jetbrains.kastle.gen.plus
import org.jetbrains.kastle.gradle.GradleTransformation
import org.jetbrains.kastle.io.export
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.LogLevel
import kotlin.random.Random

private const val DEFAULT_NAME = "sample"
private const val DEFAULT_GROUP = "com.acme"
internal val REPLACE_SNAPSHOTS = true // System.getenv("UPDATE_GENERATOR_SNAPSHOTS") != null

private val testScope = CoroutineScope(CoroutineName("generator-test"))

fun ProjectGeneratorTest(
    tearDown: suspend () -> Unit = {},
    createRepository: suspend () -> PackRepository,
) : StringSpec.() -> Unit = {
    val snapshots = Path("../testSnapshots")
    val repository: Deferred<PackRepository> =
        testScope.async(start = CoroutineStart.LAZY) {
            createRepository()
        }

    fun randomString() =
        Random(System.currentTimeMillis()).nextLong(111, 999).toString(36)

    suspend fun generate(
        outputDir: Path,
        properties: Map<VariableId, String> = emptyMap(),
        packs: List<String>
    ) = ProjectGeneratorImpl(
        repository = repository.await(),
        projectResolver = ProjectResolver.Default + GradleTransformation,
        log = ConsoleLogger(LogLevel.TRACE),
    ).generate(
        ProjectDescriptor(
            name = DEFAULT_NAME,
            group = DEFAULT_GROUP,
            properties = properties,
            packs = packs.map(PackId.Companion::parse),
        )
    ).export(outputDir)

    suspend fun generateWithPacks(outputDir: Path, vararg packs: String) =
        generate(outputDir, packs = packs.toList())

    "empty project" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "empty", randomString())
        generateWithPacks(outputDir, "com.acme/empty")
        assertFilesAreEqualWithSnapshot( "$snapshots/empty", outputDir.toString())
    }

    "with slot" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child", randomString())
        generateWithPacks(
            outputDir,
            "com.acme/parent",
            "com.acme/child",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child",
            outputDir.toString(),
        )
    }

    "with slot and two children" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child2", randomString())
        generateWithPacks(
            outputDir,
            "com.acme/parent",
            "com.acme/child",
            "com.acme/child2",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child2",
            outputDir.toString(),
        )
    }

    "with properties" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "properties", randomString())
        generate(outputDir, packs = listOf("com.acme/properties"), properties = mapOf(
            "numberProperty" to "1",
            "booleanProperty" to "true",
            "nullProperty" to "null",
            "collection" to "1,2,3",
            "whenProperty" to "yes",
            "literal" to "literal",
        ).mapKeys { (key) -> VariableId.Companion.parse("com.acme/properties/$key") })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/properties",
            outputDir.toString(),
        )
    }

    "ktor server gradle" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "ktor-server", randomString())
        generateWithPacks(
            outputDir,
            "org.gradle/gradle",
            "io.ktor/server-core",
            "io.ktor/server-cio",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server",
            outputDir.toString(),
        )
    }

    "ktor server gradle with catalog" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "ktor-server-catalog", randomString())
        generate(
            outputDir,
            packs = listOf(
                "org.gradle/gradle",
                "io.ktor/server-core",
                "io.ktor/server-cio",
                "io.ktor/server-content-negotiation",
                "io.ktor/kotlinx-serialization-json",
            ),
            properties = mapOf(
                VariableId.Companion.parse("org.gradle/gradle/versionCatalogEnabled") to "true",
            )
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server-catalog",
            outputDir.toString(),
        )
    }

    "ktor server amper" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "ktor-server-amper", randomString())
        generateWithPacks(
            outputDir,
            "org.jetbrains/amper",
            "io.ktor/server-core",
            "io.ktor/server-cio",
            "io.ktor/server-content-negotiation",
            "io.ktor/kotlinx-serialization-json",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/ktor-server-amper",
            outputDir.toString(),
        )
    }

    "compose multiplatform gradle" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-gradle", randomString())
        generate(
            outputDir,
            packs = listOf(
                "org.gradle/gradle",
                "org.jetbrains/compose-multiplatform",
            ),
            properties = mapOf(
                VariableId.parse("org.gradle/gradle/versionCatalogEnabled") to "true",
            )
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/cmp-gradle",
            outputDir.toString(),
        )
    }

    "compose multiplatform amper" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-amper", randomString())
        generateWithPacks(
            outputDir,
            "org.jetbrains/amper",
            "org.jetbrains/compose-multiplatform",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/cmp-amper",
            outputDir.toString(),
        )
    }

    afterSpec {
        tearDown()
    }
}