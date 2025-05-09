package org.jetbrains.kastle

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.jetbrains.kastle.io.FileSystemPackRepository.Companion.export
import org.jetbrains.kastle.logging.ConsoleLogger
import java.io.File

fun main(args: Array<String>) =
    CompileLocalRepository().main(args)

class CompileLocalRepository : CliktCommand() {
    val inputDir: File by option("--input-dir").file(
        mustExist = true,
        canBeFile = false,
    ).required()

    val outputDir: File by option("--output-dir")
        .file()
        .default(File("build/repository"))

    val logger = ConsoleLogger()

    override fun run() {
        logger.info { "Compiling local repository from ${inputDir.absolutePath} to ${outputDir.absolutePath}" }
        runBlocking {
            LocalPackRepository(Path(inputDir.absolutePath))
                .export(Path(outputDir.absolutePath))
        }
    }
}