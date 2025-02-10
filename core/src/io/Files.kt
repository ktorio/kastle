package org.jetbrains.kastle.io

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink

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
    Path(toString().removePrefix(base.toString()))

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T: Any> Path.readJson(
    fs: FileSystem = SystemFileSystem,
    json: Json = Json,
): T? {
    if (!fs.exists(this)) return null
    return fs.source(this).use {
        json.decodeFromSource(it.buffered())
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
            json.encodeToSink(item, sink)
        }
    }
}

inline fun <reified T: Any> Path.readYaml(
    fs: FileSystem = SystemFileSystem,
    yaml: Yaml = Yaml.default,
): T? {
    if (!fs.exists(this)) return null
    return fs.source(this).buffered().readString().let {
        yaml.decodeFromString(it)
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