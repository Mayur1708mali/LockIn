package com.lockin.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lockin.app.core.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for wallet status and configuration.
 */
@Dao
interface WalletDao {

    /**
     * Inserts the default wallet or replaces the current settings configurations.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWallet(wallet: WalletEntity): Long

    /**
     * Updates an existing wallet record.
     */
    @Update
    suspend fun updateWallet(wallet: WalletEntity): Int

    /**
     * Streams the wallet data for a specific user to reflect live balance updates.
     */
    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    fun getWalletFlow(userId: String): Flow<WalletEntity?>

    /**
     * One-shot fetch of the wallet data for a specific user.
     */
    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    suspend fun getWallet(userId: String): WalletEntity?

    /**
     * Directly updates the available balance of the user's wallet.
     */
    @Query("UPDATE wallets SET availableBalance = :availableBalance, lastUpdated = :lastUpdated WHERE userId = :userId")
    suspend fun updateAvailableBalance(userId: String, availableBalance: Int, lastUpdated: Long): Int

    /**
     * Directly updates the held/locked balance of the user's wallet.
     */
    @Query("UPDATE wallets SET heldBalance = :heldBalance, lastUpdated = :lastUpdated WHERE userId = :userId")
    suspend fun updateHeldBalance(userId: String, heldBalance: Int, lastUpdated: Long): Int
}
