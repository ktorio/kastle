package org.jetbrains.kastle.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import org.jetbrains.kastle.client.asRepository
import org.jetbrains.kastle.get

class ClientServerTest : StringSpec({

    "get pack descriptor" {
        testApplication {
            configure("application.conf")

            val repository = client.asRepository()
            val pack = repository.get("com.acme/empty")
            pack.shouldNotBeNull()
            pack.name shouldBe "Empty Feature"
            pack.version.toString() shouldBe "1.0.0"
            pack.group?.id shouldBe "com.acme"
        }
    }

})