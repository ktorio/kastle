package org.jetbrains.kastle.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class MapEncoderTest : StringSpec({

    "encodes simple types" {
        Address(
            streetNumber = 123,
            streetName = "Main Street",
            city = "Enirim",
            country = "Westfallia",
            postalCode = "1111GG"
        ).encodeToMap() shouldBe mapOf(
            "streetNumber" to 123,
            "streetName" to "Main Street",
            "city" to "Enirim",
            "country" to "Westfallia",
            "postalCode" to "1111GG",
        )
    }

    "encodes nested types" {
        Person(
            name = "Ada",
            age = 37,
            address = Address(
                streetNumber = 123,
                streetName = "Main Street",
                city = "Enirim",
                country = "Westfallia",
                postalCode = "1111GG"
            )
        ).encodeToMap() shouldBe mapOf(
            "name" to "Ada",
            "age" to 37,
            "address" to mapOf(
                "streetNumber" to 123,
                "streetName" to "Main Street",
                "city" to "Enirim",
                "country" to "Westfallia",
                "postalCode" to "1111GG",
            )
        )
    }

    "encodes lists of literals" {
        Team(
            name = "Core",
            tags = listOf("backend", "kotlin", "serialization")
        ).encodeToMap() shouldBe mapOf(
            "name" to "Core",
            "tags" to listOf("backend", "kotlin", "serialization")
        )
    }

    "encodes lists of nested types" {
        Company(
            name = "Kastle",
            offices = listOf(
                Address(
                    streetNumber = 1,
                    streetName = "North Street",
                    city = "Oslo",
                    country = "Norway",
                    postalCode = "0001"
                ),
                Address(
                    streetNumber = 2,
                    streetName = "South Street",
                    city = "Helsinki",
                    country = "Finland",
                    postalCode = "0010"
                )
            )
        ).encodeToMap() shouldBe mapOf(
            "name" to "Kastle",
            "offices" to listOf(
                mapOf(
                    "streetNumber" to 1,
                    "streetName" to "North Street",
                    "city" to "Oslo",
                    "country" to "Norway",
                    "postalCode" to "0001",
                ),
                mapOf(
                    "streetNumber" to 2,
                    "streetName" to "South Street",
                    "city" to "Helsinki",
                    "country" to "Finland",
                    "postalCode" to "0010",
                )
            )
        )
    }

    "encodes maps of literals" {
        ConfigHolder(
            config = mapOf(
                "host" to "localhost",
                "port" to "8080",
                "enabled" to "true"
            )
        ).encodeToMap() shouldBe mapOf(
            "config" to mapOf(
                "host" to "localhost",
                "port" to "8080",
                "enabled" to "true"
            )
        )
    }

    "encodes nullables and skips nulls" {
        OptionalValues(
            title = null,
            count = 42,
            note = null
        ).encodeToMap() shouldBe mapOf(
            "count" to 42,
        )
    }

    "encodes mixed literals" {
        MixedLiterals(
            text = "hello",
            number = 7,
            flag = true,
            ratio = 1.5,
            items = listOf(1, 2, 3)
        ).encodeToMap() shouldBe mapOf(
            "text" to "hello",
            "number" to 7,
            "flag" to true,
            "ratio" to 1.5,
            "items" to listOf(1, 2, 3)
        )
    }

})

@Serializable
data class Address(
    val streetNumber: Int,
    val streetName: String,
    val city: String,
    val country: String,
    val postalCode: String,
)

@Serializable
data class Person(
    val name: String,
    val age: Int,
    val address: Address,
)

@Serializable
data class Team(
    val name: String,
    val tags: List<String>,
)

@Serializable
data class Company(
    val name: String,
    val offices: List<Address>,
)

@Serializable
data class ConfigHolder(
    val config: Map<String, String>,
)

@Serializable
data class OptionalValues(
    val title: String?,
    val count: Int?,
    val note: String?,
)

@Serializable
data class MixedLiterals(
    val text: String,
    val number: Int,
    val flag: Boolean,
    val ratio: Double,
    val items: List<Int>,
)
