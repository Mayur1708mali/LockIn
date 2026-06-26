package com.lockin.app.di

import android.content.Context
import androidx.room.Room
import com.lockin.app.core.data.local.LockInDatabase
import com.lockin.app.core.data.local.dao.SessionDao
import com.lockin.app.core.data.local.dao.SessionEventDao
import com.lockin.app.core.data.local.dao.WalletDao
import com.lockin.app.core.data.local.dao.WalletTransactionDao
import com.lockin.app.core.data.local.dao.AllowedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for database components.
 * Provides singleton instances of Room database and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton instance of the LockIn Database.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): LockInDatabase {
        return Room.databaseBuilder(
            context,
            LockInDatabase::class.java,
            "lockin_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    /**
     * Provides the Session DAO.
     */
    @Provides
    @Singleton
    fun provideSessionDao(db: LockInDatabase): SessionDao {
        return db.sessionDao()
    }

    /**
     * Provides the Session Event DAO.
     */
    @Provides
    @Singleton
    fun provideSessionEventDao(db: LockInDatabase): SessionEventDao {
        return db.sessionEventDao()
    }

    /**
     * Provides the Wallet DAO.
     */
    @Provides
    @Singleton
    fun provideWalletDao(db: LockInDatabase): WalletDao {
        return db.walletDao()
    }

    /**
     * Provides the Wallet Transaction DAO.
     */
    @Provides
    @Singleton
    fun provideWalletTransactionDao(db: LockInDatabase): WalletTransactionDao {
        return db.walletTransactionDao()
    }

    /**
     * Provides the Allowed App DAO.
     */
    @Provides
    @Singleton
    fun provideAllowedAppDao(db: LockInDatabase): AllowedAppDao {
        return db.allowedAppDao()
    }
}
