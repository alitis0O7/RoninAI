package com.roninai.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roninai.app.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun getMessagesBySession(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getMessagesBySessionList(sessionId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND role = 'ASSISTANT' ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastAssistantMessage(sessionId: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Long)

    @Query("UPDATE messages SET content = :content, is_streaming = 0 WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("UPDATE messages SET is_streaming = :streaming WHERE id = :messageId")
    suspend fun setStreaming(messageId: Long, streaming: Boolean)

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int
}
