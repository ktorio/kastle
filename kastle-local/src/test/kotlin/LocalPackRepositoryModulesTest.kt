package org.jetbrains.kastle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kastle.gen.Project
import org.jetbrains.kastle.gen.ProjectResolver

private const val defaultName = "sample"
private const val defaultGroup = "com.acme"

class LocalPackRepositoryModulesTest : StringSpec({

    val root = "../repository"
    val repository = LocalPackRepository(root)

    suspend fun loadProjectDetails(
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

    "compose multiplatform" {
        val project = loadProjectDetails(packs = listOf(
            "org.jetbrains/amper",
            "org.jetbrains/compose-multiplatform",
        ))
        val actualModules = project.moduleSources.modules
            .mapNotNull { it.path.takeIf(String::isNotEmpty) }
            .sorted()
        actualModules shouldBe listOf(
            "android",
            "desktop",
            "shared",
            "ios",
            "web",
        )
    }
})