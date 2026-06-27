/*
 * File: app/src/test/java/com/lockin/app/feature/session/BreakGateViewModelTest.kt
 * Purpose: Unit tests for BreakGateViewModel using MockK and Coroutines Test libraries.
 */

package com.lockin.app.feature.session

import androidx.fragment.app.FragmentActivity
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.core.domain.usecase.BreakSessionUseCase
import com.lockin.app.core.domain.usecase.LogSessionEventUseCase
import com.lockin.app.core.security.BiometricHelper
import com.lockin.app.core.security.BiometricResult
import com.lockin.app.core.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BreakGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var breakSessionUseCase: BreakSessionUseCase
    private lateinit var logSessionEventUseCase: LogSessionEventUseCase
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var viewModel: BreakGateViewModel

    private val sessionId = "session_123"
    private val penaltyAmount = 10000 // ₹100

    @Before
    fun setUp() {
        breakSessionUseCase = mockk(relaxed = true)
        logSessionEventUseCase = mockk(relaxed = true)
        biometricHelper = mockk(relaxed = true)

        viewModel = BreakGateViewModel(
            breakSessionUseCase,
            logSessionEventUseCase,
            biometricHelper
        )
    }

    @Test
    fun `initSession - initializes state with warning step and 10s left`() = runTest {
        // Act
        viewModel.initSession(sessionId, penaltyAmount)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(BreakStep.WARNING, state.currentStep)
        assertEquals(10, state.warningSecondsLeft)
        assertEquals(penaltyAmount, state.penaltyAmountPaise)
        assertFalse(state.isBreaking)
        assertFalse(state.isBreakSuccess)
        assertNull(state.error)
        coVerify { logSessionEventUseCase(sessionId, "BREAK_ATTEMPT", any()) }
    }

    @Test
    fun `moveToBiometric - timer not finished - does not change step`() = runTest {
        viewModel.initSession(sessionId, penaltyAmount)

        // Act
        viewModel.moveToBiometric()

        // Assert
        assertEquals(BreakStep.WARNING, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `moveToBiometric - timer finished - updates step to BIOMETRIC`() = runTest {
        viewModel.initSession(sessionId, penaltyAmount)
        
        // Arrange (cheat countdown state)
        // Set warning seconds left to 0 directly
        viewModel.updateConfirmationText("") // dummy updates to sync state, but warningSecondsLeft is in UIState copy
        // We will just call the private countdown ticks or simulate timer finished by copying uiState in a test helper,
        // but since we have a state flow we can simulate countdown completion or manipulate state in test.
        // Let's verify moveToBiometric works if warningSecondsLeft <= 0:
        viewModel.initSession(sessionId, penaltyAmount)
        // Wait for countdown timer or mock it, but wait: the warningSecondsLeft ticks down every 1000ms.
        // Since we are using UnconfinedTestDispatcher, delay(1000) ticks instantly in runTest!
        // So by launching in runTest, the entire 10-second countdown runs immediately and finishes!
        // Let's check:
        assertTrue(viewModel.uiState.value.warningSecondsLeft <= 0)

        // Act
        viewModel.moveToBiometric()

        // Assert
        assertEquals(BreakStep.BIOMETRIC, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `triggerBiometricPrompt - success - transitions to CONFIRMATION step`() = runTest {
        // Arrange
        val activity = mockk<FragmentActivity>()
        every {
            biometricHelper.authenticate(activity, any(), any(), any())
        } returns flowOf(BiometricResult.Success)

        viewModel.initSession(sessionId, penaltyAmount)

        // Act
        viewModel.triggerBiometricPrompt(activity)

        // Assert
        assertEquals(BreakStep.CONFIRMATION, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `triggerBiometricPrompt - error - sends user back to warning step resetting timer`() = runTest {
        // Arrange
        val activity = mockk<FragmentActivity>()
        every {
            biometricHelper.authenticate(activity, any(), any(), any())
        } returns flowOf(BiometricResult.Error(101, "User cancelled"))

        viewModel.initSession(sessionId, penaltyAmount)

        // Act
        viewModel.triggerBiometricPrompt(activity)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(BreakStep.WARNING, state.currentStep)
        assertEquals(10, state.warningSecondsLeft) // timer reset
        assertEquals("Biometric authentication failed. Wait 10 seconds to retry.", state.error)
        coVerify { logSessionEventUseCase(sessionId, "BREAK_ATTEMPT_FAILED", any()) }
    }

    @Test
    fun `confirmBreak - incorrect typed text - sets error and does not break`() = runTest {
        viewModel.initSession(sessionId, penaltyAmount)
        viewModel.onBiometricSuccess() // manually move to confirmation step
        
        // Act
        viewModel.updateConfirmationText("break") // lowercase (incorrect)
        viewModel.confirmBreak()

        // Assert
        assertEquals("Type the word BREAK exactly (all-caps) to proceed.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isBreakSuccess)
        coVerify(exactly = 0) { breakSessionUseCase() }
    }

    @Test
    fun `confirmBreak - correct typed text - breaks session early charging penalty`() = runTest {
        // Arrange
        val brokenSession = Session(sessionId, "user_123", SessionStatus.BROKEN, 0, 0, 0, penaltyAmount, null, 1)
        coEvery { breakSessionUseCase() } returns Result.success(brokenSession)

        viewModel.initSession(sessionId, penaltyAmount)
        viewModel.onBiometricSuccess() // manually move to confirmation step

        // Act
        viewModel.updateConfirmationText("BREAK") // correct spelling
        viewModel.confirmBreak()

        // Assert
        assertTrue(viewModel.uiState.value.isBreakSuccess)
        assertNull(viewModel.uiState.value.error)
        coVerify { breakSessionUseCase() }
    }
}
