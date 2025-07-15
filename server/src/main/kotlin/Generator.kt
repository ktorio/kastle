package org.jetbrains.kastle.server

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.di.annotations.*
import io.ktor.util.logging.*
import kotlinx.io.files.Path
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kastle.LocalPackRepository
import org.jetbrains.kastle.PackRepository
import org.jetbrains.kastle.ProjectGenerator
import org.jetbrains.kastle.io.CborFilePackRepository
import org.jetbrains.kastle.io.FileFormat
import org.jetbrains.kastle.io.FileSystemPackRepository.Companion.export
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

/**
 * Provide a repository implementation based on configuration.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Application.provideGenerator(
    @Property("repository.source") sourceDir: String,
    @Property("repository.dir") compiledDir: String,
) {
    val log = environment.log
    dependencies {
        provide<PackRepository> { getCompiledRepository(sourceDir, compiledDir, log) }
        provide<ProjectGenerator> { ProjectGenerator.fromRepository(resolve()) }
    }
}

typealias JavaPath = java.nio.file.Path
fun JavaPath(str: String) = kotlin.io.path.Path(str)

@OptIn(ExperimentalSerializationApi::class)
private suspend fun getCompiledRepository(
    sourceDir: String,
    compiledDir: String,
    log: Logger,
): PackRepository {
    val sourcePath = JavaPath(sourceDir)
    val compiledPath = JavaPath(compiledDir)
    val compiledKotlinxPath = Path(compiledDir)

    if (!sourcePath.exists()) {
        log.warn("Source directory $sourceDir does not exist; continuing with compiled dir $compiledDir")
        return CborFilePackRepository(compiledKotlinxPath)
    }

    // TODO check if this works with modified files inside dir
    if (compiledPath.exists() && lastModifiedFile(compiledPath) > lastModifiedFile(sourcePath)) {
        log.info("Compiled repository $compiledDir is up to date; skipping compilation")
        return CborFilePackRepository(compiledKotlinxPath)
    }

    log.info("Source repository updated; compiling from $sourceDir to $compiledDir")
    return LocalPackRepository(Path(sourceDir))
        .export(Path(compiledDir), fileFormat = FileFormat.CBOR)
}

fun lastModifiedFile(directory: JavaPath): FileTime {
    var latestTime = directory.getLastModifiedTime()

    Files.walk(directory).use { paths ->
        paths.forEach { path ->
            val modTime = path.getLastModifiedTime()
            if (modTime > latestTime) {
                latestTime = modTime
            }
        }
    }

    return latestTime
}
