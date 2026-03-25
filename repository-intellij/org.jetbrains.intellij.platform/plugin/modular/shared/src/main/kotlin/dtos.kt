package org.jetbrains.plugins.template

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class ChatMessageDto(
    val id: String,
    val content: String,
    val author: String,
    val isMyMessage: Boolean,
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime,
    val type: ChatMessage.ChatMessageType
)

fun ChatMessageDto.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        content = content,
        author = author,
        isMyMessage = isMyMessage,
        timestamp = timestamp,
        type = type
    )
}

fun ChatMessage.toChatMessageDto(): ChatMessageDto {
    return ChatMessageDto(
        id = id,
        content = content,
        author = author,
        isMyMessage = isMyMessage,
        timestamp = timestamp,
        type = type
    )
}