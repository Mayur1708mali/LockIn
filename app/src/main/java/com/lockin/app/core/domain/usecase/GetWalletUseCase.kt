package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

/**
 * Use case to stream the user's wallet metrics and balances.
 */
class GetWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {

    /**
     * Streams the user's wallet data.
     *
     * @param userId The current user ID.
     * @return Flow streaming Wallet updates (filters out nulls).
     */
    operator fun invoke(userId: String): Flow<Wallet> {
        return walletRepository.getWalletFlow(userId).filterNotNull()
    }
}
