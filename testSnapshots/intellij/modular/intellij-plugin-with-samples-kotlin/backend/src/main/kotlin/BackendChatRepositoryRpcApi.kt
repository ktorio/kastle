package com.acme

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class BackendChatRepositoryRpcApi : ChatRepositoryRpcApi {
    override suspend fun getMessagesFlow(projectId: ProjectId): Flow<List<ChatMessageDto>> {
        val backendProject = projectId.findProjectOrNull() ?: return emptyFlow()
        return BackendChatRepositoryModel.getInstance(backendProject).getMessagesFlow()
    }

    override suspend fun sendMessage(
        projectId: ProjectId,
        messageContent: String
    ) {
        val backendProject = projectId.findProjectOrNull() ?: return
        return BackendChatRepositoryModel.getInstance(backendProject).sendMessage(messageContent)
    }
}
