package com.roninai.app.session

import android.content.Context
import com.roninai.app.data.dao.MessageDao
import com.roninai.app.data.dao.SessionDao
import com.roninai.app.data.entity.MessageEntity
import com.roninai.app.data.entity.MessageRole
import com.roninai.app.data.entity.MessageType
import com.roninai.app.data.entity.SessionEntity
import com.roninai.app.engine.ChatMessage
import com.roninai.app.engine.ChatResponse
import com.roninai.app.engine.JarvisApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SessionState(
    val currentSessionId: Long? = null,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val streamingContent: String = ""
)

class SessionManager(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var apiClient: JarvisApiClient? = null

    fun setApiClient(client: JarvisApiClient) {
        apiClient = client
    }

    suspend fun createNewSession(
        title: String = "New Session",
        workspacePath: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val session = SessionEntity(
            title = title,
            workspacePath = workspacePath
        )
        val id = sessionDao.insertSession(session)
        _state.value = _state.value.copy(currentSessionId = id)
        id
    }

    suspend fun switchSession(sessionId: Long) {
        _state.value = _state.value.copy(
            currentSessionId = sessionId,
            error = null,
            streamingContent = ""
        )
    }

    suspend fun sendMessage(
        content: String,
        sessionId: Long,
        imageBase64: String? = null,
        workDir: String? = null,
        systemPrompt: String? = null,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(isProcessing = true, error = null)

            val userMessage = MessageEntity(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                imageBase64 = imageBase64,
                messageType = if (imageBase64 != null) MessageType.IMAGE else MessageType.TEXT
            )
            val userMsgId = messageDao.insertMessage(userMessage)
            sessionDao.touchSession(sessionId)

            val assistantMessage = MessageEntity(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true
            )
            val assistantMsgId = messageDao.insertMessage(assistantMessage)

            val conversationHistory = buildConversationHistory(sessionId)
            val chatMessages = conversationHistory.map {
                ChatMessage(
                    role = it.role.name.lowercase(),
                    content = it.content,
                    imageBase64 = it.imageBase64
                )
            }

            val session = sessionDao.getSessionById(sessionId)
            val client = apiClient ?: JarvisApiClient(
                "http://127.0.0.1:8080"
            )

            val result = client.sendMessage(
                messages = chatMessages,
                model = session?.modelName ?: "mimo-2.5-free",
                systemPrompt = systemPrompt ?: buildDefaultSystemPrompt(session),
                workDir = workDir ?: session?.workspacePath
            )

            result.fold(
                onSuccess = { response ->
                    val finalMessage = MessageEntity(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = response.content,
                        messageType = MessageType.TEXT,
                        cached = response.cached,
                        tokenCount = response.tokenCount
                    )
                    messageDao.updateMessage(finalMessage.copy(id = assistantMsgId))
                    sessionDao.touchSession(sessionId)

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        streamingContent = ""
                    )

                    val savedMessage = messageDao.getMessageById(assistantMsgId)
                    Result.success(savedMessage ?: finalMessage.copy(id = assistantMsgId))
                },
                onFailure = { error ->
                    val errorMessage = MessageEntity(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = "Error: ${error.message}",
                        messageType = MessageType.TEXT,
                        errorMessage = error.message
                    )
                    messageDao.updateMessage(errorMessage.copy(id = assistantMsgId))

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        error = error.message,
                        streamingContent = ""
                    )

                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isProcessing = false,
                error = e.message
            )
            Result.failure(e)
        }
    }

    suspend fun sendStreamingMessage(
        content: String,
        sessionId: Long,
        imageBase64: String? = null,
        workDir: String? = null,
        systemPrompt: String? = null,
        onToken: (String) -> Unit
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(isProcessing = true, error = null)

            val userMessage = MessageEntity(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                imageBase64 = imageBase64,
                messageType = if (imageBase64 != null) MessageType.IMAGE else MessageType.TEXT
            )
            messageDao.insertMessage(userMessage)
            sessionDao.touchSession(sessionId)

            val assistantMessage = MessageEntity(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true
            )
            val assistantMsgId = messageDao.insertMessage(assistantMessage)

            val conversationHistory = buildConversationHistory(sessionId)
            val chatMessages = conversationHistory.map {
                ChatMessage(
                    role = it.role.name.lowercase(),
                    content = it.content,
                    imageBase64 = it.imageBase64
                )
            }

            val session = sessionDao.getSessionById(sessionId)
            val client = apiClient ?: JarvisApiClient("http://127.0.0.1:8080")
            val accumulated = StringBuilder()

            client.streamChat(
                messages = chatMessages,
                model = session?.modelName ?: "mimo-2.5-free",
                systemPrompt = systemPrompt ?: buildDefaultSystemPrompt(session),
                workDir = workDir ?: session?.workspacePath
            ).collect { chunk ->
                accumulated.append(chunk.content)
                val current = accumulated.toString()
                _state.value = _state.value.copy(streamingContent = current)
                messageDao.updateMessageContent(assistantMsgId, current)
                onToken(chunk.content)
            }

            val finalContent = accumulated.toString()
            val finalMessage = MessageEntity(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = finalContent,
                messageType = MessageType.TEXT,
                isStreaming = false
            )
            messageDao.updateMessage(finalMessage.copy(id = assistantMsgId))

            _state.value = _state.value.copy(
                isProcessing = false,
                streamingContent = ""
            )

            val savedMessage = messageDao.getMessageById(assistantMsgId)
            Result.success(savedMessage ?: finalMessage.copy(id = assistantMsgId))
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isProcessing = false,
                error = e.message,
                streamingContent = ""
            )
            Result.failure(e)
        }
    }

    suspend fun getMessages(sessionId: Long): List<MessageEntity> =
        messageDao.getMessagesBySessionList(sessionId)

    suspend fun deleteSession(sessionId: Long) {
        messageDao.deleteMessagesBySession(sessionId)
        sessionDao.deleteSessionById(sessionId)
        if (_state.value.currentSessionId == sessionId) {
            _state.value = _state.value.copy(currentSessionId = null)
        }
    }

    suspend fun renameSession(sessionId: Long, newTitle: String) {
        sessionDao.updateTitle(sessionId, newTitle)
    }

    suspend fun setWorkspaceForSession(sessionId: Long, path: String) {
        sessionDao.updateWorkspacePath(sessionId, path)
    }

    private suspend fun buildConversationHistory(sessionId: Long): List<MessageEntity> {
        return messageDao.getRecentMessages(sessionId, 30)
            .filter { it.role in listOf(MessageRole.USER, MessageRole.ASSISTANT) && it.errorMessage == null }
    }

    private fun buildDefaultSystemPrompt(session: SessionEntity?): String {
        return buildString {
            append("You are RoninAI, an advanced AI assistant running on-device via OpenCode. ")
            append("You are capable of code analysis, file operations, refactoring, and autonomous agent tasks. ")
            append("You are concise, precise, and action-oriented. ")
            append("When asked to perform file operations, prefer using the provided workspace directory. ")
            append("Respond in the same language the user writes in. ")

            session?.workspacePath?.let {
                append("\nCurrent workspace: $it")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
