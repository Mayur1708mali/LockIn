package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to retrieve the history of wallet transactions.
 * Streams transactions sorted by date descending (credits/debits).
 */
class GetTransactionHistoryUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {

    /**
     * Streams all wallet transactions.
     *
     * @return A Flow emitting a list of all WalletTransactions.
     */
    operator fun invoke(): Flow<List<WalletTransaction>> {
        return walletRepository.getAllTransactionsFlow()
    }
}
