package com.roninai.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roninai.app.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE is_active = 1 ORDER BY updated_at DESC")
    fun getActiveSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSessionById(sessionId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE remote_session_id = :remoteId")
    suspend fun getSessionByRemoteId(remoteId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("UPDATE sessions SET updated_at = :timestamp WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET title = :title, updated_at = :timestamp WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: Long, title: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET workspace_path = :path, updated_at = :timestamp WHERE id = :sessionId")
    suspend fun updateWorkspacePath(sessionId: Long, path: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET total_messages = total_messages + 1, updated_at = :timestamp WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getSessionCount(): Int
}
