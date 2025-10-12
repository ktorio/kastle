package org.jetbrains.kastle.server

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondOutputStream
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.Buffer
import kotlinx.io.asSink
import org.jetbrains.kastle.SourceFileEntry
import org.jetbrains.kastle.io.writeJsonString
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(InternalAPI::class)
fun ByteWriteChannel.writeJsonString(buffer: Buffer) =
    writeBuffer.writeJsonString(buffer)

/**
 * Respond with the generated project as a ZIP stream.
 */
suspend fun ApplicationCall.respondProjectDownload(result: Flow<SourceFileEntry>) {
    respondOutputStream(ContentType.Application.Zip) {
        ZipOutputStream(this).use { zip ->
            val outputSink = zip.asSink()
            result.flowOn(Dispatchers.IO).collect { (path, contents) ->
                val entry = ZipEntry(path)
                val source = contents()
                zip.putNextEntry(entry)
                outputSink.write(source, source.size)
                zip.closeEntry()
            }
        }
    }
}