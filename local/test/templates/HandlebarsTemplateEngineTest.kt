package org.jetbrains.kastle.templates

import org.jetbrains.kastle.ForEachBlock
import org.jetbrains.kastle.ElseBlock
import org.jetbrains.kastle.IfBlock
import org.jetbrains.kastle.NamedSlot
import org.jetbrains.kastle.ExpressionValue
import org.jetbrains.kastle.RepeatingSlot
import org.jetbrains.kastle.utils.Expression.VariableRef
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
        assertIs<ExpressionValue>(literal)
        val expression = literal.expression
        assertIs<VariableRef>(expression)
        assertEquals("someProperty", expression.name)
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

        assertIs<ExpressionValue>(literal)
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("someProperty", expression.name)
            assertEquals("{{ someProperty }}", input.substringEx(literal.range))
        }

        assertIs<IfBlock>(conditional)
        assertEquals(condition, input.substringEx(conditional.range))
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("someBooleanProperty", expression.name)
        }

        val expression = literal.expression
        assertIs<VariableRef>(expression)
        assertEquals("someBooleanProperty", expression.name)
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
        assertIs<ExpressionValue>(literal)
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("someProperty", expression.name)
        }
        assertEquals("{{ someProperty }}", input.substringEx(literal.range))

        assertIs<IfBlock>(ifBlock)
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("someBooleanProperty", expression.name)
        }
        assertEquals("\nHello, {{ someProperty }}!\n", input.substringEx(ifBlock.body)) // TODO trim newlines

        assertIs<ElseBlock>(elseBlock)
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("someBooleanProperty", expression.name)
        }
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
        assertIs<ExpressionValue>(literal)
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("this", expression.name)
        }
        assertEquals("{{ this }}", input.substringEx(literal.range))
        assertEquals(loop, input.substringEx(forEach.range))

        assertIs<ForEachBlock>(forEach)
        literal.expression.let { expression ->
            assertIs<VariableRef>(expression)
            assertEquals("aList", expression.name)
        }
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