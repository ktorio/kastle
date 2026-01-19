package com.acme

import java.nio.file.Paths

class Parent {
    fun install() {
        (0..10).forEach {
            println(it)
        }
        println("working dir: " + Paths.get("").toString())
    }
}