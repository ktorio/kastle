package org.jetbrains.kastle.server.ui

object Resources {
    val stylesheet: String by lazy {
        this::class.java.getResourceAsStream("/style.css")!!.readAllBytes().decodeToString()
    }
    val script: String by lazy {
        """
            $appearance
            $htmxEvents
            $usability
            $generate
        """.trimIndent()
    }
    private val appearance by lazy {
        this::class.java.getResourceAsStream("/js/appearance.js")!!.readAllBytes().decodeToString()
    }
    private val htmxEvents by lazy {
        this::class.java.getResourceAsStream("/js/htmx-events.js")!!.readAllBytes().decodeToString()
    }
    private val usability by lazy {
        this::class.java.getResourceAsStream("/js/usability.js")!!.readAllBytes().decodeToString()
    }
    private val generate by lazy {
        this::class.java.getResourceAsStream("/js/generate.js")!!.readAllBytes().decodeToString()
    }
}