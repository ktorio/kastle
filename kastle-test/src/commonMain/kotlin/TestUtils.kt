package org.jetbrains.kastle

import io.kotest.matchers.shouldBe
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import org.jetbrains.kastle.io.resolve

private val DEFAULT_IGNORE_FILES = setOf(
    ".gitkeep",
    "gradle-wrapper.jar"
)

fun assertFilesAreEqualWithSnapshot(
    expectedPath: String,
    actualPath: String,
    ignorePaths: Collection<String> = DEFAULT_IGNORE_FILES,
    replace: Boolean = REPLACE_SNAPSHOTS,
) {
    val fs = SystemFileSystem
    try {
        assertFilesAreEqual(
            Path(expectedPath),
            Path(actualPath),
            ignorePaths,
        )
    } catch (cause: Throwable) {
        // Save a copy of the failed project
        val destination = if (replace) Path(expectedPath)
        else Path(fs.metadataOrNull(SystemTemporaryDirectory)?.toString() ?: ".")
            .resolve("actual-files-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}")

        println("Files changed, see $destination for new snapshot")

        if (!fs.exists(destination)) {
            fs.createDirectories(destination)
        }

        deleteRecursively(destination, fs)
        copyRecursively(Path(actualPath), destination, fs)

        if (!replace)
            throw cause
    }
}

fun assertFilesAreEqual(
    expected: Path,
    actual: Path,
    ignorePaths: Collection<String> = emptySet(),
    ignoreListing: Boolean = false,
) {
    val fs = SystemFileSystem

    if (!fs.exists(expected) || fs.metadataOrNull(expected)!!.isRegularFile) {
        error("Expected path is not a directory or does not exist: $expected")
    }

    if (!fs.exists(actual) || fs.metadataOrNull(actual)!!.isRegularFile) {
        error("Actual path is not a directory or does not exist: $actual")
    }

    val expectedFiles = walkFiles(expected, fs)
        .filter { !ignorePaths.contains(it.name) }
        .map { relativePath(expected, it) }
        .sorted()

    val actualFiles = walkFiles(actual, fs)
        .filter { !ignorePaths.contains(it.name) }
        .map { relativePath(actual, it) }
        .sorted()

    if (!ignoreListing) {
        val actualListing = actualFiles.joinToString("\n")
        val expectedListing = expectedFiles.joinToString("\n")
        actualListing shouldBe expectedListing
    }

    for (relativePath in expectedFiles) {
        val expectedFile = expected.resolve(relativePath)
        val actualFile = actual.resolve(relativePath)

        val expectedContents = readText(expectedFile, fs).normalize()
        val actualContents = readText(actualFile, fs).normalize()

        actualContents shouldBe expectedContents
    }
}

private fun walkFiles(path: Path, fs: FileSystem = SystemFileSystem): List<Path> {
    val result = mutableListOf<Path>()

    fun walk(current: Path) {
        val metadata = fs.metadataOrNull(current) ?: return

        if (metadata.isRegularFile) {
            result.add(current)
        } else if (metadata.isDirectory) {
            fs.list(current).forEach { walk(it) }
        }
    }

    walk(path)
    return result
}

private fun relativePath(base: Path, target: Path): String {
    val baseParts = base.toString().split('/')
    val targetParts = target.toString().split('/')

    val commonPrefix = baseParts.zip(targetParts)
        .takeWhile { (a, b) -> a == b }
        .count()

    return targetParts.drop(commonPrefix).joinToString("/")
}

private fun readText(path: Path, fs: FileSystem = SystemFileSystem): String =
    fs.source(path).buffered().use { it.readString() }

private fun copyRecursively(source: Path, destination: Path, fs: FileSystem = SystemFileSystem) {
    val metadata = fs.metadataOrNull(source) ?: return

    if (metadata.isRegularFile) {
        fs.source(source).buffered().use { input ->
            fs.sink(destination).buffered().use { output ->
                input.transferTo(output)
            }
        }
    } else if (metadata.isDirectory) {
        if (!fs.exists(destination)) {
            fs.createDirectories(destination)
        }
        fs.list(source).forEach { child ->
            val childName = child.name
            copyRecursively(child, destination.resolve(childName), fs)
        }
    }
}

private fun deleteRecursively(path: Path, fs: FileSystem = SystemFileSystem) {
    if (!fs.exists(path)) return

    val metadata = fs.metadataOrNull(path) ?: return

    if (metadata.isDirectory) {
        fs.list(path).forEach { deleteRecursively(it, fs) }
    }

    fs.delete(path)
}

fun String.normalize(): String {
    var result = this
    // timestamps
    result = result.replace(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3,9}Z"""), "2023-02-03T23:23:23.000Z")
    // versions
    result = result.replace(Regex("""\d+\.\d+\.\d+(?:-[\w-]+)?"""), "1.0.0")

    return result.trim()
}