/*
 * File: com/lockin/app/navigation/LockInNavGraph.kt
 * Purpose: Navigation Graph definition for LockIn using Navigation 3.
 * Defines the mapping from each LockInRoute key to its corresponding Composable screen.
 */

package com.lockin.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.lockin.app.feature.home.HomeScreen
import com.lockin.app.feature.onboarding.OnboardingScreen
import com.lockin.app.feature.session.ActiveSessionScreen
import com.lockin.app.feature.session.BreakGateScreen
import com.lockin.app.feature.session.SessionCompleteScreen
import com.lockin.app.feature.wallet.WalletScreen
import com.lockin.app.feature.settings.SettingsScreen
import com.lockin.app.feature.history.HistoryScreen
import timber.log.Timber

/**
 * Renders the Navigation display for LockIn based on the current back stack.
 * Maps each serialized [LockInRoute] to its screen UI.
 * Integrates Google Sign-In as Step 0 of Onboarding and supports global redirection upon sign out/session expiration.
 * Temporary placeholder screens are supplied so the app compiles while UI screens are being built.
 *
 * @param backStack The Navigation 3 back stack tracking current destination state.
 */
@Composable
fun LockInNavGraph(
    backStack: NavBackStack<NavKey>
) {
    val currentRoute = backStack.lastOrNull() as? LockInRoute
    
    // LockIn requires blocking back navigation when in an active session or break gate.
    val isBackNavigationDisabled = currentRoute is LockInRoute.ActiveSession || currentRoute is LockInRoute.BreakGate
    
    BackHandler(enabled = isBackNavigationDisabled) {
        Timber.w("Back press blocked: Back navigation is disabled during active session or break-early gate.")
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider<NavKey> {
            entry<LockInRoute.Onboarding> {
                OnboardingScreen(
                    onComplete = {
                        backStack.clear()
                        backStack.add(LockInRoute.Home)
                    }
                )
            }
            entry<LockInRoute.Home> {
                HomeScreen(
                    onNavigateToActiveSession = { sessionId, duration, penalty ->
                        backStack.add(
                            LockInRoute.ActiveSession(
                                sessionId = sessionId,
                                durationSeconds = duration,
                                penaltyAmountPaise = penalty
                            )
                        )
                    },
                    onNavigateToSettings = {
                        backStack.add(LockInRoute.Settings)
                    },
                    onNavigateToWallet = {
                        backStack.add(LockInRoute.Wallet(openWithdrawalSheet = false))
                    }
                )
            }
            entry<LockInRoute.ActiveSession> { route ->
                ActiveSessionScreen(
                    sessionId = route.sessionId,
                    onNavigateToBreakGate = { sessionId, penalty ->
                        backStack.add(
                            LockInRoute.BreakGate(
                                sessionId = sessionId,
                                penaltyAmountPaise = penalty
                            )
                        )
                    },
                    onSessionFinished = { sessionId, status ->
                        backStack.clear()
                        backStack.add(
                            LockInRoute.SessionComplete(
                                sessionId = sessionId,
                                status = status.name
                            )
                        )
                    }
                )
            }
            entry<LockInRoute.BreakGate> { route ->
                BreakGateScreen(
                    sessionId = route.sessionId,
                    penaltyAmountPaise = route.penaltyAmountPaise,
                    onSessionFinished = { sessionId, status ->
                        backStack.clear()
                        backStack.add(
                            LockInRoute.SessionComplete(
                                sessionId = sessionId,
                                status = status.name
                            )
                        )
                    },
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<LockInRoute.SessionComplete> { route ->
                SessionCompleteScreen(
                    sessionId = route.sessionId,
                    statusString = route.status,
                    onNavigateHome = {
                        backStack.clear()
                        backStack.add(LockInRoute.Home)
                    },
                    onNavigateToWallet = { openWithdrawalSheet ->
                        backStack.clear()
                        backStack.add(LockInRoute.Wallet(openWithdrawalSheet = openWithdrawalSheet))
                    }
                )
            }
            entry<LockInRoute.Wallet> { route ->
                WalletScreen(
                    openWithdrawalSheet = route.openWithdrawalSheet,
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    },
                    onNavigateHome = {
                        backStack.clear()
                        backStack.add(LockInRoute.Home)
                    }
                )
            }
            entry<LockInRoute.History> {
                HistoryScreen(
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<LockInRoute.Settings> {
                SettingsScreen(
                    onNavigateBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}

/**
 * Composable displaying a centered placeholder message.
 *
 * @param message The text to display.
 */
@Composable
private fun PlaceholderScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center
        )
    }
}
