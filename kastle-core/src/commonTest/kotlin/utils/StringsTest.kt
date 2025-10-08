package org.jetbrains.kastle.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SourceFileWriterTest : StringSpec({

    "append with level" {
        val testString = """
            
            }
        }
        
        plugins {
            id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
        """.trimIndent()

        testString shouldBe StringBuilder()
            .append(testString, 0, testString.length, level = 0)
            .toString()
    }

})