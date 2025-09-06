package org.jetbrains.kastle

import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
fun assertFilesAreEqualWithSnapshot(
    expectedPath: String,
    actualPath: String,
    ignorePaths: Collection<String> = emptySet(),
    replace: Boolean = REPLACE_SNAPSHOTS,
) = try {
    assertFilesAreEqual(Paths.get(expectedPath), Paths.get(actualPath), ignorePaths)
} catch (cause: Throwable) {
    // Save a copy of the failed project
    val destination = if (replace) Paths.get(expectedPath)
                      else Files.createTempDirectory("actual-files-${System.currentTimeMillis()}")
    println("Files changed, see ${destination.absolute()} for new snapshot")
    if (!destination.exists())
        destination.createDirectories()
    destination.deleteRecursively()
    Paths.get(actualPath).copyToRecursively(destination, followLinks = false, overwrite = true)
    throw cause
}

fun assertFilesAreEqual(
    expected: Path,
    actual: Path,
    ignorePaths: Collection<String> = emptySet(),
    ignoreListing: Boolean = false,
) {
    if (!Files.exists(expected) || !Files.isDirectory(expected)) {
        fail("Expected path is not a directory or does not exist: $expected")
    }

    if (!Files.exists(actual) || !Files.isDirectory(actual)) {
        fail("Actual path is not a directory or does not exist: $actual")
    }

    val expectedFiles = Files.walk(expected, FileVisitOption.FOLLOW_LINKS)
        .filter { Files.isRegularFile(it) && !ignorePaths.contains(it.toString()) }
        .map { expected.relativize(it).toString() }
        .sorted()
        .collect(Collectors.toList())

    val actualFiles = Files.walk(actual, FileVisitOption.FOLLOW_LINKS)
        .filter { Files.isRegularFile(it) && !ignorePaths.contains(it.toString()) }
        .map { actual.relativize(it).toString() }
        .sorted()
        .collect(Collectors.toList())

    if (!ignoreListing) {
        val actualListing = actualFiles.joinToString("\n")
        val expectedListing = expectedFiles.joinToString("\n")
        actualListing shouldBe expectedListing
    }

    for (relativePath in expectedFiles) {
        val expectedFile = expected.resolve(relativePath)
        val actualFile = actual.resolve(relativePath)

        val expectedContents = expectedFile.readText().normalize()
        val actualContents = actualFile.readText().normalize()

        actualContents shouldBe expectedContents
    }
}

fun String.normalize(): String {
    var result = this
    // timestamps
    result = result.replace(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3,9}Z"""), "2023-02-03T23:23:23.000Z")
    // versions
    result = result.replace(Regex("""\d+\.\d+\.\d+(?:-[\w-]+)?"""), "1.0.0")

    return result.trim()
}