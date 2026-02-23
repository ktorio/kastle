package org.jetbrains.kastle.utils

import kotlin.random.Random

fun Random.nextStringId(length: Int = 12): String =
    ByteArray(length).also(this::nextBytes).joinToString("") {
        it.toUByte().toString(16).padStart(2, '0')
    }

/**
 * Generates a random 32-byte hash encoded as a hexadecimal string.
 *
 * @return A randomly generated hash string of 64 hexadecimal characters,
 *         created by encoding a 32-byte random sequence into hexadecimal format.
 */
fun generateRandomHash(): String {
    val bytes = Random.nextBytes(32)
    return bytes.joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        value.toString(16).padStart(2, '0')
    }
}
