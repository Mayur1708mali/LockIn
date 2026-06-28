/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/api/AuthApi.kt
 * Purpose: Retrofit interface for remote authentication calls.
 */

package com.lockin.app.core.data.remote.api

import com.lockin.app.core.data.remote.dto.GoogleSignInRequest
import com.lockin.app.core.data.remote.dto.GoogleSignInResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Service endpoint interface representing authentication actions on the server.
 */
interface AuthApi {

    /**
     * Verifies the Google ID token and returns our backend's signed JWT token.
     * Why: Validates the user's identity securely and establishes the app session.
     *
     * @param request GoogleSignInRequest containing the raw ID token.
     * @return GoogleSignInResponse containing the JWT token and user profile details.
     */
    @POST("auth/google")
    suspend fun signInWithGoogle(
        @Body request: GoogleSignInRequest
    ): GoogleSignInResponse
}
