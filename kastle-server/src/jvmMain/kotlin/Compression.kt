package org.jetbrains.kastle.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.compression.zstd.zstd

private const val MINIMUM_COMPRESSED_BYTES_SIZE = 1024L

fun Application.compression() {
    install(Compression) {
        gzip {
            priority = 0.9
            minimumSize(MINIMUM_COMPRESSED_BYTES_SIZE)
        }
        deflate {
            priority = 0.8
            minimumSize(MINIMUM_COMPRESSED_BYTES_SIZE)
        }

        excludeContentType(ContentType.Application.Zip)
        excludeContentType(ContentType.Application.OctetStream)
    }
}
