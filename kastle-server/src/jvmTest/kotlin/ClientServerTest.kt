package org.jetbrains.kastle.server

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.kastle.client.asRepository
import org.jetbrains.kastle.read

val ClientServerTest by testSuite("Client/Server API Test") {

    test("get pack descriptor") {
        testApplication {
            configure("application.conf")

            val repository = client.asRepository()
            val pack = repository.read("com.acme/empty")
            pack.shouldNotBeNull()
            pack.name shouldBe "Empty Feature"
            pack.version.toString() shouldBe "1.0.0"
            pack.group?.id shouldBe "com.acme"
        }
    }

    test("get files") {
        testApplication {
            configure("application.conf")

            val repository = client.asRepository()
            val fileList = repository.files().toList().sorted().joinToString("\n")
            val expected = """
                io.ktor/icon.svg
                org.gradle/icon.svg
                org.jetbrains/icon.svg
            """.trimIndent()

            fileList shouldBe expected
        }
    }

}
