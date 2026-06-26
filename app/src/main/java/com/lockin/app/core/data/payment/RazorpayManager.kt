/*
 * File: C:/Users/mayur/AndroidStudioProjects/LockIn/app/src/main/java/com/lockin/app/core/data/payment/RazorpayManager.kt
 * Purpose: Wrapper class for the Razorpay Android SDK.
 * Manages standard checkouts by suspending coroutines until the Activity callbacks resolve,
 * handles token recording for silent background top-ups, and mocks backend payouts/charges.
 */

package com.lockin.app.core.data.payment

import android.app.Activity
import com.lockin.app.BuildConfig
import com.lockin.app.core.security.EncryptedPrefsManager
import com.razorpay.Checkout
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Singleton manager class interfacing with Razorpay checkout functions and stubs.
 */
@Singleton
class RazorpayManager @Inject constructor(
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    // Tracks the suspended coroutine waiting for the payment response callback
    private var currentContinuation: CancellableContinuation<Result<String>>? = null

    /**
     * Launches the Razorpay checkout overlay using JSON configuration parameters.
     * Suspends execution until the user finishes or cancels the checkout flow.
     *
     * @param activity The host Activity rendering the overlay.
     * @param amountPaise The checkout value in Paise.
     * @return Result enclosing the payment reference ID string or checkout Exception.
     */
    suspend fun deposit(activity: Activity, amountPaise: Int): Result<String> = suspendCancellableCoroutine { continuation ->
        currentContinuation = continuation

        try {
            // Initialize Razorpay SDK
            val checkout = Checkout()
            
            // Set the Key ID injected via gradle build configurations (Task 1.3 / local.properties)
            checkout.setKeyID(BuildConfig.RAZORPAY_KEY_ID)

            // Setup Razorpay checkout options object
            val options = JSONObject().apply {
                put("name", "LockIn Wallet")
                put("description", "Wallet Balance Top-Up")
                put("theme.color", "#FF3B30") // Flat Accent Red #FF3B30
                put("currency", "INR")
                put("amount", amountPaise.toString()) // Amount in Paise (min 5000 = ₹50)
                
                // Set checkout preferences
                val prefill = JSONObject().apply {
                    put("email", "support@lockin.app")
                    put("contact", "9999999999")
                }
                put("prefill", prefill)

                val retry = JSONObject().apply {
                    put("enabled", true)
                    put("max_count", 2)
                }
                put("retry", retry)
            }

            // Launch Razorpay activity overlay
            checkout.open(activity, options)
            Timber.i("Razorpay Checkout launched successfully for amount: $amountPaise Paise")
        } catch (e: Exception) {
            Timber.e(e, "Fatal exception launching Razorpay checkout")
            continuation.resume(Result.failure(e))
            currentContinuation = null
        }

        // Clean references if the coroutine is cancelled
        continuation.invokeOnCancellation {
            currentContinuation = null
        }
    }

    /**
     * Invoked when the Razorpay payment callback reports success.
     * Resumes the suspended coroutine and persists the payment token for silent Auto Top-Up charges.
     *
     * @param paymentId Razorpay payment transaction reference string.
     */
    fun onPaymentSuccess(paymentId: String) {
        Timber.i("Razorpay success callback received with ID: $paymentId")
        
        // Save token securely to support future silent server-side charges (Task 14.3)
        encryptedPrefsManager.saveToken(paymentId)
        
        currentContinuation?.resume(Result.success(paymentId))
        currentContinuation = null
    }

    /**
     * Invoked when the Razorpay payment callback reports failure.
     * Resumes the suspended coroutine with an Exception description.
     *
     * @param code Error result status code.
     * @param description Error string describing payment failure reason.
     */
    fun onPaymentError(code: Int, description: String) {
        Timber.e("Razorpay failure callback received. Code: $code, Message: $description")
        currentContinuation?.resume(Result.failure(Exception("Payment failed ($code): $description")))
        currentContinuation = null
    }

    /**
     * Charges a saved payment token server-side for Auto Top-Up.
     * Communicates with the backend server (charges must never initiate from mobile client).
     *
     * @param userId The current user ID.
     * @param amountPaise The amount in Paise to charge.
     * @param paymentToken The saved Razorpay payment method token.
     * @return Result indicating if the server-side charge succeeded.
     */
    suspend fun chargeToken(userId: String, amountPaise: Int, paymentToken: String): Result<Boolean> {
        Timber.i("RazorpayManager: Initiating server-side token charge for user $userId, amount: $amountPaise Paise using token: $paymentToken")
        
        // Simulated network call to backend API (Phase 19 / 20 integration)
        delay(1500)
        return Result.success(true)
    }

    /**
     * Initiates a manual bank withdrawal request.
     * Communicates with the backend payout services.
     *
     * @param userId The current user ID.
     * @param amountPaise The amount in Paise to withdraw.
     * @return Result indicating if the withdrawal request succeeded.
     */
    suspend fun initiateWithdrawal(userId: String, amountPaise: Int): Result<Boolean> {
        Timber.i("RazorpayManager: Requesting payout withdrawal from backend for user $userId, amount: $amountPaise Paise")
        
        // Simulated network call to backend payout services (Phase 19 / 20 integration)
        delay(1500)
        return Result.success(true)
    }
}
