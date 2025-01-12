package com.acme

import java.nio.file.Paths

class Source {
    fun install() {
        // child source here
        println("working dir: " + Paths.get("").toString())
    }
}