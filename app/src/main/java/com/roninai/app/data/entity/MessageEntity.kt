package com.roninai.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["created_at"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "role")
    val role: MessageRole,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "message_type")
    val messageType: MessageType = MessageType.TEXT,

    @ColumnInfo(name = "image_base64")
    val imageBase64: String? = null,

    @ColumnInfo(name = "tool_name")
    val toolName: String? = null,

    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String? = null,

    @ColumnInfo(name = "is_streaming")
    val isStreaming: Boolean = false,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "token_count")
    val tokenCount: Int = 0,

    @ColumnInfo(name = "cached")
    val cached: Boolean = false,

    @ColumnInfo(name = "execution_time_ms")
    val executionTimeMs: Long = 0
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL_CALL,
    TOOL_RESULT
}

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE,
    TOOL_CALL,
    TOOL_RESULT,
    DIFF,
    TERMINAL_OUTPUT,
    FILE_OPERATION
}
