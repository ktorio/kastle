package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.io.CborFilePackRepository

/**
 * Provide a repository implementation based on configuration.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Application.provideGenerator() {
    val repositoryPath = Path(property<String>("repository.dir"))
    if (!SystemFileSystem.exists(repositoryPath))
        throw IllegalStateException("Repository $repositoryPath does not exist")
    environment.log.info("Reading CBOR repository from: $repositoryPath")
    dependencies {
        provide<PackRepository> { CborFilePackRepository(repositoryPath) }
        provide<ProjectGenerator> { ProjectGenerator.fromRepository(resolve()) }
    }
}