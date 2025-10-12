package com.acme

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform