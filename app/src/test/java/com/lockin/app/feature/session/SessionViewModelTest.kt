/*
 * File: app/src/test/java/com/lockin/app/feature/session/SessionViewModelTest.kt
 * Purpose: Unit tests for SessionViewModel using MockK and Coroutines Test libraries.
 */

package com.lockin.app.feature.session

import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.repository.SessionRepository
import com.lockin.app.core.domain.usecase.CompleteSessionUseCase
import com.lockin.app.core.domain.usecase.GetActiveSessionUseCase
import com.lockin.app.core.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getActiveSessionUseCase: GetActiveSessionUseCase
    private lateinit var completeSessionUseCase: CompleteSessionUseCase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var viewModel: SessionViewModel

    private val userId = "user_abc"
    private val activeSession = Session(
        sessionId = "session_123",
        userId = userId,
        status = SessionStatus.ACTIVE,
        startTime = System.currentTimeMillis() - 5000,
        targetEndTime = System.currentTimeMillis() + 5000,
        actualEndTime = null,
        penaltyAmount = 10000,
        walletTxHoldId = "tx_hold_123",
        allowlistVersion = 1
    )

    private val activeSessionFlow = MutableStateFlow<Session?>(activeSession)

    @Before
    fun setUp() {
        getActiveSessionUseCase = mockk(relaxed = true)
        completeSessionUseCase = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)

        coEvery { getActiveSessionUseCase() } returns Result.success(activeSession)
        every { sessionRepository.getActiveSessionFlow() } returns activeSessionFlow
    }

    @Test
    fun `init - active session found - sets UI state to Success`() = runTest {
        // Act
        viewModel = SessionViewModel(
            getActiveSessionUseCase,
            completeSessionUseCase,
            sessionRepository
        )

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is SessionUiState.Success)
        val successState = state as SessionUiState.Success
        assertEquals("session_123", successState.sessionId)
        assertEquals(10000, successState.penaltyAmountPaise)
    }

    @Test
    fun `init - no active session found - sets UI state to Error`() = runTest {
        // Arrange
        coEvery { getActiveSessionUseCase() } returns Result.failure(IllegalStateException("No session"))

        // Act
        viewModel = SessionViewModel(
            getActiveSessionUseCase,
            completeSessionUseCase,
            sessionRepository
        )

        // Assert
        assertEquals(SessionUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `completeSession - calls complete use case and resolves`() = runTest {
        // Arrange
        coEvery { completeSessionUseCase() } returns Result.success(activeSession.copy(status = SessionStatus.COMPLETED))
        viewModel = SessionViewModel(
            getActiveSessionUseCase,
            completeSessionUseCase,
            sessionRepository
        )

        // Act
        viewModel.completeSession()

        // Assert
        coVerify { completeSessionUseCase() }
    }

    @Test
    fun `lifecycle - active session becomes null in DB - triggers finishedSession emission`() = runTest {
        // Arrange
        val finishedSession = activeSession.copy(status = SessionStatus.COMPLETED, actualEndTime = System.currentTimeMillis())
        coEvery { sessionRepository.getSessionById("session_123") } returns finishedSession
        
        viewModel = SessionViewModel(
            getActiveSessionUseCase,
            completeSessionUseCase,
            sessionRepository
        )

        // Act
        // Simulate DB clearing the active session row
        activeSessionFlow.value = null

        // Assert
        val finished = viewModel.finishedSession.first { it != null }
        assertEquals(SessionStatus.COMPLETED, finished?.status)
        assertEquals("session_123", finished?.sessionId)
    }
}
