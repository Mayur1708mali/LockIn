/*
 * File: app/src/test/java/com/lockin/app/core/domain/usecase/SessionUseCasesTest.kt
 * Purpose: Unit tests for all focus session domain use cases using MockK.
 */

package com.lockin.app.core.domain.usecase

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.repository.WalletRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SessionUseCasesTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var walletRepository: WalletRepository

    private lateinit var startSessionUseCase: StartSessionUseCase
    private lateinit var completeSessionUseCase: CompleteSessionUseCase
    private lateinit var breakSessionUseCase: BreakSessionUseCase
    private lateinit var getActiveSessionUseCase: GetActiveSessionUseCase
    private lateinit var getSessionHistoryUseCase: GetSessionHistoryUseCase
    private lateinit var getStreakUseCase: GetStreakUseCase
    private lateinit var logSessionEventUseCase: LogSessionEventUseCase

    private val userId = "test_user_123"
    private val defaultWallet = Wallet(
        userId = userId,
        availableBalance = 50000, // ₹500
        heldBalance = 0,
        totalDeposited = 50000,
        totalPenaltiesPaid = 0,
        autoTopUpEnabled = true,
        autoTopUpThresholdPaise = 10000,
        autoTopUpAmountPaise = 20000,
        lastUpdated = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        sessionRepository = mockk(relaxed = true)
        walletRepository = mockk(relaxed = true)

        startSessionUseCase = StartSessionUseCase(sessionRepository, walletRepository)
        completeSessionUseCase = CompleteSessionUseCase(sessionRepository, walletRepository)
        breakSessionUseCase = BreakSessionUseCase(sessionRepository, walletRepository)
        getActiveSessionUseCase = GetActiveSessionUseCase(sessionRepository)
        getSessionHistoryUseCase = GetSessionHistoryUseCase(sessionRepository)
        getStreakUseCase = GetStreakUseCase(sessionRepository)
        logSessionEventUseCase = LogSessionEventUseCase(sessionRepository)
    }

    @Test
    fun `StartSessionUseCase - sufficient balance - starts session successfully`() = runTest {
        // Arrange
        coEvery { walletRepository.getWallet(userId) } returns defaultWallet
        coEvery {
            sessionRepository.startSessionTransaction(any(), any(), any(), any())
        } returns true

        // Act
        val result = startSessionUseCase(
            userId = userId,
            penaltyAmount = 20000, // ₹200
            durationMs = 3600000, // 1 hour
            allowlistVersion = 1
        )

        // Assert
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertEquals(userId, session?.userId)
        assertEquals(SessionStatus.ACTIVE, session?.status)
        assertEquals(20000, session?.penaltyAmount)

        coVerify {
            sessionRepository.startSessionTransaction(
                session = any(),
                holdTransaction = any(),
                newAvailableBalance = 30000, // 50000 - 20000
                newHeldBalance = 20000 // 0 + 20000
            )
        }
    }

    @Test
    fun `StartSessionUseCase - insufficient balance - returns failure`() = runTest {
        // Arrange
        val lowBalanceWallet = defaultWallet.copy(availableBalance = 5000) // ₹50
        coEvery { walletRepository.getWallet(userId) } returns lowBalanceWallet

        // Act
        val result = startSessionUseCase(
            userId = userId,
            penaltyAmount = 20000, // ₹200
            durationMs = 3600000,
            allowlistVersion = 1
        )

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { sessionRepository.startSessionTransaction(any(), any(), any(), any()) }
    }

    @Test
    fun `CompleteSessionUseCase - active session exists - completes session successfully`() = runTest {
        // Arrange
        val activeSession = Session(
            sessionId = "session_abc",
            userId = userId,
            status = SessionStatus.ACTIVE,
            startTime = System.currentTimeMillis() - 10000,
            targetEndTime = System.currentTimeMillis() + 10000,
            actualEndTime = null,
            penaltyAmount = 10000,
            walletTxHoldId = "tx_hold_123",
            allowlistVersion = 1
        )
        val walletWithHeld = defaultWallet.copy(availableBalance = 40000, heldBalance = 10000)

        coEvery { sessionRepository.getActiveSession() } returns activeSession
        coEvery { walletRepository.getWallet(userId) } returns walletWithHeld
        coEvery {
            sessionRepository.completeSessionTransaction(any(), any(), any(), any(), any())
        } returns true

        // Act
        val result = completeSessionUseCase()

        // Assert
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertEquals(SessionStatus.COMPLETED, session?.status)

        coVerify {
            sessionRepository.completeSessionTransaction(
                session = any(),
                releaseTransaction = any(),
                newAvailableBalance = 50000, // 40000 + 10000
                newHeldBalance = 0, // 10000 - 10000
                completedEvent = any()
            )
        }
    }

    @Test
    fun `CompleteSessionUseCase - no active session - returns failure`() = runTest {
        // Arrange
        coEvery { sessionRepository.getActiveSession() } returns null

        // Act
        val result = completeSessionUseCase()

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `BreakSessionUseCase - active session exists - breaks session early charging penalty`() = runTest {
        // Arrange
        val activeSession = Session(
            sessionId = "session_abc",
            userId = userId,
            status = SessionStatus.ACTIVE,
            startTime = System.currentTimeMillis() - 10000,
            targetEndTime = System.currentTimeMillis() + 10000,
            actualEndTime = null,
            penaltyAmount = 10000,
            walletTxHoldId = "tx_hold_123",
            allowlistVersion = 1
        )
        val walletWithHeld = defaultWallet.copy(availableBalance = 40000, heldBalance = 10000)

        coEvery { sessionRepository.getActiveSession() } returns activeSession
        coEvery { walletRepository.getWallet(userId) } returns walletWithHeld
        coEvery {
            sessionRepository.breakSessionTransaction(any(), any(), any(), any(), any())
        } returns true

        // Act
        val result = breakSessionUseCase()

        // Assert
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertEquals(SessionStatus.BROKEN, session?.status)

        coVerify {
            sessionRepository.breakSessionTransaction(
                session = any(),
                penaltyTransaction = any(),
                newHeldBalance = 0, // 10000 - 10000
                newTotalPenaltiesPaid = 10000, // 0 + 10000
                brokenEvent = any()
            )
        }
    }

    @Test
    fun `GetStreakUseCase - calculates completed streaks correctly`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Generate sessions completed today, yesterday, and 2 days ago
        val sessions = listOf(
            Session("s1", userId, SessionStatus.COMPLETED, now, now, now, 1000, allowlistVersion = 1),
            Session("s2", userId, SessionStatus.COMPLETED, now - oneDayMs, now - oneDayMs, now - oneDayMs, 1000, allowlistVersion = 1),
            Session("s3", userId, SessionStatus.COMPLETED, now - 2 * oneDayMs, now - 2 * oneDayMs, now - 2 * oneDayMs, 1000, allowlistVersion = 1),
            Session("s4", userId, SessionStatus.BROKEN, now - 3 * oneDayMs, now - 3 * oneDayMs, now - 3 * oneDayMs, 1000, allowlistVersion = 1)
        )

        every { sessionRepository.getAllSessionsFlow() } returns flowOf(sessions)

        // Act
        val streak = getStreakUseCase().first()

        // Assert
        assertEquals(3, streak)
    }

    @Test
    fun `LogSessionEventUseCase - appends session event correctly`() = runTest {
        // Arrange
        val event = SessionEvent(
            eventId = "event_123",
            sessionId = "session_123",
            eventType = "HEARTBEAT",
            timestamp = System.currentTimeMillis(),
            metadata = null
        )

        // Act
        logSessionEventUseCase(event)

        // Assert
        coVerify { sessionRepository.insertEvent(event) }
    }
}
