package com.lockin.app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lockin.app.core.data.local.converter.TypeConverters as LockInTypeConverters
import com.lockin.app.core.data.local.dao.SessionDao
import com.lockin.app.core.data.local.dao.SessionEventDao
import com.lockin.app.core.data.local.dao.WalletDao
import com.lockin.app.core.data.local.dao.WalletTransactionDao
import com.lockin.app.core.data.local.dao.AllowedAppDao
import com.lockin.app.core.data.local.entity.SessionEntity
import com.lockin.app.core.data.local.entity.SessionEventEntity
import com.lockin.app.core.data.local.entity.WalletEntity
import com.lockin.app.core.data.local.entity.WalletTransactionEntity
import com.lockin.app.core.data.local.entity.AllowedAppEntity

/**
 * Core Room database class for LockIn.
 * Registers entities and maps the custom type converters.
 */
@Database(
    entities = [
        SessionEntity::class,
        SessionEventEntity::class,
        WalletEntity::class,
        WalletTransactionEntity::class,
        AllowedAppEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(LockInTypeConverters::class)
abstract class LockInDatabase : RoomDatabase() {

    /**
     * Obtains the Session DAO instance.
     */
    abstract fun sessionDao(): SessionDao

    /**
     * Obtains the Session Event DAO instance.
     */
    abstract fun sessionEventDao(): SessionEventDao

    /**
     * Obtains the Wallet DAO instance.
     */
    abstract fun walletDao(): WalletDao

    /**
     * Obtains the Wallet Transaction DAO instance.
     */
    abstract fun walletTransactionDao(): WalletTransactionDao

    /**
     * Obtains the Allowed App DAO instance.
     */
    abstract fun allowedAppDao(): AllowedAppDao
}
