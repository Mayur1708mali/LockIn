/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/api/UserApi.kt
 * Purpose: Retrofit interface for remote device registration and user accounts.
 */

package com.lockin.app.core.data.remote.api

import com.lockin.app.core.data.remote.dto.FcmTokenRequest
import com.lockin.app.core.data.remote.dto.RegisterRequest
import com.lockin.app.core.data.remote.dto.RegisterResponse
import com.lockin.app.core.data.remote.dto.StatusResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

/**
 * Service endpoint interface representing user and device settings actions on the server.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
interface UserApi {

    /**
     * Registers the current device with the backend system.
     * Why: Establishes unique user identity on startup and yields initial JWT access tokens.
     *
     * @param request Device identifier and platform type.
     * @return Unique user ID and authorization token response.
     */
    @POST("users/register")
    suspend fun registerDevice(
        @Body request: RegisterRequest
    ): RegisterResponse

    /**
     * Uploads/updates the Firebase Cloud Messaging registration token.
     * Why: Syncs local device messaging token to enable targeting specific device push notifications.
     *
     * @param request Payload containing the device's FCM token.
     * @return Confirmation status response.
     */
    @POST("users/fcm-token")
    suspend fun saveFcmToken(
        @Body request: FcmTokenRequest
    ): StatusResponse

    /**
     * Deletes the user account from the server.
     * Why: Supports data deletion and privacy compliance, clearing user state.
     *
     * @return Confirmation status response.
     */
    @DELETE("users/account")
    suspend fun deleteAccount(): StatusResponse
}
