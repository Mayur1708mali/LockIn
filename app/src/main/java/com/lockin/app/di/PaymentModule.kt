/*
 * File: C:/Users/mayur/AndroidStudioProjects/LockIn/app/src/main/java/com/lockin/app/di/PaymentModule.kt
 * Purpose: Dependency Injection module for payment infrastructure.
 * Provides a singleton instance of RazorpayManager to coordinate with in-app checkouts.
 */

package com.lockin.app.di

import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that exposes Razorpay-related payment service bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentModule {

    /**
     * Provides a singleton instance of RazorpayManager.
     *
     * @param encryptedPrefsManager The app's secure preference store.
     * @return The singleton RazorpayManager instance.
     */
    @Provides
    @Singleton
    fun provideRazorpayManager(
        encryptedPrefsManager: EncryptedPrefsManager
    ): RazorpayManager {
        return RazorpayManager(encryptedPrefsManager)
    }
}
