package org.jetbrains.kastle

import de.infix.testBalloon.framework.core.TestSuite
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.kastle.io.export
import org.jetbrains.kastle.logging.ConsoleLogger
import org.jetbrains.kastle.logging.LogLevel
import kotlin.random.Random
import kotlin.time.Clock

private const val DEFAULT_GROUP = "com.acme"

private val snapshots = Path("../testSnapshots")

fun TestSuite.testProjectGenerator(
    tearDown: suspend () -> Unit = {},
    createRepository: suspend () -> PackRepository,
) {
    testFixture {
        ProjectGenerator(
            createRepository(),
            log = ConsoleLogger(LogLevel.INFO)
        )
    } closeWith {
        tearDown()
    } asParameterForEach {

        test("empty project") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "empty", randomString())
            gen.generateWithPacks(outputDir, "empty", "com.acme/empty")
            assertFilesAreEqualWithSnapshot("$snapshots/empty", outputDir.toString())
        }

        test("with slot") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child", randomString())
            gen.generateWithPacks(
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

        test("with slot and two children") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "parent-child2", randomString())
            gen.generateWithPacks(
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

        test("with repeating slot and texts sorted by priority then text") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "sorting-slot-values-consumer", randomString())
            gen.generateWithPacks(
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

        test("with properties") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "properties", randomString())
            gen.generate(
                outputDir, "properties", packs = listOf("com.acme/properties"), properties = mapOf(
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

        test("value expressions") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "value-expressions", randomString())
            val expressionPackProperties = mapOf(
                "booleanProperty" to "true",
                "integerProperty" to "40",
                "stringProperty" to "test",
                "listProperty" to "item1,item2,item3",
            ).mapKeys { (key) ->
                VariableId.parse("com.acme/value-expressions/$key")
            }
            val basicPackProperties = mapOf(
                "booleanProperty" to "false",
            ).mapKeys { (key) ->
                VariableId.parse("com.acme/properties/$key")
            }
            gen.generate(
                outputDir, "value-expressions",
                packs = listOf(
                    "com.acme/value-expressions",
                    "com.acme/properties",
                ),
                properties = expressionPackProperties + basicPackProperties
            )
            assertFilesAreEqualWithSnapshot(
                "$snapshots/value-expressions",
                outputDir.toString(),
            )
        }

        test("wildcard sources with explicit target directory") { gen ->
            gen.generateAndValidateSnapshot(
                "wildcard-target",
                listOf("com.acme/wildcard-target"),
            )
        }

        test("target expressions") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "target-expressions", randomString())
            gen.generate(
                outputDir, "target-expressions", packs = listOf("com.acme/target-expressions"), properties = mapOf(
                    "folderName" to "my-folder",
                ).mapKeys { (key) -> VariableId.parse("com.acme/target-expressions/$key") })
            assertFilesAreEqualWithSnapshot(
                "$snapshots/target-expressions",
                outputDir.toString(),
            )
        }

        test("conditions") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "conditions", randomString())
            gen.generate(
                outputDir, "target-expressions", packs = listOf("com.acme/conditions"), properties = mapOf(
                    "myFlag" to "true",
                ).mapKeys { (key) -> VariableId.parse("com.acme/conditions/$key") })
            assertFilesAreEqualWithSnapshot(
                "$snapshots/conditions",
                outputDir.toString(),
            )
        }

        test("module if - both conditions false") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-excluded", randomString())
            gen.generate(
                outputDir, "module-if-excluded", packs = listOf("com.acme/module-if"), properties = mapOf(
                    "includeModule" to "false",
                    "includeDefault" to "false",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") })
            assertFilesAreEqualWithSnapshot(
                "$snapshots/module-if-excluded",
                outputDir.toString()
            )
        }

        test("module if - user property condition true") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-optional", randomString())
            gen.generate(
                outputDir, "module-if-optional", packs = listOf("com.acme/module-if"), properties = mapOf(
                    "includeModule" to "true",
                    "includeDefault" to "false",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") })
            assertFilesAreEqualWithSnapshot(
                "$snapshots/module-if-optional",
                outputDir.toString()
            )
        }

        test("module if - default-value property condition true") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-default", randomString())
            gen.generate(
                outputDir, "module-if-default", packs = listOf("com.acme/module-if"), properties = mapOf(
                    "includeModule" to "false",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") })
            assertFilesAreEqualWithSnapshot(
                "$snapshots/module-if-default",
                outputDir.toString()
            )
        }

        test("module if - both conditions true") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-both", randomString())
            gen.generate(
                outputDir, "module-if-both", packs = listOf("com.acme/module-if"), properties = mapOf(
                    "includeModule" to "true",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") })
            assertFilesAreEqualWithSnapshot(
                "$snapshots/module-if-both",
                outputDir.toString()
            )
        }

        test("module if - with gradle: both optional modules excluded, no deps in catalog") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-dep-excluded", randomString())
            gen.generate(
                outputDir, "module-if-dep-excluded",
                packs = listOf("com.acme/module-if", "org.gradle/gradle"),
                properties = mapOf(
                    "includeModule" to "false",
                    "includeDefault" to "false",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") }
            )
            assertFilesAreEqualWithSnapshot("$snapshots/module-if-dep-excluded", outputDir.toString())
        }

        test("module if - with gradle: optional module included, its deps in catalog") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-dep-optional", randomString())
            gen.generate(
                outputDir, "module-if-dep-optional",
                packs = listOf("com.acme/module-if", "org.gradle/gradle"),
                properties = mapOf(
                    "includeModule" to "true",
                    "includeDefault" to "false",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") }
            )
            assertFilesAreEqualWithSnapshot("$snapshots/module-if-dep-optional", outputDir.toString())
        }

        test("module if - with gradle: both modules included, all deps in catalog") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "module-if-dep-both", randomString())
            gen.generate(
                outputDir, "module-if-dep-both",
                packs = listOf("com.acme/module-if", "org.gradle/gradle"),
                properties = mapOf(
                    "includeModule" to "true",
                ).mapKeys { (key) -> VariableId.parse("com.acme/module-if/$key") }
            )
            assertFilesAreEqualWithSnapshot("$snapshots/module-if-dep-both", outputDir.toString())
        }

        test("ktor server") { gen ->
            gen.generateAndValidateSnapshot(
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

        test("ktor server kotlin toolchain") { gen ->
            gen.generateAndValidateSnapshot(
                "ktor-server-toolchain",
                listOf(
                    "org.jetbrains/kotlin-toolchain",
                    "io.ktor/server-core",
                    "io.ktor/server-netty",
                    "io.ktor/server-content-negotiation",
                    "io.ktor/server-kotlinx-serialization",
                )
            )
        }

        test("ktor server maven") { gen ->
            gen.generateAndValidateSnapshot(
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

        test("ktor server htmx") { gen ->
            gen.generateAndValidateSnapshot(
                "ktor-server-htmx",
                listOf(
                    "org.gradle/gradle",
                    "io.ktor/server-core",
                    "io.ktor/server-netty",
                    "io.ktor/server-htmx",
                )
            )
        }

        test("ktor server rpc") { gen ->
            gen.generateAndValidateSnapshot(
                "ktor-server-rpc",
                listOf(
                    "org.gradle/gradle",
                    "io.ktor/server-core",
                    "io.ktor/server-netty",
                    "org.jetbrains/kotlinx-rpc",
                )
            )
        }

        test("compose multiplatform gradle") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-gradle", randomString())
            gen.generate(
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

        test("compose multiplatform toolchain") { gen ->
            val outputDir = Path(SystemTemporaryDirectory, "generated", "cmp-toolchain", randomString())
            gen.generateWithPacks(
                outputDir,
                "cmp-toolchain",
                "org.jetbrains/kotlin-toolchain",
                "org.jetbrains/compose-multiplatform",
            )
            assertFilesAreEqualWithSnapshot(
                "$snapshots/cmp-toolchain",
                outputDir.toString(),
            )
        }

        test("module remapping") { gen ->
            gen.generateAndValidateSnapshot(
                "module-remapping",
                listOf("com.acme/module-remapping"),
            )
        }
    } closeWith {
        tearDown()
    }
}

fun randomString() =
    Random(Clock.System.now().toEpochMilliseconds()).nextLong(111, 999).toString(36)

suspend fun ProjectGenerator.generate(
    outputDir: Path,
    name: String,
    properties: Map<VariableId, String> = emptyMap(),
    packs: List<String>
) {
    deleteRecursively(outputDir, SystemFileSystem)

    generate(
        ProjectDescriptor(
            name = name,
            group = DEFAULT_GROUP,
            properties = properties,
            packs = packs.map(PackId.Companion::parse),
        )
    ).export(outputDir)
}

suspend fun ProjectGenerator.generateWithPacks(outputDir: Path, name: String, vararg packs: String) =
    generate(outputDir, name, packs = packs.toList())


suspend fun ProjectGenerator.generateAndValidateSnapshot(
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
