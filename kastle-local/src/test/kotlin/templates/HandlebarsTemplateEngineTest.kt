package org.jetbrains.kastle.templates

import org.jetbrains.kastle.InlineValue
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.RepeatingSlot
import org.jetbrains.kastle.utils.Expression.VariableRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HandlebarsTemplateEngineTest {

    private val engine = HandlebarsTemplateEngine()
    private val path = "templates/test.txt"

    @Test
    fun literals() {
        val template = engine.read(path, """
            Hello, {{ someProperty }}!
        """.trimIndent())

        assertEquals(1, template.blocks?.size)
        val literal = template.blocks!!.first()
        assertIs<InlineValue>(literal)
        val expression = literal.expression
        assertIs<VariableRef>(expression)
        assertEquals("someProperty", expression.name)
    }

    @Test
    fun slot() {
        val template = engine.read(path, """
            Hello, {{#slot someSlot}}!
        """.trimIndent())

        assertEquals(1, template.blocks?.size)

        val slot = template.blocks!!.first()
        assertIs<NamedSlot>(slot)
        assertEquals("someSlot", slot.name)
    }

    @Test
    fun repeatingSlot() {
        val template = engine.read(path, """
            Hello, {{#slots someSlot}}!
        """.trimIndent())

        assertEquals(1, template.blocks?.size)

        val slot = template.blocks!!.first()
        assertIs<RepeatingSlot>(slot)
        assertEquals("someSlot", slot.name)
    }


    @Test
    fun escapedBraces() {
        val template = engine.read(path, """
            This is a normal template: {{ someProperty }}
            These are escaped: \{{notAProperty}}\{{notAProperty}} \{{notAProperty}}
            This has both: {{ realProperty }} and \{{notAProperty}}
        """.trimIndent())

        assertEquals(2, template.blocks?.size)
        val expected = listOf("someProperty", "realProperty")
        for ((i, ref) in template.blocks!!.withIndex()) {
            assertIs<InlineValue>(ref)
            val expression = ref.expression
            assertIs<VariableRef>(expression)
            assertEquals(expected[i], expression.name)
            assertEquals("{{ ${expected[i]} }}", template.text.substring(ref.position.range).trim())
        }

        // Check that the backslash is removed in the processed text
        val processedText = template.text
        assertEquals(true, processedText.contains("{{notAProperty}}"))
        assertEquals(false, processedText.contains("\\{{notAProperty}}"))
    }

}