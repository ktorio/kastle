package org.jetbrains.kastle.amper

import com.charleskorn.kaml.*
import org.jetbrains.kastle.ArtifactDependency
import org.jetbrains.kastle.CatalogReference
import org.jetbrains.kastle.Dependency
import org.jetbrains.kastle.ModuleDependency
import org.jetbrains.kastle.SourceModuleType
import org.jetbrains.kastle.VersionsCatalog

// TODO not entirely correct
fun YamlMap?.readHeader(): Pair<SourceModuleType, List<String>> =
    when(val productNode = this?.get<YamlNode>("product")) {
        is YamlScalar -> SourceModuleType.parse(productNode.content) to listOf("jvm")
        is YamlMap -> {
            val productType = SourceModuleType.parse(productNode.get<YamlScalar>("type")?.content ?: "lib")
            val platforms = productNode.get<YamlList>("platforms")?.items?.map { it.yamlScalar.content }.orEmpty()
            productType to platforms
        }
        else -> SourceModuleType.LIB to listOf("jvm")
    }

fun YamlMap?.readDependencies(key: String, versionsLookup: VersionsCatalog) =
    this?.get<YamlList>(key)?.items?.asSequence()
        ?.map(::dependencyString)
        .orEmpty()
        .filter(isNotTemplateDSL)
        .map(Dependency::parse)
        .map { dependency ->
            when(dependency) {
                is ArtifactDependency, is ModuleDependency -> dependency
                is CatalogReference -> dependency.resolve(versionsLookup)
            }
        }
        .toList()

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