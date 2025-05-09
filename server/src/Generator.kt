package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.*
import kotlinx.io.files.Path
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.io.CborFilePackRepository

/**
 * Provide a repository implementation based on configuration.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Application.provideGenerator() {
    val repositoryPath: String = property("repository.dir")
    dependencies {
        provide<PackRepository> { CborFilePackRepository(Path(repositoryPath)) }
        provide<ProjectGenerator> { ProjectGenerator.fromRepository(resolve()) }
    }
}