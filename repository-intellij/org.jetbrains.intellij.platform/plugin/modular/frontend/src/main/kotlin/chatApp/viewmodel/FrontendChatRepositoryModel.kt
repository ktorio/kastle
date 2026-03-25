@file:Suppress("UnstableApiUsage")

package org.jetbrains.plugins.template.chatApp.viewmodel

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.template.ChatMessage
import org.jetbrains.plugins.template.ChatRepositoryRpcApi
import org.jetbrains.plugins.template.toChatMessage

@Service(Level.PROJECT)
class FrontendChatRepositoryModel(
    private val project: Project,
    coroutineScope: CoroutineScope
) : ChatRepositoryApi {
    companion object {
        fun getInstance(project: Project): FrontendChatRepositoryModel {
            return project.getService(FrontendChatRepositoryModel::class.java)
        }
    }

    override val messagesFlow: StateFlow<List<ChatMessage>> = flow {
        durable {
            ChatRepositoryRpcApi.getInstanceAsync().getMessagesFlow(project.projectId()).collect { valueFromBackend ->
                val mappedValue = valueFromBackend.map { messageDto -> messageDto.toChatMessage() }
                emit(mappedValue)
            }
        }
    }.stateIn(coroutineScope, initialValue = emptyList(), started = SharingStarted.Lazily)

    override suspend fun sendMessage(messageContent: String) {
        ChatRepositoryRpcApi.getInstanceAsync().sendMessage(project.projectId(), messageContent)
    }
}