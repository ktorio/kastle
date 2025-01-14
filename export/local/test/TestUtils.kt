package org.jetbrains.kastle

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalPathApi::class)
fun assertFilesAreEqualWithSnapshot(
    expectedPath: String,
    actualPath: String,
    ignorePaths: Collection<String> = emptySet(),
    replace: Boolean = false,
) = try {
    assertFilesAreEqual(Paths.get(expectedPath), Paths.get(actualPath), ignorePaths)
} catch (cause: Throwable) {
    // Save a copy of the failed project
    val destination = if (replace) Paths.get(expectedPath)
                      else Files.createTempDirectory("actual-files-${System.currentTimeMillis()}")
    destination.let { target ->
        println("Files changed, see ${target.absolute()} for new snapshot")
        if (!target.exists())
            target.createDirectories()
        target.deleteRecursively()
        Paths.get(actualPath).copyToRecursively(target, followLinks = false, overwrite = true)
    }
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
        .collect(Collectors.toSet())

    val actualFiles = Files.walk(actual, FileVisitOption.FOLLOW_LINKS)
        .filter { Files.isRegularFile(it) && !ignorePaths.contains(it.toString()) }
        .map { actual.relativize(it).toString() }
        .collect(Collectors.toSet())

    if (!ignoreListing) {
        assertEquals(
            expectedFiles.joinToString("\n"),
            actualFiles.joinToString("\n"),
            "File listing does not match"
        )
    }

    for (relativePath in expectedFiles) {
        val expectedFile = expected.resolve(relativePath)
        val actualFile = actual.resolve(relativePath)

        val expectedContents = expectedFile.readText().normalize()
        val actualContents = actualFile.readText().normalize()

        assertEquals(
            expectedContents,
            actualContents,
            "File contents do not match for: $relativePath"
        )
    }
}

fun String.normalize() =
    // replace timestamps
    replace(
        Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3,9}Z"""),
        "2023-02-03T23:23:23.000Z"
    ).trim()