package org.jetbrains.kastle.io

import com.akuleshov7.ktoml.Toml
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink
import kotlin.math.log10
import kotlin.math.pow

fun Path.resolve(subPath: String) = Path(this, subPath)

@OptIn(ExperimentalSerializationApi::class)
fun Path.readText(fs: FileSystem = SystemFileSystem): String? {
    if (!fs.exists(this)) return null
    return fs.source(this).use {
        it.buffered().readString()
    }
}

// TODO
fun Path.relativeTo(base: Path): Path =
    Path(toString().removePrefix(base.toString()).removePrefix("/"))

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T: Any> Path.readCbor(
    fs: FileSystem = SystemFileSystem,
    cbor: Cbor = Cbor.Default,
): T? {
    if (!fs.exists(this)) return null
    return fs.source(this).use {
        cbor.decodeFromByteArray<T>(it.buffered().readByteArray())
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Path.writeCbor(
    item: T,
    fs: FileSystem = SystemFileSystem,
    cbor: Cbor = Cbor.Default
) {
    fs.sink(this).use { rs ->
        rs.buffered().use { sink ->
            sink.write(cbor.encodeToByteArray(item))
        }
    }
}
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T: Any> Path.readJson(
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
): T? {
    if (!fs.exists(this)) return null
    return fs.source(this).use {
        json.decodeFromSource<T>(it.buffered())
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Path.writeJson(
    item: T,
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
) {
    fs.sink(this).use { rs ->
        rs.buffered().use { sink ->
            json.encodeToSink<T>(item, sink)
        }
    }
}

inline fun <reified T: Any> Path.readYaml(
    fs: FileSystem = SystemFileSystem,
    yaml: Yaml = Yaml.default,
): T? {
    if (!fs.exists(this)) return null
    return fs.source(this).buffered().readString().let {
        yaml.decodeFromString<T>(it)
    }
}

fun Path.readYamlNode(
    fs: FileSystem = SystemFileSystem,
    yaml: Yaml = Yaml.default,
): YamlNode? {
    if (!fs.exists(this)) return null
    return fs.source(this).buffered().readString().let {
        yaml.parseToYamlNode(it)
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T: Any> Path.readToml(
    fs: FileSystem = SystemFileSystem,
    toml: Toml = Toml,
): T? {
    if (!fs.exists(this)) return null
    return fs.source(this).buffered().readString().let { text ->
        toml.decodeFromString<T>(text)
    }
}

fun FileSystem.deleteRecursively(path: Path) {
    if (isDirectory(path)) {
        val contents = list(path)
        for (entry in contents)
            deleteRecursively(entry)
    }
    // Delete the current file or empty directory
    delete(path, mustExist = false)
}

fun FileSystem.isDirectory(path: Path): Boolean =
    metadataOrNull(path)?.isDirectory == true

fun FileSystem.calculateDirectorySize(path: Path): Long =
    if (!exists(path) || !isDirectory(path)) {
        0L
    } else {
        list(path).sumOf { file ->
            when {
                isDirectory(file) -> calculateDirectorySize(file)
                else -> metadataOrNull(file)?.size ?: 0L
            }
        }
    }

fun FileSystem.walkFiles(root: Path): Sequence<Path> = sequence {
    val metadata = metadataOrNull(root) ?: return@sequence

    if (metadata.isRegularFile) {
        yield(root)
    } else if (metadata.isDirectory) {
        list(root).forEach { path ->
            yieldAll(walkFiles(path))
        }
    }
}