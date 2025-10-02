package com.acme

import java.nio.file.Pathsclass Parent {
    fun install() {
        // child source here
            println("working dir: " + Paths.get("").toString())
    }
}