@file:Suppress("UnstableApiUsage")

package kastle

import org.jetbrains.plugins.template.repository.AIResponseGenerator
import org.jetbrains.plugins.template.repository.ChatMessageFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Service(Service.Level.PROJECT)
class BackendChatRepositoryModel {
    companion object {
        fun getInstance(project: Project): BackendChatRepositoryModel {
            return project.getService(BackendChatRepositoryModel::class.java)
        }
    }

    private val chatMessageFactory = ChatMessageFactory("AI Buddy", "Super Engineer")
    private val aiResponseGenerator = AIResponseGenerator()
    private val _messages = MutableStateFlow(
        listOf(
            chatMessageFactory.createAIMessage(
                content = "Hello! I'm your AI Buddy. I'm here to help and chat with you about anything you'd like to discuss. How are you doing today?",
                timestamp = LocalDateTime.now().minusMinutes(30),
            ),
            chatMessageFactory.createAIMessage(
                content = "Feel free to ask me questions, share your thoughts, or just have a casual conversation. I'm designed to provide helpful and engaging responses!",
                timestamp = LocalDateTime.now().minusMinutes(25),
            ),
            chatMessageFactory.createAIMessage(
                content = "I can help with a wide variety of topics - from coding and technical questions to creative writing, analysis, math problems, or just friendly chat. What interests you?",
                timestamp = LocalDateTime.now().minusMinutes(20),
            )
        )
    )

    fun getMessagesFlow(): Flow<List<ChatMessageDto>> {
        return _messages.map { messagesList -> messagesList.map(ChatMessage::toChatMessageDto) }
    }

    suspend fun sendMessage(messageContent: String) {
        withContext(Dispatchers.IO) {
            try {
                // Emits the user message to a chat list
                _messages.value += chatMessageFactory.createUserMessage(messageContent)

                // Simulate AI responding
                simulateAIResponse(messageContent)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // In case the message sending is canceled before a response is generated,
                    // we remove a loading placeholder message
                    _messages.value = _messages.value.filter { !it.isAIThinkingMessage() }

                    throw e

                }

                e.printStackTrace()
            }
        }
    }

    private suspend fun simulateAIResponse(userMessage: String) {
        val aiThinkingMessage = chatMessageFactory
            .createAIThinkingMessage("Hm, let me think about that...")
        _messages.value += aiThinkingMessage

        // Simulate delay for the AI response
        delay(2000 + (500..2000).random().toLong()) // Random delay between 2.5-4 seconds

        val responseMessage =
            chatMessageFactory.createAIMessage(content = aiResponseGenerator.generateAIResponse(userMessage))

        _messages.value = _messages.value
            .map { message -> if (message.id == aiThinkingMessage.id) responseMessage else message }
    }
}