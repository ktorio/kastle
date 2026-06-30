package org.jetbrains.kastle.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.kastle.client.asRepository
import org.jetbrains.kastle.read

class ClientServerTest : StringSpec({

    "get pack descriptor" {
        testApplication {
            configure("application.conf")

            val repository = client.asRepository()
            val pack = repository.read("org.jetbrains.intellij.platform/plugin")
            pack.shouldNotBeNull()
            pack.name shouldBe "IDE Plugin"
            pack.version.toString() shouldBe "1.0.0"
            pack.group?.id shouldBe "org.jetbrains.intellij.platform"
        }
    }

    // we don't have supplementary files in repository-intellij
    /*"get files" {
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
    }*/

})
