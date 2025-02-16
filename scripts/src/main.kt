package org.jetbrains.kastle

import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.jetbrains.kastle.io.JsonFilePackRepository.Companion.exportToJson

suspend fun main() {
    val inputDir = Path("local/example")
    val outputDir = Path("server/json")
    LocalPackRepository(inputDir)
        .exportToJson(outputDir, json = Json {
            prettyPrint = true
        })
}