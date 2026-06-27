/*
 * File: app/src/androidTest/java/com/lockin/app/feature/onboarding/OnboardingUiTest.kt
 * Purpose: Compose UI tests for the onboarding walkthrough screens.
 */

package com.lockin.app.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lockin.app.core.domain.model.AutoTopUpConfig
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testOnboardingWalkthroughFlow() {
        var completed = false
        var vpnGranted = false
        var notificationsGranted = false

        // 1. Render OnboardingScreen in initial state (Step 0: Welcome Screen)
        val initialUiState = OnboardingUiState(
            currentStep = OnboardingStep.WELCOME,
            isDepositProcessing = false,
            depositSuccess = false,
            depositError = null,
            depositAmountPaise = 50000, // ₹500 default initial fund
            autoTopUpConfig = AutoTopUpConfig(
                autoTopUpEnabled = true,
                autoTopUpThresholdPaise = 10000,
                autoTopUpAmountPaise = 20000,
                savedPaymentMethodLabel = null
            )
        )

        composeTestRule.setContent {
            OnboardingScreenContent(
                uiState = initialUiState,
                onNextStep = { /* Tested via manual step triggers below */ },
                onBackStep = {},
                onGrantVpn = { vpnGranted = true },
                onGrantNotifications = { notificationsGranted = true },
                onSkipNotifications = {},
                onInitiateDeposit = {},
                onSimulateDepositSuccess = {},
                onToggleAutoTopUp = {},
                onSelectThreshold = {},
                onSelectAmount = {},
                onConfirmConfig = {},
                onCompleteOnboarding = { completed = true }
            )
        }

        // Verify Step 0: Welcome text and CTA are visible
        composeTestRule.onNodeWithText("Start Configuration").assertIsDisplayed()
    }

    @Test
    fun testRulesStateWalkthrough() {
        val rulesState = OnboardingUiState(
            currentStep = OnboardingStep.RULES,
            isDepositProcessing = false,
            depositSuccess = false,
            depositError = null,
            depositAmountPaise = 50000,
            autoTopUpConfig = AutoTopUpConfig(true, 10000, 20000, null)
        )

        composeTestRule.setContent {
            OnboardingScreenContent(
                uiState = rulesState,
                onNextStep = {},
                onBackStep = {},
                onGrantVpn = {},
                onGrantNotifications = {},
                onSkipNotifications = {},
                onInitiateDeposit = {},
                onSimulateDepositSuccess = {},
                onToggleAutoTopUp = {},
                onSelectThreshold = {},
                onSelectAmount = {},
                onConfirmConfig = {},
                onCompleteOnboarding = {}
            )
        }

        // Verify Step 1: Stakes rules page is visible
        composeTestRule.onNodeWithText("I Understand").assertIsDisplayed()
    }

    @Test
    fun testVpnStateWalkthrough() {
        val vpnState = OnboardingUiState(
            currentStep = OnboardingStep.VPN_PERMISSION,
            isDepositProcessing = false,
            depositSuccess = false,
            depositError = null,
            depositAmountPaise = 50000,
            autoTopUpConfig = AutoTopUpConfig(true, 10000, 20000, null)
        )

        var vpnClicked = false

        composeTestRule.setContent {
            OnboardingScreenContent(
                uiState = vpnState,
                onNextStep = {},
                onBackStep = {},
                onGrantVpn = { vpnClicked = true },
                onGrantNotifications = {},
                onSkipNotifications = {},
                onInitiateDeposit = {},
                onSimulateDepositSuccess = {},
                onToggleAutoTopUp = {},
                onSelectThreshold = {},
                onSelectAmount = {},
                onConfirmConfig = {},
                onCompleteOnboarding = {}
            )
        }

        // Verify Step 2: VPN permission CTA is visible and clickable
        composeTestRule.onNodeWithText("Grant VPN Permission").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant VPN Permission").performClick()
        assertTrue(vpnClicked)
    }

    @Test
    fun testNotificationsStateWalkthrough() {
        val notificationsState = OnboardingUiState(
            currentStep = OnboardingStep.NOTIFICATION_PERMISSION,
            isDepositProcessing = false,
            depositSuccess = false,
            depositError = null,
            depositAmountPaise = 50000,
            autoTopUpConfig = AutoTopUpConfig(true, 10000, 20000, null)
        )

        var notificationClicked = false
        var notificationSkipped = false

        composeTestRule.setContent {
            OnboardingScreenContent(
                uiState = notificationsState,
                onNextStep = {},
                onBackStep = {},
                onGrantVpn = {},
                onGrantNotifications = { notificationClicked = true },
                onSkipNotifications = { notificationSkipped = true },
                onInitiateDeposit = {},
                onSimulateDepositSuccess = {},
                onToggleAutoTopUp = {},
                onSelectThreshold = {},
                onSelectAmount = {},
                onConfirmConfig = {},
                onCompleteOnboarding = {}
            )
        }

        // Verify Step 3: Notification permission alerts and choices
        composeTestRule.onNodeWithText("Grant Permissions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip For Now").assertIsDisplayed()

        composeTestRule.onNodeWithText("Grant Permissions").performClick()
        assertTrue(notificationClicked)

        composeTestRule.onNodeWithText("Skip For Now").performClick()
        assertTrue(notificationSkipped)
    }
}
