/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/interceptor/AuthInterceptor.kt
 * Purpose: OkHttp Interceptor that appends JWT authentication headers to outgoing REST calls.
 */

package com.lockin.app.core.data.remote.interceptor

import com.lockin.app.core.security.EncryptedPrefsManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor implementation that appends the JWT bearer token to requests if authenticated.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) : Interceptor {

    /**
     * Intercepts outgoing HTTP requests and injects the Authorization Bearer header.
     * Why: Authenticates requests on backend endpoints without manual headers in Retrofit definitions.
     *
     * @param chain Interceptor chain representing request execution pipeline.
     * @return Execution response from downstream handlers.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = encryptedPrefsManager.getAuthToken()

        // If a valid JWT token is stored, rewrite the request headers to include it
        val authenticatedRequest = if (!token.isNullOrEmpty()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(authenticatedRequest)
    }
}
