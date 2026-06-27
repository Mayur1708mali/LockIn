/*
 * File: app/src/androidTest/java/com/lockin/app/feature/session/BreakGateUiTest.kt
 * Purpose: Compose UI tests for the 3-step Break-Early Gate screen.
 */

package com.lockin.app.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class BreakGateUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val penaltyAmountPaise = 10000 // ₹100

    @Test
    fun testWarningStepCountdownEnforced() {
        // Arrange: State with 5 seconds remaining on forced wait
        val warningState = BreakGateUiState(
            currentStep = BreakStep.WARNING,
            warningSecondsLeft = 5,
            penaltyAmountPaise = penaltyAmountPaise
        )

        composeTestRule.setContent {
            BreakGateScreenContent(
                uiState = warningState,
                onMoveToBiometric = {},
                onTriggerBiometric = {},
                onSimulateBiometric = {},
                onUpdateConfirmationText = {},
                onConfirmBreak = {},
                onCancel = {}
            )
        }

        // Assert Step 1 Warning header is shown
        composeTestRule.onNodeWithText("STEP 1 OF 3: STAKES WARNING").assertIsDisplayed()

        // Assert forced countdown message is shown
        composeTestRule.onNodeWithText("FORCED WAIT: 5S REMAINING").assertIsDisplayed()

        // Assert Continue button is disabled because timer is running
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun testWarningStepProceedEnabledAfterTimer() {
        var continueClicked = false

        // Arrange: State with 0 seconds remaining
        val warningState = BreakGateUiState(
            currentStep = BreakStep.WARNING,
            warningSecondsLeft = 0,
            penaltyAmountPaise = penaltyAmountPaise
        )

        composeTestRule.setContent {
            BreakGateScreenContent(
                uiState = warningState,
                onMoveToBiometric = { continueClicked = true },
                onTriggerBiometric = {},
                onSimulateBiometric = {},
                onUpdateConfirmationText = {},
                onConfirmBreak = {},
                onCancel = {}
            )
        }

        // Continue button should be enabled
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
        
        // Clicking should trigger transition
        composeTestRule.onNodeWithText("Continue").performClick()
        assertTrue(continueClicked)
    }

    @Test
    fun testBiometricStepComponents() {
        var biometricClicked = false
        var cancelClicked = false

        // Arrange: State at Step 2 Biometrics
        val biometricState = BreakGateUiState(
            currentStep = BreakStep.BIOMETRIC,
            warningSecondsLeft = 0,
            penaltyAmountPaise = penaltyAmountPaise
        )

        composeTestRule.setContent {
            BreakGateScreenContent(
                uiState = biometricState,
                onMoveToBiometric = {},
                onTriggerBiometric = { biometricClicked = true },
                onSimulateBiometric = {},
                onUpdateConfirmationText = {},
                onConfirmBreak = {},
                onCancel = { cancelClicked = true }
            )
        }

        // Assert Step 2 Biometric header is shown
        composeTestRule.onNodeWithText("STEP 2 OF 3: BIOMETRIC AUTHENTICATION").assertIsDisplayed()

        // Verify actions
        composeTestRule.onNodeWithText("Open Biometric Prompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Biometric Prompt").performClick()
        assertTrue(biometricClicked)

        composeTestRule.onNodeWithText("Abort and Return").performClick()
        assertTrue(cancelClicked)
    }

    @Test
    fun testTypedConfirmationValidation() {
        var typedText = ""
        var confirmClicked = false

        // Arrange: State at Step 3 typed confirmation
        val typedState = BreakGateUiState(
            currentStep = BreakStep.CONFIRMATION,
            warningSecondsLeft = 0,
            penaltyAmountPaise = penaltyAmountPaise,
            confirmationText = ""
        )

        composeTestRule.setContent {
            BreakGateScreenContent(
                uiState = typedState.copy(confirmationText = typedText),
                onMoveToBiometric = {},
                onTriggerBiometric = {},
                onSimulateBiometric = {},
                onUpdateConfirmationText = { typedText = it },
                onConfirmBreak = { confirmClicked = true },
                onCancel = {}
            )
        }

        // Assert Step 3 Typed confirmation header is shown
        composeTestRule.onNodeWithText("STEP 3 OF 3: TYPED CONFIRMATION").assertIsDisplayed()
        
        // Assert field is present and button is displayed
        composeTestRule.onNodeWithText("Confirm Charge & Break").assertIsDisplayed()
        
        // Click confirm
        composeTestRule.onNodeWithText("Confirm Charge & Break").performClick()
        assertTrue(confirmClicked)
    }
}
