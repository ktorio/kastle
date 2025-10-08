package org.jetbrains.kastle.templates

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jetbrains.kastle.InlineValue
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.RepeatingSlot
import org.jetbrains.kastle.utils.Expression.VariableRef

class HandlebarsTemplateEngineTest : StringSpec({

    val engine = HandlebarsTemplateEngine()
    val path = "templates/test.txt"

    "literals" {
        val template = engine.read(path, """
            Hello, {{ someProperty }}!
        """.trimIndent())

        template.blocks.shouldNotBeNull()
        template.blocks!! shouldHaveSize 1
        val literal = template.blocks!!.first()
        literal.shouldBeInstanceOf<InlineValue>()
        val expression = literal.expression
        expression.shouldBeInstanceOf<VariableRef>()
        expression.name shouldBe "someProperty"
    }

    "slot" {
        val template = engine.read(path, """
            Hello, {{#slot someSlot}}!
        """.trimIndent())

        template.blocks.shouldNotBeNull()
        template.blocks!! shouldHaveSize 1

        val slot = template.blocks!!.first()
        slot.shouldBeInstanceOf<NamedSlot>()
        slot.name shouldBe "someSlot"
    }

    "repeatingSlot" {
        val template = engine.read(path, """
            Hello, {{#slots someSlot}}!
        """.trimIndent())

        template.blocks.shouldNotBeNull()
        template.blocks!! shouldHaveSize 1

        val slot = template.blocks!!.first()
        slot.shouldBeInstanceOf<RepeatingSlot>()
        slot.name shouldBe "someSlot"
    }

    "escapedBraces" {
        val template = engine.read(path, """
            This is a normal template: {{ someProperty }}
            These are escaped: \{{notAProperty}}\{{notAProperty}} \{{notAProperty}}
            This has both: {{ realProperty }} and \{{notAProperty}}
        """.trimIndent())

        template.blocks.shouldNotBeNull()
        template.blocks!! shouldHaveSize 2
        val expected = listOf("someProperty", "realProperty")
        for ((i, ref) in template.blocks!!.withIndex()) {
            ref.shouldBeInstanceOf<InlineValue>()
            val expression = ref.expression
            expression.shouldBeInstanceOf<VariableRef>()
            expression.name shouldBe expected[i]
            template.text.substring(ref.position.range).trim() shouldBe "{{ ${expected[i]} }}"
        }

        // Check that the backslash is removed in the processed text
        val processedText = template.text
        processedText shouldContain "{{notAProperty}}"
        processedText shouldNotContain "\\{{notAProperty}}"
    }

})