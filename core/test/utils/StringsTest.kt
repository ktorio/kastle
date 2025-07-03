package org.jetbrains.kastle.utils

import kotlin.test.*

class SourceFileWriterTest {

    @Test
    fun testIndentation() {
        val testString = """
            
            }
        }
        
        plugins {
            id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
        """.trimIndent()

        val actual = StringBuilder()
            .append(testString, 0, testString.length, indent = 0)
            .toString()
        assertEquals(testString, actual)
    }

}