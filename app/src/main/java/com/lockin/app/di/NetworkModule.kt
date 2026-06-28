/*
 * File: app/src/main/java/com/lockin/app/di/NetworkModule.kt
 * Purpose: Dagger Hilt Module providing networking dependencies (Retrofit, OkHttp, APIs).
 */

package com.lockin.app.di

import com.lockin.app.BuildConfig
import com.lockin.app.core.data.remote.api.AuthApi
import com.lockin.app.core.data.remote.api.SessionApi
import com.lockin.app.core.data.remote.api.UserApi
import com.lockin.app.core.data.remote.api.WalletApi
import com.lockin.app.core.data.remote.interceptor.AuthInterceptor
import com.lockin.app.core.data.remote.interceptor.LoggingInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt Dependency Injection module for network components.
 * Configures request timeout lengths, attaches interceptors, setups SSL pinning, and provides Retrofit APIs.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides a configured OkHttpClient instance.
     * Why: Centralizes request settings, connects auth and logging interceptors, and sets up certificate pinning.
     *
     * @param authInterceptor Custom interceptor injecting JWT bearer headers.
     * @param loggingInterceptor Custom interceptor logging HTTP packages (debug only).
     * @return Fully configured OkHttpClient singleton.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        // Enable certificate pinning in release builds to protect from Man-in-the-Middle attacks (Phase 19.9)
        if (!BuildConfig.DEBUG) {
            val baseUri = try {
                URI(BuildConfig.BASE_URL)
            } catch (e: Exception) {
                null
            }
            val host = baseUri?.host ?: "api.lockin.app"

            // Configure certificate pins. A fallback/placeholder hash is provided here.
            // Replace with actual production server public key hashes before live launch.
            val certificatePinner = CertificatePinner.Builder()
                .add(host, "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .add("api.lockin.app", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .build()

            builder.certificatePinner(certificatePinner)
        }

        return builder.build()
    }

    /**
     * Provides the Retrofit builder instance using OkHttpClient and Gson.
     * Why: Handles base URL routing, JSON serialization/deserialization, and builds service interfaces.
     *
     * @param okHttpClient The client executing HTTP queries.
     * @return Fully initialized Retrofit singleton.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provides SessionApi Retrofit service instance.
     * Why: Allows injecting session REST calls into data source/repository classes.
     *
     * @param retrofit Central Retrofit instance.
     * @return Ready-to-use SessionApi interface.
     */
    @Provides
    @Singleton
    fun provideSessionApi(
        retrofit: Retrofit
    ): SessionApi {
        return retrofit.create(SessionApi::class.java)
    }

    /**
     * Provides WalletApi Retrofit service instance.
     * Why: Allows injecting wallet REST calls into data source/repository classes.
     *
     * @param retrofit Central Retrofit instance.
     * @return Ready-to-use WalletApi interface.
     */
    @Provides
    @Singleton
    fun provideWalletApi(
        retrofit: Retrofit
    ): WalletApi {
        return retrofit.create(WalletApi::class.java)
    }

    /**
     * Provides UserApi Retrofit service instance.
     * Why: Allows injecting user registration and push token uploads into repositories.
     *
     * @param retrofit Central Retrofit instance.
     * @return Ready-to-use UserApi interface.
     */
    @Provides
    @Singleton
    fun provideUserApi(
        retrofit: Retrofit
    ): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    /**
     * Provides AuthApi Retrofit service instance.
     * Why: Allows injecting authentication APIs for Google Sign-in verify flows.
     *
     * @param retrofit Central Retrofit instance.
     * @return Ready-to-use AuthApi interface.
     */
    @Provides
    @Singleton
    fun provideAuthApi(
        retrofit: Retrofit
    ): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }
}
