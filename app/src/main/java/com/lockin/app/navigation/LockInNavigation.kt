/*
 * File: com/lockin/app/navigation/LockInNavigation.kt
 * Purpose: Top-level navigation coordinator that displays a loading screen
 * while determining the startup destination, then builds the NavDisplay with LockInNavGraph.
 */

package com.lockin.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Top-level Composable representing the main navigation entry point for the LockIn application.
 * Collects state from [LaunchViewModel] and instantiates the back stack once the initial route is resolved.
 *
 * @param viewModel The LaunchViewModel used to resolve starting destination.
 */
@Composable
fun LockInNavigation(
    viewModel: LaunchViewModel = viewModel()
) {
    val launchState by viewModel.launchState.collectAsState()

    when (val state = launchState) {
        is LaunchState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is LaunchState.Destination -> {
            val backStack = rememberNavBackStack(state.route)
            LockInNavGraph(backStack = backStack)
        }
    }
}
