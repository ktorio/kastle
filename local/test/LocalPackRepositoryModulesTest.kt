package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import org.jetbrains.kastle.gen.Project
import org.jetbrains.kastle.gen.load
import kotlin.test.Test
import kotlin.test.assertEquals

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"

class LocalPackRepositoryModulesTest {

    private val root = "../example"
    private val repository = LocalPackRepository(root)

    @Test
    fun `compose multiplatform`() = runTest {
        val project = loadProjectDetails(packs = listOf(
            "std/amper",
            "org.jetbrains/compose-multiplatform",
        ))
        assertEquals(
            listOf("android", "common", "desktop"),
            project.moduleSources.modules.mapNotNull {
                it.path.takeIf(String::isNotEmpty)
            }.sorted()
        )
    }

    private suspend fun loadProjectDetails(
        properties: Map<VariableId, String> = emptyMap(),
        packs: List<String>,
    ): Project {
        val descriptor = ProjectDescriptor(
            name = defaultName,
            group = defaultGroup,
            properties = properties,
            packs = packs.map(PackId::parse),
        )
        return descriptor.load(repository)
    }

}