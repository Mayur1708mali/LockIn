/*
 * File: com/lockin/app/di/SecurityModule.kt
 * Purpose: Dependency Injection module for security components.
 * Provides singleton instances of EncryptedPrefsManager, BiometricHelper, and RootDetectionManager.
 */

package com.lockin.app.di

import android.content.Context
import com.lockin.app.core.security.BiometricHelper
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.core.security.RootDetectionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for security-related helpers and managers.
 * Installs bindings in the SingletonComponent.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * Provides the singleton instance of EncryptedPrefsManager.
     */
    @Provides
    @Singleton
    fun provideEncryptedPrefsManager(
        @ApplicationContext context: Context
    ): EncryptedPrefsManager {
        return EncryptedPrefsManager(context)
    }

    /**
     * Provides the singleton instance of BiometricHelper.
     */
    @Provides
    @Singleton
    fun provideBiometricHelper(
        @ApplicationContext context: Context
    ): BiometricHelper {
        return BiometricHelper(context)
    }

    /**
     * Provides the singleton instance of RootDetectionManager.
     */
    @Provides
    @Singleton
    fun provideRootDetectionManager(
        @ApplicationContext context: Context
    ): RootDetectionManager {
        return RootDetectionManager(context)
    }
}
