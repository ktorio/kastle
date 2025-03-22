package org.jetbrains.kastle.templates

import org.jetbrains.kastle.BlockPosition.Companion.reduceEnd
import org.jetbrains.kastle.ForEachBlock
import org.jetbrains.kastle.ElseBlock
import org.jetbrains.kastle.IfBlock
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.PropertyLiteral
import org.jetbrains.kastle.RepeatingSlot
import org.jetbrains.kastle.utils.body
import org.jetbrains.kastle.utils.range
import kotlin.test.*

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
        assertIs<PropertyLiteral>(literal)
        assertEquals("someProperty", literal.property)
    }

    @Test
    fun ifBlock() {
        val condition = """
            {{#if someBooleanProperty }}
            Hello, {{ someProperty }}!
            {{/if }}
        """.trimIndent()
        val input = """
            Before blocks
            $condition
            After blocks
        """.trimIndent()
        val template = engine.read(path, input)

        val blocks = template.blocks
        assertNotNull(blocks)
        assertEquals(2, blocks.size)
        val (literal, conditional) = blocks

        assertIs<PropertyLiteral>(literal)
        assertEquals("someProperty", literal.property)
        assertEquals("{{ someProperty }}", input.substringEx(literal.range))

        assertIs<IfBlock>(conditional)
        assertEquals(condition, input.substringEx(conditional.range))
        assertEquals("someBooleanProperty", conditional.property)
        assertEquals("\nHello, {{ someProperty }}!\n", input.substringEx(conditional.body)) // TODO trim newlines
    }

    @Test
    fun elseBlock() {
        val condition = """
            {{#if someBooleanProperty }}
            Hello, {{ someProperty }}!
            {{else}}
            Goodbye!
            {{/if}}
        """.trimIndent()
        val input = """
            Before blocks
            $condition
            After blocks
        """.trimIndent()
        val template = engine.read(path, input)

        val blocks = template.blocks
        assertNotNull(blocks)
        assertEquals(3, blocks.size)

        val (literal, ifBlock, elseBlock) = blocks
        assertIs<PropertyLiteral>(literal)
        assertEquals("someProperty", literal.property)
        assertEquals("{{ someProperty }}", input.substringEx(literal.range))

        assertIs<IfBlock>(ifBlock)
        assertEquals("someBooleanProperty", ifBlock.property)
        assertEquals("\nHello, {{ someProperty }}!\n", input.substringEx(ifBlock.body)) // TODO trim newlines

        assertIs<ElseBlock>(elseBlock)
        assertEquals("someBooleanProperty", elseBlock.property)
        assertEquals("\nGoodbye!\n", input.substringEx(elseBlock.body)) // TODO trim newlines
    }

    @Test
    fun forEach() {
        val loop = """
            {{#each aList}}
            Hello, {{ this }}!
            {{/each}}
        """.trimIndent()
        val input = """
            Before blocks
            $loop
            After blocks
        """.trimIndent()
        val template = engine.read(path, input)

        val blocks = template.blocks
        assertNotNull(blocks)
        assertEquals(2, blocks.size)

        val (literal, forEach) = blocks
        assertIs<PropertyLiteral>(literal)
        assertEquals("this", literal.property)
        assertEquals("{{ this }}", input.substringEx(literal.range))
        assertEquals(loop, input.substringEx(forEach.range))

        assertIs<ForEachBlock>(forEach)
        assertEquals("aList", forEach.property)
        assertNull(forEach.variable)
        assertEquals("\nHello, {{ this }}!\n", input.substringEx(forEach.body)) // TODO trim newlines
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

    fun String.substringEx(intRange: IntRange) =
        substring(intRange.first, intRange.endInclusive)

}