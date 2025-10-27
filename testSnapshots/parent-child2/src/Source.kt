package com.acme

import java.nio.file.Paths

class Parent {
    fun install() {
        println("working dir: " + Paths.get("").toString())
        (0..10).forEach {
            println(it)
        }
    }
}