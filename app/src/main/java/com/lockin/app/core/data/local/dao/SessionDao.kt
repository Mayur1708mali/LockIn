package com.lockin.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lockin.app.core.data.local.entity.SessionEntity
import com.lockin.app.core.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for focus sessions.
 */
@Dao
interface SessionDao {

    /**
     * Inserts a new session record. If it already exists, replaces it.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    /**
     * Inserts multiple session records. If any already exist, replaces them.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>): List<Long>

    /**
     * Updates an existing session record with new lifecycle information.
     */
    @Update
    suspend fun updateSession(session: SessionEntity): Int

    /**
     * Fetches a session by its unique ID.
     */
    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    /**
     * Streams all sessions ordered by start time descending.
     */
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<SessionEntity>>

    /**
     * Streams the currently active session, if one exists.
     */
    @Query("SELECT * FROM sessions WHERE status = :activeStatus LIMIT 1")
    fun getActiveSessionFlow(activeStatus: SessionStatus = SessionStatus.ACTIVE): Flow<SessionEntity?>

    /**
     * Non-reactive fetch of the active session (one-shot check).
     */
    @Query("SELECT * FROM sessions WHERE status = :activeStatus LIMIT 1")
    suspend fun getActiveSession(activeStatus: SessionStatus = SessionStatus.ACTIVE): SessionEntity?
}
