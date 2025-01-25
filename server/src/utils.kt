package org.jetbrains.kastle

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.io.Buffer
import org.jetbrains.kastle.io.writeJsonString

@OptIn(InternalAPI::class)
fun ByteWriteChannel.writeJsonString(buffer: Buffer) =
    writeBuffer.writeJsonString(buffer)