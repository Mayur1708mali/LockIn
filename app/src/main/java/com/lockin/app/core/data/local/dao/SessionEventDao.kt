package com.lockin.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lockin.app.core.data.local.entity.SessionEventEntity

/**
 * Data Access Object (DAO) for focus session events (append-only audit trail).
 */
@Dao
interface SessionEventDao {

    /**
     * Appends a new event to the session audit log.
     * Enforces uniqueness (aborts if a transaction or log UUID collision occurs).
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: SessionEventEntity): Long

    /**
     * Fetches all events associated with a specific session, ordered by time.
     */
    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getEventsForSession(sessionId: String): List<SessionEventEntity>

    /**
     * Retrieves all events that are currently not synchronized with the backend.
     */
    @Query("SELECT * FROM session_events WHERE isSynced = 0")
    suspend fun getUnsyncedEvents(): List<SessionEventEntity>

    /**
     * Marks a session event as synchronized with the backend.
     */
    @Query("UPDATE session_events SET isSynced = 1 WHERE eventId = :eventId")
    suspend fun markEventSynced(eventId: String): Int
}
