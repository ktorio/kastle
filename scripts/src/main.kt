package org.jetbrains.kastle

import org.jetbrains.kastle.io.JsonFileFeatureRepository.Companion.exportToJson

suspend fun main() {
    LocalFeatureRepository("export/local/example")
        .exportToJson("export/local/json")
}