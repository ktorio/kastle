package org.jetbrains.kastle.templates

import org.jetbrains.kastle.EachBlock
import org.jetbrains.kastle.ElseBlock
import org.jetbrains.kastle.IfBlock
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.PropertyLiteral
import org.jetbrains.kastle.RepeatingSlot
import kotlin.test.*

class DoubleBraceTemplateEngineTest {

    private val engine = DoubleBraceTemplateEngine()
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
            {{ if someBooleanProperty }}
            Hello, {{ someProperty }}!
            {{ /if }}
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
        assertEquals("{{ someProperty }}", input.substring(literal.position.range))

        assertIs<IfBlock>(conditional)
        assertEquals(condition, input.substring(conditional.position.range))
        assertEquals("someBooleanProperty", conditional.property)
        assertEquals("\nHello, {{ someProperty }}!\n", input.substring(conditional.body!!.range)) // TODO trim newlines
    }

    @Test
    fun elseBlock() {
        val condition = """
            {{ if someBooleanProperty }}
            Hello, {{ someProperty }}!
            {{ else }}
            Goodbye!
            {{ /if }}
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
        assertEquals("{{ someProperty }}", input.substring(literal.position.range))

        assertIs<IfBlock>(ifBlock)
        assertEquals("someBooleanProperty", ifBlock.property)
        assertEquals("\nHello, {{ someProperty }}!\n", input.substring(ifBlock.body!!.range)) // TODO trim newlines

        assertIs<ElseBlock>(elseBlock)
        assertEquals("someBooleanProperty", elseBlock.property)
        assertEquals("\nGoodbye!\n", input.substring(elseBlock.body!!.range)) // TODO trim newlines
    }

    @Test
    fun forEach() {
        val loop = """
            {{ for elem in aList }}
            Hello, {{ elem }}!
            {{ /for }}
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
        assertEquals("elem", literal.property)
        assertEquals("{{ elem }}", input.substring(literal.position.range))
        assertEquals(loop, input.substring(forEach.position.range))

        assertIs<EachBlock>(forEach)
        assertEquals("aList", forEach.property)
        assertEquals("elem", forEach.variable)
        assertEquals("\nHello, {{ elem }}!\n", input.substring(forEach.body!!.range)) // TODO trim newlines
    }

    @Test
    fun slot() {
        val template = engine.read(path, """
            Hello, {{ slot someSlot }}!
        """.trimIndent())

        assertEquals(1, template.blocks?.size)

        val slot = template.blocks!!.first()
        assertIs<NamedSlot>(slot)
        assertEquals("someSlot", slot.name)
    }

    @Test
    fun repeatingSlot() {
        val template = engine.read(path, """
            Hello, {{ slots someSlot }}!
        """.trimIndent())

        assertEquals(1, template.blocks?.size)

        val slot = template.blocks!!.first()
        assertIs<RepeatingSlot>(slot)
        assertEquals("someSlot", slot.name)
    }

}