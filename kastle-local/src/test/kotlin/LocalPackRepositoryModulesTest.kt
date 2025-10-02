package org.jetbrains.kastle

import kotlinx.coroutines.test.runTest
import org.jetbrains.kastle.gen.Project
import org.jetbrains.kastle.gen.ProjectResolver
import kotlin.test.Test
import kotlin.test.assertEquals

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"

class LocalPackRepositoryModulesTest {

    private val root = "../repository"
    private val repository = LocalPackRepository(root)

    @Test
    fun `compose multiplatform`() = runTest {
        val project = loadProjectDetails(packs = listOf(
            "org.jetbrains/amper",
            "org.jetbrains/compose-multiplatform",
        ))
        assertEquals(
            listOf("android", "desktop", "shared"),
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
        return ProjectResolver.Default.resolve(descriptor, repository)
    }

}