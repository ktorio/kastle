package org.jetbrains.kastle

import org.jetbrains.kastle.io.JsonFileKodRepository.Companion.exportToJson

suspend fun main() {
    LocalKodRepository("export/local/example")
        .exportToJson("export/local/json")
}