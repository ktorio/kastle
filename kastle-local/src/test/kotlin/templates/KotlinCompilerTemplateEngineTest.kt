package org.jetbrains.kastle.templates

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kastle.PackId
import org.jetbrains.kastle.SourceTemplate
import org.jetbrains.kastle.TemplateEvaluator.Companion.toString
import org.jetbrains.kastle.utils.LocalVariables
import org.jetbrains.kastle.utils.Variables

class KotlinCompilerTemplateEngineTest : StringSpec({

    val engine = KotlinCompilerTemplateEngine()
    val packId = PackId("com.acme", "test")

    fun localVars(vararg pairs: Pair<String, Any?>) =
        LocalVariables(packId, pairs.toMap())

    fun template(text: String): SourceTemplate =
        engine.read(text = text)

    "literals" {
        val template = template("""
            val title: String by _properties
            val html = html {
                h1 {
                    +title
                }
            }
        """.trimIndent())

        template.toString(
            variables = localVars("title" to "Hello, World!")
        ) shouldBe "\nval html = html {\n    h1 {\n        +\"Hello, World!\"\n    }\n}"
    }

    "string interpolation" {
        val template = template($$"""
            val someProperty: String by _properties
            fun main() {
                println("Hello, $someProperty!")
            }
        """.trimIndent())

        template.toString(
            variables = localVars("someProperty" to "World")
        ) shouldBe "\nfun main() {\n    println(\"Hello, World!\")\n}"
    }

    "if and else" {
        val template = template("""
            val condition: Boolean by _properties
            val result = if (condition) "on" else "off"
        """.trimIndent())

        template.toString(
            variables = localVars("condition" to true)
        ) shouldBe "\nval result = \"on\""

        template.toString(
            variables = localVars("condition" to false)
        ) shouldBe "\nval result = \"off\""
    }

    "for each" {
        val template = template("""
            val names: List<String> by _properties
            fun readNames(callback: (String) -> Unit) {
                for (name in names) {
                    callback(name)
                }
            }
        """.trimIndent())

        template.toString(
            variables = localVars("names" to listOf("John", "Jane", "Jill"))
        ) shouldBe """
            
            fun readNames(callback: (String) -> Unit) {
                callback("John")
                callback("Jane")
                callback("Jill")
            }
        """.trimIndent()
    }

    "operator expressions" {
        val template = template("""
            val number: Int by _properties
            fun main() {
                if (number + 42 > 100) {
                    bigNumber()
                } else {
                    smallNumber()
                }
            }
        """.trimIndent().trim('\n'))

        template.toString(
            variables = localVars("number" to 60)
        ) shouldBe """
            
            fun main() {
                bigNumber()
            }
        """.trimIndent()

        template.toString(
            variables = localVars("number" to 37)
        ) shouldBe """
            
            fun main() {
                smallNumber()
            }
        """.trimIndent()
    }

})
