/*
 * File: app/src/test/java/com/lockin/app/feature/wallet/WalletViewModelTest.kt
 * Purpose: Unit tests for WalletViewModel using MockK and Coroutines Test libraries.
 */

package com.lockin.app.feature.wallet

import androidx.fragment.app.FragmentActivity
import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.usecase.DepositToWalletUseCase
import com.lockin.app.core.domain.usecase.GetTransactionHistoryUseCase
import com.lockin.app.core.domain.usecase.GetWalletUseCase
import com.lockin.app.core.domain.usecase.WithdrawFromWalletUseCase
import com.lockin.app.core.security.BiometricHelper
import com.lockin.app.core.security.BiometricResult
import com.lockin.app.core.security.EncryptedPrefsManager
import com.lockin.app.core.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WalletViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getWalletUseCase: GetWalletUseCase
    private lateinit var getTransactionHistoryUseCase: GetTransactionHistoryUseCase
    private lateinit var withdrawFromWalletUseCase: WithdrawFromWalletUseCase
    private lateinit var depositToWalletUseCase: DepositToWalletUseCase
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var encryptedPrefsManager: EncryptedPrefsManager
    private lateinit var razorpayManager: RazorpayManager
    private lateinit var viewModel: WalletViewModel

    private val userId = "test_user_456"
    private val defaultWallet = Wallet(
        userId = userId,
        availableBalance = 20000, // ₹200
        heldBalance = 0,
        totalDeposited = 20000,
        totalPenaltiesPaid = 0,
        autoTopUpEnabled = true,
        autoTopUpThresholdPaise = 10000,
        autoTopUpAmountPaise = 20000,
        lastUpdated = System.currentTimeMillis()
    )

    private val transactions = listOf<WalletTransaction>()

    @Before
    fun setUp() {
        getWalletUseCase = mockk(relaxed = true)
        getTransactionHistoryUseCase = mockk(relaxed = true)
        withdrawFromWalletUseCase = mockk(relaxed = true)
        depositToWalletUseCase = mockk(relaxed = true)
        biometricHelper = mockk(relaxed = true)
        encryptedPrefsManager = mockk(relaxed = true)
        razorpayManager = mockk(relaxed = true)

        every { encryptedPrefsManager.getUserId() } returns userId
        every { getWalletUseCase(userId) } returns flowOf(defaultWallet)
        every { getTransactionHistoryUseCase() } returns flowOf(transactions)
    }

    @Test
    fun `init - loads wallet and transactions dashboard`() = runTest {
        // Act
        viewModel = WalletViewModel(
            getWalletUseCase,
            getTransactionHistoryUseCase,
            withdrawFromWalletUseCase,
            depositToWalletUseCase,
            biometricHelper,
            encryptedPrefsManager,
            razorpayManager
        )

        // Assert
        assertEquals(defaultWallet, viewModel.uiState.value.wallet)
        assertEquals(transactions, viewModel.uiState.value.transactions)
        assertFalse(viewModel.uiState.value.isWalletLoading)
        assertFalse(viewModel.uiState.value.isTransactionsLoading)
    }

    @Test
    fun `updateWithdrawAmount - updates input amount state`() = runTest {
        viewModel = WalletViewModel(
            getWalletUseCase,
            getTransactionHistoryUseCase,
            withdrawFromWalletUseCase,
            depositToWalletUseCase,
            biometricHelper,
            encryptedPrefsManager,
            razorpayManager
        )

        // Act
        viewModel.updateWithdrawAmount("8000") // ₹80

        // Assert
        assertEquals("8000", viewModel.uiState.value.withdrawAmountPaise)
        assertNull(viewModel.uiState.value.withdrawalError)
        assertFalse(viewModel.uiState.value.withdrawalSuccess)
    }

    @Test
    fun `initiateWithdrawal - biometrics available and sufficient balance - executes successfully`() = runTest {
        // Arrange
        val activity = mockk<FragmentActivity>()
        every { biometricHelper.isBiometricAvailable() } returns true
        every {
            biometricHelper.authenticate(activity, any(), any(), any())
        } returns flowOf(BiometricResult.Success)
        coEvery { withdrawFromWalletUseCase(userId, 10000) } returns Result.success(true)

        viewModel = WalletViewModel(
            getWalletUseCase,
            getTransactionHistoryUseCase,
            withdrawFromWalletUseCase,
            depositToWalletUseCase,
            biometricHelper,
            encryptedPrefsManager,
            razorpayManager
        )
        viewModel.updateWithdrawAmount("10000") // ₹100

        // Act
        viewModel.initiateWithdrawal(activity)

        // Assert
        assertTrue(viewModel.uiState.value.withdrawalSuccess)
        assertEquals("", viewModel.uiState.value.withdrawAmountPaise)
        assertNull(viewModel.uiState.value.withdrawalError)
        coVerify { withdrawFromWalletUseCase(userId, 10000) }
    }

    @Test
    fun `initiateWithdrawal - biometrics unavailable - sets error state`() = runTest {
        // Arrange
        val activity = mockk<FragmentActivity>()
        every { biometricHelper.isBiometricAvailable() } returns false

        viewModel = WalletViewModel(
            getWalletUseCase,
            getTransactionHistoryUseCase,
            withdrawFromWalletUseCase,
            depositToWalletUseCase,
            biometricHelper,
            encryptedPrefsManager,
            razorpayManager
        )
        viewModel.updateWithdrawAmount("10000")

        // Act
        viewModel.initiateWithdrawal(activity)

        // Assert
        assertFalse(viewModel.uiState.value.withdrawalSuccess)
        assertEquals("Biometric setup is required to authorize bank withdrawals.", viewModel.uiState.value.withdrawalError)
        coVerify(exactly = 0) { withdrawFromWalletUseCase(any(), any()) }
    }

    @Test
    fun `initiateWithdrawal - insufficient balance - sets error state`() = runTest {
        // Arrange
        val activity = mockk<FragmentActivity>()

        viewModel = WalletViewModel(
            getWalletUseCase,
            getTransactionHistoryUseCase,
            withdrawFromWalletUseCase,
            depositToWalletUseCase,
            biometricHelper,
            encryptedPrefsManager,
            razorpayManager
        )
        viewModel.updateWithdrawAmount("30000") // ₹300 (Available: ₹200)

        // Act
        viewModel.initiateWithdrawal(activity)

        // Assert
        assertFalse(viewModel.uiState.value.withdrawalSuccess)
        assertEquals("Insufficient available balance for withdrawal", viewModel.uiState.value.withdrawalError)
        coVerify(exactly = 0) { withdrawFromWalletUseCase(any(), any()) }
    }
}
