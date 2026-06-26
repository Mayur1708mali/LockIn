package com.lockin.app.core.data.local.converter

import androidx.room.TypeConverter
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.TransactionType

/**
 * Type converters for Room database to handle custom enums.
 */
class TypeConverters {

    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus {
        return try {
            SessionStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SessionStatus.PENDING
        }
    }

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return try {
            TransactionType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TransactionType.DEPOSIT
        }
    }
}
