/*
 * File: app/src/main/java/com/lockin/app/core/data/remote/api/WalletApi.kt
 * Purpose: Retrofit interface for remote wallet balance and config management.
 */

package com.lockin.app.core.data.remote.api

import com.lockin.app.core.data.remote.dto.AutoTopUpRequest
import com.lockin.app.core.data.remote.dto.DepositRequest
import com.lockin.app.core.data.remote.dto.WalletDto
import com.lockin.app.core.data.remote.dto.WalletTransactionDto
import com.lockin.app.core.data.remote.dto.WithdrawRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Service endpoint interface representing wallet activities on the server.
 * Commented every function per Code Generation Rules to explain what it does and why.
 */
interface WalletApi {

    /**
     * Fetches current wallet status and settings for the authenticated user.
     * Why: Syncs local balances (available, held) and configurations with server ground truth.
     *
     * @return The current wallet model details.
     */
    @GET("wallet")
    suspend fun getWallet(): WalletDto

    /**
     * Reports a manual Razorpay deposit to the server for verification.
     * Why: Direct server verifies Razorpay payment status via transaction tokens to avoid client spoofing.
     *
     * @param request Token and transaction details.
     * @return Updated wallet details.
     */
    @POST("wallet/deposit")
    suspend fun deposit(
        @Body request: DepositRequest
    ): WalletDto

    /**
     * Requests withdrawal of available funds back to the user's payment method.
     * Why: Signals server to process standard withdrawal requests (min ₹50).
     *
     * @param request Amount in paise.
     * @return Updated wallet details.
     */
    @POST("wallet/withdraw")
    suspend fun withdraw(
        @Body request: WithdrawRequest
    ): WalletDto

    /**
     * Executes auto top-up payment silent charge server-side.
     * Why: Securely charges the saved payment token in the cloud instead of direct SDK trigger.
     *
     * @param request Configured amount to charge.
     * @return Updated wallet details.
     */
    @POST("wallet/auto-topup")
    suspend fun autoTopUp(
        @Body request: AutoTopUpRequest
    ): WalletDto

    /**
     * Retrieves all wallet transactions for the authenticated user from the server.
     * Why: Used to sync remote transaction history to local database on sign in.
     *
     * @return List of all user wallet transaction data transfer objects.
     */
    @GET("wallet/transactions")
    suspend fun getTransactions(): List<WalletTransactionDto>
}
