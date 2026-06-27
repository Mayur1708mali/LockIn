/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/interceptor/LoggingInterceptor.kt
 * Purpose: OkHttp Interceptor wrapping HTTP request/response logging for debug diagnostics.
 */

package com.lockin.app.core.data.remote.interceptor

import com.lockin.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom interceptor wrapper around OkHttp's standard HttpLoggingInterceptor.
 * Only logs output when building a debug build flavor to secure user data in production.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
@Singleton
class LoggingInterceptor @Inject constructor() : Interceptor {

    // Instantiate HttpLoggingInterceptor logging HTTP headers and bodies
    private val delegate = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * Intercepts outgoing HTTP requests and responses to log payloads in debug mode.
     * Why: Provides a unified logging level constraint so raw network logs never leak in release builds.
     *
     * @param chain Interceptor chain representing request execution pipeline.
     * @return Execution response from downstream handlers.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
