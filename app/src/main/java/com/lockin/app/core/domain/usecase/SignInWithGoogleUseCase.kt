/*
 * File: app/src/main/java/com/lockin/app/core/domain/usecase/SignInWithGoogleUseCase.kt
 * Purpose: Use case to execute Google Authentication backend verification.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.remote.api.AuthApi
import com.lockin.app.core.data.remote.api.SessionApi
import com.lockin.app.core.data.remote.api.WalletApi
import com.lockin.app.core.data.remote.dto.GoogleSignInRequest
import com.lockin.app.core.data.remote.dto.GoogleSignInResponse
import com.lockin.app.core.domain.model.AutoTopUpConfig
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import com.lockin.app.core.security.EncryptedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Coordinates verifying Google ID tokens with the backend, storing JWT tokens / profiles locally,
 * and fetching the user's remote wallet, transactions, and sessions state to synchronize the local Room database.
 * Why: Encapsulates user login/registration operations and initial user data synchronization under a clean use case definition.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authApi: AuthApi,
    private val walletApi: WalletApi,
    private val sessionApi: SessionApi,
    private val walletRepository: WalletRepository,
    private val sessionRepository: SessionRepository,
    private val encryptedPrefsManager: EncryptedPrefsManager
) {

    /**
     * Sends the Google ID token to the server, saves the returned JWT, and fetches/syncs remote wallet, sessions, and transactions data.
     * Why: Authenticates the user, sets up locally encrypted profile credentials, and pulls down all database state to sync locally.
     *
     * @param idToken Google ID token fetched from Credential Manager.
     * @return Result wrapping the server's GoogleSignInResponse.
     */
    suspend operator fun invoke(idToken: String): Result<GoogleSignInResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = GoogleSignInRequest(idToken)
                val response = authApi.signInWithGoogle(request)

                // Save JWT under key "auth_jwt"
                encryptedPrefsManager.saveAuthJwt(response.jwt)

                // Save user profile details
                encryptedPrefsManager.saveUserId(response.userId)
                encryptedPrefsManager.saveGoogleDisplayName(response.displayName)
                encryptedPrefsManager.saveGoogleEmail(response.email)

                // Fetch remote wallet state and sync it to the local Room database
                try {
                    val walletDto = walletApi.getWallet()
                    val wallet = Wallet(
                        userId = walletDto.userId,
                        availableBalance = walletDto.availableBalance,
                        heldBalance = walletDto.heldBalance,
                        totalDeposited = walletDto.totalDeposited,
                        totalPenaltiesPaid = walletDto.totalPenaltiesPaid,
                        autoTopUpEnabled = walletDto.autoTopUpEnabled,
                        autoTopUpThresholdPaise = walletDto.autoTopUpThresholdPaise,
                        autoTopUpAmountPaise = walletDto.autoTopUpAmountPaise,
                        lastUpdated = walletDto.lastUpdated
                    )
                    walletRepository.insertOrUpdateWallet(wallet)
                    
                    // Sync auto top-up config to encrypted preferences (Task 4.13/5.1)
                    encryptedPrefsManager.saveAutoTopUpConfig(
                        AutoTopUpConfig(
                            autoTopUpEnabled = walletDto.autoTopUpEnabled,
                            autoTopUpThresholdPaise = walletDto.autoTopUpThresholdPaise,
                            autoTopUpAmountPaise = walletDto.autoTopUpAmountPaise,
                            savedPaymentMethodLabel = if (encryptedPrefsManager.getToken() != null) "Razorpay Saved Instrument" else null
                        )
                    )
                    Timber.d("Successfully synced remote wallet data to local DB and encrypted preferences on sign-in.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch/sync wallet data from backend on sign-in.")
                }

                // Fetch and sync remote sessions state to local Room database
                try {
                    val sessionDtos = sessionApi.getSessions()
                    val sessions = sessionDtos.map { dto ->
                        Session(
                            sessionId = dto.sessionId,
                            userId = dto.userId,
                            status = SessionStatus.valueOf(dto.status.uppercase()),
                            startTime = dto.startTime,
                            targetEndTime = dto.targetEndTime,
                            actualEndTime = dto.actualEndTime,
                            penaltyAmount = dto.penaltyAmount,
                            currency = dto.currency,
                            walletTxHoldId = dto.walletTxHoldId,
                            allowlistVersion = dto.allowlistVersion,
                            platform = dto.platform
                        )
                    }
                    sessionRepository.insertSessions(sessions)
                    Timber.d("Successfully synced remote sessions (${sessions.size}) to local DB on sign-in.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch/sync sessions from backend on sign-in.")
                }

                // Fetch and sync remote wallet transactions state to local Room database
                try {
                    val txDtos = walletApi.getTransactions()
                    val transactions = txDtos.map { dto ->
                        WalletTransaction(
                            txId = dto.txId,
                            userId = dto.userId,
                            type = TransactionType.valueOf(dto.type.uppercase()),
                            amount = dto.amount,
                            direction = dto.direction,
                            sessionId = dto.sessionId,
                            description = dto.description,
                            timestamp = dto.timestamp
                        )
                    }
                    walletRepository.insertTransactions(transactions)
                    Timber.d("Successfully synced remote transactions (${transactions.size}) to local DB on sign-in.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch/sync transactions from backend on sign-in.")
                }

                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
