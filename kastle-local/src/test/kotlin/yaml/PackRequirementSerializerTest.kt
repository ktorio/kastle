package org.jetbrains.kastle.yaml

import com.charleskorn.kaml.Yaml
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.*
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.PackRequirement

class PackRequirementSerializerTest : StringSpec({
    val yaml = Yaml.default

    "deserializes a plain string as PackId" {
        val text = "org.jetbrains/foo"
        val result = yaml.decodeFromString(PackRequirementYamlSerializer(), text)
        result shouldBe PackRequirement(
            packId = PackId("org.jetbrains", "foo"),
            modules = emptyMap(),
        )
    }

    "deserializes a single-key map with a string value as modules with empty key" {
        val text = """
            org.jetbrains/foo: bar
        """.trimIndent()
        val result = yaml.decodeFromString(PackRequirementYamlSerializer(), text)
        result shouldBe PackRequirement(
            packId = PackId("org.jetbrains", "foo"),
            modules = mapOf("" to "bar"),
        )
    }

    "deserializes a single-key map with a map value as modules" {
        val text = """
            org.jetbrains/foo:
              moduleA: depA
              moduleB: depB
        """.trimIndent()
        val result = yaml.decodeFromString(PackRequirementYamlSerializer(), text)
        result shouldBe PackRequirement(
            packId = PackId("org.jetbrains", "foo"),
            modules = mapOf("moduleA" to "depA", "moduleB" to "depB"),
        )
    }

    "serializes a requirement with no modules as a string" {
        val value = PackRequirement(
            packId = PackId("org.jetbrains", "foo"),
            modules = emptyMap(),
        )
        val text = yaml.encodeToString(PackRequirementYamlSerializer(), value)
        text.trim() shouldBe "\"org.jetbrains/foo\""
    }
})
