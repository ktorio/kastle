package org.jetbrains.kastle.utils

import kotlin.random.Random

fun Random.nextStringId(length: Int = 12): String =
    ByteArray(length).also(this::nextBytes).joinToString("") {
        it.toUByte().toString(16).padStart(2, '0')
    }