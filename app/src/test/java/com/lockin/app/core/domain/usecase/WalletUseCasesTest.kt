/*
 * File: app/src/test/java/com/lockin/app/core/domain/usecase/WalletUseCasesTest.kt
 * Purpose: Unit tests for all wallet-related domain use cases using MockK.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.domain.model.AutoTopUpConfig
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import com.lockin.app.core.security.EncryptedPrefsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletUseCasesTest {

    private lateinit var walletRepository: WalletRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var encryptedPrefsManager: EncryptedPrefsManager
    private lateinit var razorpayManager: RazorpayManager

    private lateinit var getWalletUseCase: GetWalletUseCase
    private lateinit var depositToWalletUseCase: DepositToWalletUseCase
    private lateinit var withdrawFromWalletUseCase: WithdrawFromWalletUseCase
    private lateinit var autoTopUpUseCase: AutoTopUpUseCase
    private lateinit var saveAutoTopUpConfigUseCase: SaveAutoTopUpConfigUseCase
    private lateinit var getAutoTopUpConfigUseCase: GetAutoTopUpConfigUseCase

    private val userId = "test_user_123"
    private val defaultWallet = Wallet(
        userId = userId,
        availableBalance = 5000, // ₹50 (below the default ₹100 threshold)
        heldBalance = 0,
        totalDeposited = 5000,
        totalPenaltiesPaid = 0,
        autoTopUpEnabled = true,
        autoTopUpThresholdPaise = 10000, // ₹100
        autoTopUpAmountPaise = 20000, // ₹200
        lastUpdated = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        walletRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        encryptedPrefsManager = mockk(relaxed = true)
        razorpayManager = mockk(relaxed = true)

        getWalletUseCase = GetWalletUseCase(walletRepository)
        depositToWalletUseCase = DepositToWalletUseCase(walletRepository)
        withdrawFromWalletUseCase = WithdrawFromWalletUseCase(walletRepository)
        autoTopUpUseCase = AutoTopUpUseCase(
            walletRepository = walletRepository,
            sessionRepository = sessionRepository,
            depositToWalletUseCase = depositToWalletUseCase,
            encryptedPrefsManager = encryptedPrefsManager,
            razorpayManager = razorpayManager
        )
        saveAutoTopUpConfigUseCase = SaveAutoTopUpConfigUseCase(encryptedPrefsManager)
        getAutoTopUpConfigUseCase = GetAutoTopUpConfigUseCase(encryptedPrefsManager)
    }

    @Test
    fun `GetWalletUseCase - retrieves user wallet stream`() = runTest {
        // Arrange
        every { walletRepository.getWalletFlow(userId) } returns flowOf(defaultWallet)

        // Act
        val result = getWalletUseCase(userId).first()

        // Assert
        assertEquals(defaultWallet, result)
        coVerify { walletRepository.getWalletFlow(userId) }
    }

    @Test
    fun `DepositToWalletUseCase - amount above minimum - deposits successfully`() = runTest {
        // Arrange
        coEvery {
            walletRepository.depositTransaction(userId, 10000, any())
        } returns true

        // Act
        val result = depositToWalletUseCase(
            userId = userId,
            amountPaise = 10000, // ₹100
            transactionType = TransactionType.DEPOSIT,
            razorpayPaymentId = "pay_123"
        )

        // Assert
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify {
            walletRepository.depositTransaction(
                userId = userId,
                amountPaise = 10000,
                transaction = any()
            )
        }
    }

    @Test
    fun `DepositToWalletUseCase - amount below minimum - returns failure`() = runTest {
        // Act
        val result = depositToWalletUseCase(
            userId = userId,
            amountPaise = 3000 // ₹30 (Min: ₹50)
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `WithdrawFromWalletUseCase - sufficient balance - withdraws successfully`() = runTest {
        // Arrange
        val walletWithBalance = defaultWallet.copy(availableBalance = 15000) // ₹150
        coEvery { walletRepository.getWallet(userId) } returns walletWithBalance
        coEvery {
            walletRepository.withdrawTransaction(userId, 5000, any())
        } returns true

        // Act
        val result = withdrawFromWalletUseCase(userId, 5000) // ₹50

        // Assert
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify {
            walletRepository.withdrawTransaction(
                userId = userId,
                amountPaise = 5000,
                transaction = any()
            )
        }
    }

    @Test
    fun `WithdrawFromWalletUseCase - insufficient balance - returns failure`() = runTest {
        // Arrange
        val walletLowBalance = defaultWallet.copy(availableBalance = 3000) // ₹30
        coEvery { walletRepository.getWallet(userId) } returns walletLowBalance

        // Act
        val result = withdrawFromWalletUseCase(userId, 5000) // ₹50

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `AutoTopUpUseCase - enabled and below threshold - triggers charge and deposits successfully`() = runTest {
        // Arrange
        coEvery { walletRepository.getWallet(userId) } returns defaultWallet
        coEvery { sessionRepository.getActiveSession() } returns null
        coEvery { walletRepository.getTransactionCountByTypeSince(TransactionType.AUTO_TOPUP, any()) } returns 0
        every { encryptedPrefsManager.getToken() } returns "razorpay_customer_token_123"
        coEvery { razorpayManager.chargeToken(userId, 20000, "razorpay_customer_token_123") } returns Result.success(true)
        coEvery { walletRepository.depositTransaction(userId, 20000, any()) } returns true

        // Act
        val result = autoTopUpUseCase(userId)

        // Assert
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify {
            razorpayManager.chargeToken(userId, 20000, "razorpay_customer_token_123")
            walletRepository.depositTransaction(userId, 20000, any())
        }
    }

    @Test
    fun `AutoTopUpUseCase - auto top-up disabled - skips top-up`() = runTest {
        // Arrange
        val disabledWallet = defaultWallet.copy(autoTopUpEnabled = false)
        coEvery { walletRepository.getWallet(userId) } returns disabledWallet

        // Act
        val result = autoTopUpUseCase(userId)

        // Assert
        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() == true)
        coVerify(exactly = 0) { razorpayManager.chargeToken(any(), any(), any()) }
    }

    @Test
    fun `AutoTopUpUseCase - balance above threshold - skips top-up`() = runTest {
        // Arrange
        val highBalanceWallet = defaultWallet.copy(availableBalance = 30000) // ₹300 (Threshold: ₹100)
        coEvery { walletRepository.getWallet(userId) } returns highBalanceWallet

        // Act
        val result = autoTopUpUseCase(userId)

        // Assert
        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() == true)
    }

    @Test
    fun `AutoTopUpUseCase - active session exists - returns failure`() = runTest {
        // Arrange
        val activeSession = Session("s1", userId, SessionStatus.ACTIVE, 0, 0, 0, 1000, allowlistVersion = 1)
        coEvery { walletRepository.getWallet(userId) } returns defaultWallet
        coEvery { sessionRepository.getActiveSession() } returns activeSession

        // Act
        val result = autoTopUpUseCase(userId)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `AutoTopUpUseCase - daily cap reached - returns failure`() = runTest {
        // Arrange
        coEvery { walletRepository.getWallet(userId) } returns defaultWallet
        coEvery { sessionRepository.getActiveSession() } returns null
        coEvery { walletRepository.getTransactionCountByTypeSince(TransactionType.AUTO_TOPUP, any()) } returns 3

        // Act
        val result = autoTopUpUseCase(userId)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `SaveAutoTopUpConfigUseCase - saves config to preferences`() = runTest {
        // Arrange
        val config = AutoTopUpConfig(true, 10000, 20000, "Visa ending in 1111")

        // Act
        saveAutoTopUpConfigUseCase(config)

        // Assert
        coVerify { encryptedPrefsManager.saveAutoTopUpConfig(config) }
    }

    @Test
    fun `GetAutoTopUpConfigUseCase - retrieves config from preferences`() = runTest {
        // Arrange
        val config = AutoTopUpConfig(true, 10000, 20000, "Visa ending in 1111")
        every { encryptedPrefsManager.getAutoTopUpConfig() } returns config

        // Act
        val result = getAutoTopUpConfigUseCase()

        // Assert
        assertEquals(config, result)
        coVerify { encryptedPrefsManager.getAutoTopUpConfig() }
    }
}
