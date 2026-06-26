package com.lockin.app.core.data.local.mapper

import com.lockin.app.core.data.local.entity.SessionEntity
import com.lockin.app.core.data.local.entity.SessionEventEntity
import com.lockin.app.core.data.local.entity.WalletEntity
import com.lockin.app.core.data.local.entity.WalletTransactionEntity
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction

import com.lockin.app.core.data.local.entity.AllowedAppEntity
import com.lockin.app.core.domain.model.AllowedApp

/**
 * Maps a SessionEntity (database representation) to a Session (domain representation).
 */
fun SessionEntity.toDomain(): Session = Session(
    sessionId = sessionId,
    userId = userId,
    status = status,
    startTime = startTime,
    targetEndTime = targetEndTime,
    actualEndTime = actualEndTime,
    penaltyAmount = penaltyAmount,
    currency = currency,
    walletTxHoldId = walletTxHoldId,
    allowlistVersion = allowlistVersion,
    platform = platform
)

/**
 * Maps a Session (domain representation) to a SessionEntity (database representation).
 */
fun Session.toEntity(): SessionEntity = SessionEntity(
    sessionId = sessionId,
    userId = userId,
    status = status,
    startTime = startTime,
    targetEndTime = targetEndTime,
    actualEndTime = actualEndTime,
    penaltyAmount = penaltyAmount,
    currency = currency,
    walletTxHoldId = walletTxHoldId,
    allowlistVersion = allowlistVersion,
    platform = platform
)

/**
 * Maps a SessionEventEntity (database representation) to a SessionEvent (domain representation).
 */
fun SessionEventEntity.toDomain(): SessionEvent = SessionEvent(
    eventId = eventId,
    sessionId = sessionId,
    eventType = eventType,
    timestamp = timestamp,
    metadata = metadata
)

/**
 * Maps a SessionEvent (domain representation) to a SessionEventEntity (database representation).
 */
fun SessionEvent.toEntity(): SessionEventEntity = SessionEventEntity(
    eventId = eventId,
    sessionId = sessionId,
    eventType = eventType,
    timestamp = timestamp,
    metadata = metadata
)

/**
 * Maps a WalletEntity (database representation) to a Wallet (domain representation).
 */
fun WalletEntity.toDomain(): Wallet = Wallet(
    userId = userId,
    availableBalance = availableBalance,
    heldBalance = heldBalance,
    totalDeposited = totalDeposited,
    totalPenaltiesPaid = totalPenaltiesPaid,
    autoTopUpEnabled = autoTopUpEnabled,
    autoTopUpThresholdPaise = autoTopUpThresholdPaise,
    autoTopUpAmountPaise = autoTopUpAmountPaise,
    lastUpdated = lastUpdated
)

/**
 * Maps a Wallet (domain representation) to a WalletEntity (database representation).
 */
fun Wallet.toEntity(): WalletEntity = WalletEntity(
    userId = userId,
    availableBalance = availableBalance,
    heldBalance = heldBalance,
    totalDeposited = totalDeposited,
    totalPenaltiesPaid = totalPenaltiesPaid,
    autoTopUpEnabled = autoTopUpEnabled,
    autoTopUpThresholdPaise = autoTopUpThresholdPaise,
    autoTopUpAmountPaise = autoTopUpAmountPaise,
    lastUpdated = lastUpdated
)

/**
 * Maps a WalletTransactionEntity (database representation) to a WalletTransaction (domain representation).
 */
fun WalletTransactionEntity.toDomain(): WalletTransaction = WalletTransaction(
    txId = txId,
    userId = userId,
    type = type,
    amount = amount,
    direction = direction,
    sessionId = sessionId,
    description = description,
    timestamp = timestamp
)

/**
 * Maps a WalletTransaction (domain representation) to a WalletTransactionEntity (database representation).
 */
fun WalletTransaction.toEntity(): WalletTransactionEntity = WalletTransactionEntity(
    txId = txId,
    userId = userId,
    type = type,
    amount = amount,
    direction = direction,
    sessionId = sessionId,
    description = description,
    timestamp = timestamp
)

/**
 * Maps an AllowedAppEntity to an AllowedApp domain model.
 */
fun AllowedAppEntity.toDomain(): AllowedApp = AllowedApp(
    packageName = packageName,
    appName = appName,
    addedAt = addedAt
)

/**
 * Maps an AllowedApp domain model to an AllowedAppEntity database record.
 */
fun AllowedApp.toEntity(): AllowedAppEntity = AllowedAppEntity(
    packageName = packageName,
    appName = appName,
    addedAt = addedAt
)
