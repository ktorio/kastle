package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.export
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DEFAULT_NAME = "sample"
private const val DEFAULT_GROUP = "com.acme"

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

    @OptIn(ExperimentalTime::class)
    fun randomString() =
        Random(Clock.System.now().toEpochMilliseconds()).nextLong(111, 999).toString(36)

    suspend fun generate(
        outputDir: Path,
        name: String,
        properties: Map<VariableId, String> = emptyMap(),
        packs: List<String>
    ) {
        deleteRecursively(outputDir, SystemFileSystem)

        ProjectGenerator(
            repository = repository.await(),
        ).generate(
            ProjectDescriptor(
                name = name,
                group = DEFAULT_GROUP,
                properties = properties,
                packs = packs.map(PackId.Companion::parse),
            )
        ).export(outputDir)
    }

    suspend fun generateWithPacks(outputDir: Path, name: String, vararg packs: String) =
        generate(outputDir, name, packs = packs.toList())


    suspend fun generateAndValidateSnapshot(
        snapshotName: String,
        packs: List<String>,
    ) {
        val outputDir = Path(SystemTemporaryDirectory, "generated", snapshotName, randomString())
        generate(
            outputDir,
            snapshotName,
            packs = packs
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/$snapshotName",
            outputDir.toString(),
        )
    }

    "empty project" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "empty", randomString())
        generateWithPacks(outputDir, "empty", "com.acme/empty")
        assertFilesAreEqualWithSnapshot( "$snapshots/empty", outputDir.toString())
    }

    "with slot" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child", randomString())
        generateWithPacks(
            outputDir,
            "parent-child",
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
            "parent-child2",
            "com.acme/parent",
            "com.acme/child",
            "com.acme/child2",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/parent-child2",
            outputDir.toString(),
        )
    }

    "with repeating slot and texts sorted by priority then text" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "sorting-slot-values-consumer", randomString())
        generateWithPacks(
            outputDir,
            "sorting-slot-values-consumer",
            "com.acme/sorting-slot-values-consumer",
            "com.acme/sorting-slot-values-test1",
            "com.acme/sorting-slot-values-test2",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/sorting-slot-values-consumer",
            outputDir.toString(),
        )
    }

    "with properties" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "properties", randomString())
        generate(outputDir, "properties", packs = listOf("com.acme/properties"), properties = mapOf(
            "numberProperty" to "1",
            "booleanProperty" to "true",
            "nullProperty" to "null",
            "collection" to "1,2,3",
            "whenProperty" to "yes",
            "literal" to "literal",
        ).mapKeys { (key) ->
            VariableId.parse(key, PackId.parse("com.acme/properties"))
        })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/properties",
            outputDir.toString(),
        )
    }

    "value expressions" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "value-expressions", randomString())
        generate(outputDir, "value-expressions", packs = listOf("com.acme/value-expressions"), properties = mapOf(
            "booleanProperty" to "true",
            "integerProperty" to "40",
            "stringProperty" to "test",
            "listProperty" to "item1,item2,item3",
        ).mapKeys { (key) -> VariableId.parse("com.acme/value-expressions/$key") })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/value-expressions",
            outputDir.toString(),
        )
    }

    "target expressions" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "target-expressions", randomString())
        generate(outputDir, "target-expressions", packs = listOf("com.acme/target-expressions"), properties = mapOf(
            "folderName" to "my-folder",
        ).mapKeys { (key) -> VariableId.parse("com.acme/target-expressions/$key") })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/target-expressions",
            outputDir.toString(),
        )
    }

    "conditions" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "conditions", randomString())
        generate(outputDir, "target-expressions", packs = listOf("com.acme/conditions"), properties = mapOf(
            "myFlag" to "true",
        ).mapKeys { (key) -> VariableId.parse("com.acme/conditions/$key") })
        assertFilesAreEqualWithSnapshot(
            "$snapshots/conditions",
            outputDir.toString(),
        )
    }

    "ktor server" {
        generateAndValidateSnapshot(
            "ktor-server",
            listOf(
                "org.gradle/gradle",
                "io.ktor/server-core",
                "io.ktor/server-netty",
                "io.ktor/server-content-negotiation",
                "io.ktor/server-kotlinx-serialization",
            )
        )
    }

    "ktor server amper" {
        generateAndValidateSnapshot(
            "ktor-server-amper",
            listOf(
                "org.jetbrains/amper",
                "io.ktor/server-core",
                "io.ktor/server-netty",
                "io.ktor/server-content-negotiation",
                "io.ktor/server-kotlinx-serialization",
            )
        )
    }

    "ktor server maven" {
        generateAndValidateSnapshot(
            "ktor-server-maven",
            listOf(
                "org.apache/maven",
                "io.ktor/server-core",
                "io.ktor/server-netty",
                "io.ktor/server-content-negotiation",
                "io.ktor/server-kotlinx-serialization",
            )
        )
    }

    "ktor server htmx" {
        generateAndValidateSnapshot(
            "ktor-server-htmx",
            listOf(
                "org.gradle/gradle",
                "io.ktor/server-core",
                "io.ktor/server-netty",
                "io.ktor/server-htmx",
            )
        )
    }

    "compose multiplatform gradle" {
        val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-gradle", randomString())
        generate(
            outputDir,
            "cmp-gradle",
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
            "cmp-amper",
            "org.jetbrains/amper",
            "org.jetbrains/compose-multiplatform",
        )
        assertFilesAreEqualWithSnapshot(
            "$snapshots/cmp-amper",
            outputDir.toString(),
        )
    }

    afterContainer {
        tearDown()
    }
}
