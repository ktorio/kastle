package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFilePackRepository.Companion.exportToJson

suspend fun main() {
    LocalPackRepository("local/example")
        .exportToJson(Path("local/json"), json = Json {
            prettyPrint = true
        })
}