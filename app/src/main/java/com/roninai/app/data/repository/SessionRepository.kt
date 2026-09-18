package com.roninai.app.data.repository

import com.roninai.app.data.dao.MessageDao
import com.roninai.app.data.dao.SessionDao
import com.roninai.app.data.entity.MessageEntity
import com.roninai.app.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SessionRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {

    fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    fun getActiveSessions(): Flow<List<SessionEntity>> = sessionDao.getActiveSessions()

    suspend fun getSessionById(id: Long): SessionEntity? = sessionDao.getSessionById(id)

    fun observeSessionById(id: Long): Flow<SessionEntity?> = sessionDao.observeSessionById(id)

    suspend fun getSessionByRemoteId(remoteId: String): SessionEntity? =
        sessionDao.getSessionByRemoteId(remoteId)

    suspend fun createSession(
        title: String = "New Session",
        workspacePath: String? = null,
        isVoiceSession: Boolean = false
    ): Long {
        val session = SessionEntity(
            title = title,
            workspacePath = workspacePath,
            isVoiceSession = isVoiceSession
        )
        return sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: SessionEntity) = sessionDao.updateSession(session)

    suspend fun deleteSession(session: SessionEntity) {
        messageDao.deleteMessagesBySession(session.id)
        sessionDao.deleteSession(session)
    }

    suspend fun deleteSessionById(id: Long) {
        messageDao.deleteMessagesBySession(id)
        sessionDao.deleteSessionById(id)
    }

    suspend fun updateSessionTitle(sessionId: Long, title: String) {
        sessionDao.updateTitle(sessionId, title)
    }

    suspend fun updateSessionWorkspace(sessionId: Long, path: String?) {
        sessionDao.updateWorkspacePath(sessionId, path)
    }

    fun getMessagesBySession(sessionId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesBySession(sessionId)

    suspend fun getMessagesBySessionList(sessionId: Long): List<MessageEntity> =
        messageDao.getMessagesBySessionList(sessionId)

    suspend fun addMessage(message: MessageEntity): Long {
        val id = messageDao.insertMessage(message)
        sessionDao.incrementMessageCount(message.sessionId)
        return id
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateMessageContent(messageId, content)
    }

    suspend fun setStreaming(messageId: Long, isStreaming: Boolean) {
        messageDao.setStreaming(messageId, isStreaming)
    }

    suspend fun getRecentMessages(sessionId: Long, limit: Int = 20): List<MessageEntity> =
        messageDao.getRecentMessages(sessionId, limit)

    suspend fun getLastAssistantMessage(sessionId: Long): MessageEntity? =
        messageDao.getLastAssistantMessage(sessionId)

    suspend fun buildConversationContext(sessionId: Long, maxMessages: Int = 30): List<Map<String, String>> {
        val messages = messageDao.getRecentMessages(sessionId, maxMessages)
        return messages
            .filter { it.role.name in listOf("USER", "ASSISTANT") }
            .map { msg ->
                mapOf(
                    "role" to msg.role.name.lowercase(),
                    "content" to msg.content
                )
            }
    }

    suspend fun getSessionCount(): Int = sessionDao.getSessionCount()

    suspend fun touchSession(sessionId: Long) = sessionDao.touchSession(sessionId)
}
