/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/api/SessionApi.kt
 * Purpose: Retrofit interface for remote focus session operations.
 */

package com.lockin.app.core.data.remote.api

import com.lockin.app.core.data.remote.dto.HeartbeatRequest
import com.lockin.app.core.data.remote.dto.SessionCreateRequest
import com.lockin.app.core.data.remote.dto.SessionDto
import com.lockin.app.core.data.remote.dto.SessionUpdateRequest
import com.lockin.app.core.data.remote.dto.StatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Service endpoint interface representing focus session actions on the server.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
interface SessionApi {

    /**
     * Creates a new focus session on the server.
     * Why: Confirms session initialization, deducts penalty from wallet, and starts backend monitoring.
     *
     * @param request Payload containing start times and penalty details.
     * @return The created session state.
     */
    @POST("sessions")
    suspend fun createSession(
        @Body request: SessionCreateRequest
    ): SessionDto

    /**
     * Updates an active session's state on the server (COMPLETED/BROKEN).
     * Why: Tells the server to resolve the held balance depending on if the user succeeded or failed.
     *
     * @param sessionId Unique session identifier.
     * @param request Payload specifying final status and timestamp.
     * @return The updated session state.
     */
    @PATCH("sessions/{id}")
    suspend fun updateSession(
        @Path("id") sessionId: String,
        @Body request: SessionUpdateRequest
    ): SessionDto

    /**
     * Logs a session heartbeat/event to the server.
     * Why: Provides a constant proof of active app lock and registers anomalies (like VPN gaps).
     *
     * @param sessionId Unique session identifier.
     * @param request Heartbeat event timestamp and metadata.
     * @return Confirmation status response.
     */
    @POST("sessions/{id}/heartbeat")
    suspend fun heartbeat(
        @Path("id") sessionId: String,
        @Body request: HeartbeatRequest
    ): StatusResponse

    /**
     * Retrieves session details from the server by ID.
     * Why: Synchronizes session details on restore or reconnection.
     *
     * @param sessionId Unique session identifier.
     * @return The fetched session details.
     */
    @GET("sessions/{id}")
    suspend fun getSession(
        @Path("id") sessionId: String
    ): SessionDto

    /**
     * Retrieves all sessions for the authenticated user from the server.
     * Why: Used to sync remote session history to local database on sign in.
     *
     * @return List of all user session data transfer objects.
     */
    @GET("sessions")
    suspend fun getSessions(): List<SessionDto>
}
