package com.roninai.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "remote_session_id")
    val remoteSessionId: String? = null,

    @ColumnInfo(name = "title")
    val title: String = "New Session",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = Instant.now().toEpochMilli(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = Instant.now().toEpochMilli(),

    @ColumnInfo(name = "workspace_path")
    val workspacePath: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "system_prompt_override")
    val systemPromptOverride: String? = null,

    @ColumnInfo(name = "model_name")
    val modelName: String = "mimo-2.5-free",

    @ColumnInfo(name = "total_messages")
    val totalMessages: Int = 0,

    @ColumnInfo(name = "is_voice_session")
    val isVoiceSession: Boolean = false
)
