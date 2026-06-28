/*
 * File: C:/Users/mayur/AndroidStudioProjects/LockIn/app/src/main/java/com/lockin/app/feature/wallet/WalletViewModel.kt
 * Purpose: ViewModel orchestrating the LockIn Wallet dashboard.
 * Exposes available and held wallet balances, reactive transaction lists,
 * and handles secure deposits and biometric-protected withdrawals.
 */

package com.lockin.app.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import android.app.Activity
import com.lockin.app.core.data.payment.RazorpayManager
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.usecase.DepositToWalletUseCase
import com.lockin.app.core.domain.usecase.GetTransactionHistoryUseCase
import com.lockin.app.core.domain.usecase.GetWalletUseCase
import com.lockin.app.core.domain.usecase.WithdrawFromWalletUseCase
import com.lockin.app.core.security.BiometricHelper
import com.lockin.app.core.security.BiometricResult
import com.lockin.app.core.security.EncryptedPrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state definition for the Wallet screen dashboard.
 * Represents all parameters required to draw the balances, list transactions, and manage sheets.
 */
data class WalletUiState(
    val wallet: Wallet? = null,
    val isWalletLoading: Boolean = true,
    val transactions: List<WalletTransaction> = emptyList(),
    val isTransactionsLoading: Boolean = true,
    val withdrawAmountRupees: String = "",
    val isWithdrawalProcessing: Boolean = false,
    val withdrawalError: String? = null,
    val withdrawalSuccess: Boolean = false,
    val depositAmountPaise: Int = 0,
    val isDepositProcessing: Boolean = false,
    val depositError: String? = null,
    val depositSuccess: Boolean = false
)

