/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/dto/AuthDto.kt
 * Purpose: Data Transfer Objects (DTOs) for authentication remote API calls.
 */

package com.lockin.app.core.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request payload for Google Sign-In authentication.
 * Why: Packages the Google ID token obtained from Credential Manager to verify on the backend.
 */
data class GoogleSignInRequest(
    @SerializedName("idToken") val idToken: String
)

/**
 * Response payload received after successful Google Sign-In.
 * Why: Returns our backend's signed JWT along with user profile credentials.
 */
data class GoogleSignInResponse(
    @SerializedName("jwt") val jwt: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("email") val email: String,
    @SerializedName("displayName") val displayName: String
)
