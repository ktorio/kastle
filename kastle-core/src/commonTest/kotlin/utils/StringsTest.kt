package org.jetbrains.kastle.utils

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val SourceFileWriterTest by testSuite("SourceFileWriter") {

    test("append with level") {
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

}