/**
 * ViewModel processing wallet balance views, withdrawals, and manual Razorpay deposits.
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val getWalletUseCase: GetWalletUseCase,
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val withdrawFromWalletUseCase: WithdrawFromWalletUseCase,
    private val depositToWalletUseCase: DepositToWalletUseCase,
    private val biometricHelper: BiometricHelper,
    private val encryptedPrefsManager: EncryptedPrefsManager,
    private val razorpayManager: RazorpayManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var currentLoadedUserId: String? = null
    private var walletCollectionJob: kotlinx.coroutines.Job? = null
    private var transactionsCollectionJob: kotlinx.coroutines.Job? = null

    init {
        loadWalletDashboard()
    }

    /**
     * Connects reactive data streams from DB for the user's wallet metrics and transaction lists.
     * Made public to allow refresh from composition when user context changes.
     */
    fun loadWalletDashboard() {
        val userId = encryptedPrefsManager.getUserId() ?: "default_user"
        Timber.d("Loading Wallet data stream for userId: $userId")

        if (userId != currentLoadedUserId) {
            walletCollectionJob?.cancel()
            transactionsCollectionJob?.cancel()
            currentLoadedUserId = userId

            // 1. Observe active Wallet balances
            walletCollectionJob = viewModelScope.launch {
                _uiState.update { it.copy(isWalletLoading = true) }
                getWalletUseCase(userId).collectLatest { wallet ->
                    _uiState.update {
                        it.copy(
                            wallet = wallet,
                            isWalletLoading = false
                        )
                    }
                }
            }

            // 2. Observe all historical Wallet transactions
            transactionsCollectionJob = viewModelScope.launch {
                _uiState.update { it.copy(isTransactionsLoading = true) }
                getTransactionHistoryUseCase().collectLatest { txs ->
                    _uiState.update {
                        it.copy(
                            transactions = txs,
                            isTransactionsLoading = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Updates the input withdrawal amount value (in Rupees).
     *
     * @param amount text input representing the amount.
     */
    fun updateWithdrawAmount(amount: String) {
        _uiState.update {
            it.copy(
                withdrawAmountRupees = amount,
                withdrawalError = null,
                withdrawalSuccess = false
            )
        }
    }

    /**
     * Checks if biometric verification is supported and set up on the current hardware.
     */
    fun isBiometricAvailable(): Boolean {
        return biometricHelper.isBiometricAvailable()
    }

    /**
     * Triggers biometric confirmation gate before executing bank withdrawals.
     * The input amount is in Rupees and converted to Paise.
     *
     * @param activity The FragmentActivity context needed to show system prompts.
     */
    fun initiateWithdrawal(activity: FragmentActivity) {
        val amountInRupees = _uiState.value.withdrawAmountRupees.toIntOrNull() ?: 0
        val amountInPaise = amountInRupees * 100
        if (amountInRupees < 50) {
            _uiState.update { it.copy(withdrawalError = "Minimum withdrawal amount is ₹50") }
            return
        }

        val userId = encryptedPrefsManager.getUserId() ?: "default_user"
        val availableBalance = _uiState.value.wallet?.availableBalance ?: 0
        if (availableBalance < amountInPaise) {
            _uiState.update { it.copy(withdrawalError = "Insufficient available balance for withdrawal") }
            return
        }

        if (!isBiometricAvailable()) {
            _uiState.update { it.copy(withdrawalError = "Biometric setup is required to authorize bank withdrawals.") }
            return
        }

        viewModelScope.launch {
            biometricHelper.authenticate(
                activity = activity,
                title = "Confirm Wallet Withdrawal",
                subtitle = "Authorize withdrawal of ₹$amountInRupees.00 to your bank account."
            ).collect { result ->
                when (result) {
                    is BiometricResult.Success -> {
                        executeWithdrawal(userId, amountInPaise)
                    }
                    is BiometricResult.Failure -> {
                        // Handled by native dialog prompt
                    }
                    is BiometricResult.Error -> {
                        _uiState.update {
                            it.copy(withdrawalError = "Biometric auth failed: ${result.errorMessage}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Simulator bypass to trigger withdrawals directly in environments without biometric enrollments.
     * Converts the input Rupees amount to Paise.
     */
    fun simulateBiometricWithdrawal() {
        val amountInRupees = _uiState.value.withdrawAmountRupees.toIntOrNull() ?: 0
        val amountInPaise = amountInRupees * 100
        val userId = encryptedPrefsManager.getUserId() ?: "default_user"
        executeWithdrawal(userId, amountInPaise)
    }

    /**
     * Calls usecase to persist the withdrawal and log debit transaction.
     */
    private fun executeWithdrawal(userId: String, amountPaise: Int) {
        _uiState.update { it.copy(isWithdrawalProcessing = true, withdrawalError = null) }
        viewModelScope.launch {
            val result = withdrawFromWalletUseCase(userId, amountPaise)
            if (result.isSuccess) {
                Timber.i("Withdrawal of $amountPaise Paise completed successfully.")
                _uiState.update {
                    it.copy(
                        isWithdrawalProcessing = false,
                        withdrawalSuccess = true,
                        withdrawAmountRupees = ""
                    )
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Transaction execution error"
                Timber.e("Withdrawal failed: $errorMsg")
                _uiState.update {
                    it.copy(
                        isWithdrawalProcessing = false,
                        withdrawalError = errorMsg
                    )
                }
            }
        }
    }

    /**
     * Initiates manual deposits by configuring targeted amount state prior to Razorpay checkout.
     *
     * @param amountPaise The amount selected by user in Paise.
     */
    fun initiateDeposit(amountPaise: Int) {
        _uiState.update {
            it.copy(
                depositAmountPaise = amountPaise,
                isDepositProcessing = true,
                depositError = null,
                depositSuccess = false
            )
        }
    }

    /**
     * Triggers the real Razorpay checkout overlay using the SDK helper wrapper.
     *
     * @param activity The host activity.
     */
    fun startDeposit(activity: Activity) {
        val amountPaise = _uiState.value.depositAmountPaise
        if (amountPaise <= 0) return

        _uiState.update { it.copy(isDepositProcessing = true, depositError = null) }
        viewModelScope.launch {
            val result = razorpayManager.deposit(activity, amountPaise)
            if (result.isSuccess) {
                handleDepositSuccess(result.getOrThrow())
            } else {
                handleDepositFailure(result.exceptionOrNull()?.message ?: "Payment aborted")
            }
        }
    }

    /**
     * Processes successful payment events from Razorpay checkout.
     * Invokes usecase to credit user's database wallet.
     *
     * @param paymentId Razorpay payment reference hash.
     */
    fun handleDepositSuccess(paymentId: String) {
        val userId = encryptedPrefsManager.getUserId() ?: "default_user"
        val amountPaise = _uiState.value.depositAmountPaise
        if (amountPaise <= 0) return

        viewModelScope.launch {
            val result = depositToWalletUseCase(
                userId = userId,
                amountPaise = amountPaise,
                transactionType = TransactionType.DEPOSIT,
                razorpayPaymentId = paymentId
            )
            if (result.isSuccess) {
                Timber.i("Manual deposit of $amountPaise Paise credited successfully via payment ID $paymentId.")
                _uiState.update {
                    it.copy(
                        isDepositProcessing = false,
                        depositSuccess = true,
                        depositAmountPaise = 0
                    )
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Database credit transaction error"
                Timber.e("Deposit credit failed: $errorMsg")
                _uiState.update {
                    it.copy(
                        isDepositProcessing = false,
                        depositError = errorMsg
                    )
                }
            }
        }
    }

    /**
     * Handles payment errors from Razorpay payment flow.
     */
    fun handleDepositFailure(errorMessage: String) {
        Timber.e("Deposit payment checkout failed: $errorMessage")
        _uiState.update {
            it.copy(
                isDepositProcessing = false,
                depositError = errorMessage
            )
        }
    }

    /**
     * Resets transient alert error fields when sheets are dismissed.
     */
    fun clearErrors() {
        _uiState.update {
            it.copy(
                withdrawalError = null,
                withdrawalSuccess = false,
                depositError = null,
                depositSuccess = false
            )
        }
    }
}
