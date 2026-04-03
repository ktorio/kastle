package org.jetbrains.kastle.templates

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.TemplateEvaluator.Companion.toString
import org.jetbrains.kastle.utils.LocalVariables
import org.jetbrains.kastle.utils.StringLiteral
import org.jetbrains.kastle.utils.Variables

class HandlebarsTemplateEngineTest : StringSpec({

    val engine = HandlebarsTemplateEngine()
    val target = StringLiteral("file://templates/test.txt")
    val packId = PackId("com.acme", "test")

    fun localVars(vararg pairs: Pair<String, Any?>) =
        LocalVariables(packId, pairs.toMap())

    fun template(text: String): SourceTemplate =
        engine.read(target, text)

    "literals" {
        val actual = template("Hello, {{ someProperty }}!").toString(
            variables = localVars("someProperty" to "World")
        )
        actual shouldBe "Hello, World!"
    }

    "if and else" {
        val template = template("{{#if someProperty}}Hello!{{else}}Goodbye!{{/if}}")

        template.toString(
            variables = localVars("someProperty" to true)
        ) shouldBe "Hello!"

        template.toString(
            variables = localVars("someProperty" to false)
        ) shouldBe "Goodbye!"
    }

    "unless" {
        val template = template("{{#unless someProperty}}Hello!{{else}}Goodbye!{{/unless}}")

        template.toString(
            variables = localVars("someProperty" to true)
        ) shouldBe "Goodbye!"

        template.toString(
            variables = localVars("someProperty" to false)
        ) shouldBe "Hello!"
    }

    "when" {
        val template = template("{{#when name}}{{\"Bob\"}}Hi{{\"Joe\"}}Hello{{else}}Up yours{{/when}}, {{name}}!")

        template.toString(
            variables = localVars("name" to "Bob")
        ) shouldBe "Hi, Bob!"

        template.toString(
            variables = localVars("name" to "Joe")
        ) shouldBe "Hello, Joe!"

        template.toString(
            variables = localVars("name" to "Steve")
        ) shouldBe "Up yours, Steve!"
    }

    // TODO extra newline?
    "each" {
        template("""
            {{#each names}}
            - {{this}}
            {{/each}}
        """.trimIndent()).toString(
            variables = localVars("names" to listOf("Bob", "Alice", "Joe"))
        ) shouldBe """
            
            - Bob
            - Alice
            - Joe
        """.trimIndent()
    }

    "slot" {
        template("Hello, {{slot someSlot}}!").toString(
            slots = mapOf(
                "slot:someSlot" to listOf(template("you slot"))
            )
        ) shouldBe "Hello, you slot!"
    }

    "repeatingSlot" {
        template("""
            Hello, these slots:
            {{slots someSlot}}
        """.trimIndent()).toString(
            slots = mapOf(
                "slot:someSlot" to listOf(
                    template("first"),
                    template("second"),
                    template("third")
                )
            )
        ) shouldBe """
            Hello, these slots:
            first
            second
            third
        """.trimIndent()
    }

    "escapedBraces" {
        template("""
            This is a normal template: {{ someProperty }}
            These are escaped: \{{notAProperty}}\{{notAProperty}} \{{notAProperty}}
            This has both: {{ realProperty }} and \{{notAProperty}}
        """.trimIndent()).toString(
            variables = localVars(
                "someProperty" to "some value",
                "notAProperty" to "shouldn't appear",
                "realProperty" to "another value",
            )
        ) shouldBe """
            This is a normal template: some value
            These are escaped: {{notAProperty}}{{notAProperty}} {{notAProperty}}
            This has both: another value and {{notAProperty}}
        """.trimIndent()
    }

    "nested braces" {
        template($$"${{{library}}Version}").toString(
            variables = localVars("library" to "kotlin")
        ) shouldBe $$"${kotlinVersion}"
    }

})
