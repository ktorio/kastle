package org.jetbrains.kastle.server

import io.ktor.server.application.Application
import io.ktor.server.config.property
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import kotlinx.io.files.Path
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.io.JsonFilePackRepository

/**
 * Provide a repository implementation based on configuration.
 */
fun Application.provideRepository() {
    val jsonRepositoryPath: String = property("repository.dir")
    dependencies.provide<PackRepository> {
        JsonFilePackRepository(Path(jsonRepositoryPath))
    }
}