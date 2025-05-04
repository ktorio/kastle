package org.jetbrains.kastle.server.ui

object Resources {
    val css: String by lazy {
        this::class.java.getResourceAsStream("/style.css")!!.readAllBytes().decodeToString()
    }
    val js: String by lazy {
        this::class.java.getResourceAsStream("/script.js")!!.readAllBytes().decodeToString()
    }
}