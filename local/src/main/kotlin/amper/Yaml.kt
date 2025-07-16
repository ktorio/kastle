package org.jetbrains.kastle.amper

import com.charleskorn.kaml.*
import org.jetbrains.kastle.ArtifactDependency
import org.jetbrains.kastle.CatalogReference
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.ModuleDependency
import org.jetbrains.kastle.Platform
import org.jetbrains.kastle.SourceModuleType
import org.jetbrains.kastle.VersionsCatalog

fun YamlMap.readPlatforms(): Set<Platform> =
    (
        get<YamlScalar>("platform")?.content?.let(::listOf)
            ?: get<YamlList>("platforms")?.items
                ?.filterIsInstance<YamlScalar>()
                ?.map(YamlScalar::content) ?: emptyList()
    ).map(Platform::parse).toSet()

fun YamlMap.readDependencies(key: String): Set<Dependency> =
    this.get<YamlList>(key)?.items?.asSequence()
        ?.map(::dependencyString)
        .orEmpty()
        .filter(isNotTemplateDSL)
        .map(Dependency::parse)
        .toSet()

private fun dependencyString(node: YamlNode): String = when (node) {
    is YamlScalar -> node.yamlScalar.content
    is YamlMap -> {
        val entry = node.yamlMap.entries.entries.singleOrNull()
            ?: error("Expected single key for dependency, got: ${node.yamlMap.entries.entries}")
        require(entry.value.yamlScalar.content == "exported") {
            "Expected `exported` for dependency, got: ${entry.value.yamlScalar.content}"
        }
        entry.key.yamlScalar.content + ":exported"
    }
    else -> error("Unexpected node for dependency: $node")
}

private val isNotTemplateDSL: (String) -> Boolean = { !it.endsWith("/templates") }