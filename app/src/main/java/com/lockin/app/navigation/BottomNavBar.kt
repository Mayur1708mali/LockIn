/*
 * File: com/lockin/app/navigation/BottomNavBar.kt
 * Purpose: Bottom Navigation Bar component for the LockIn application.
 * Manages tab switching between Home, History, and Wallet screens with high-contrast minimalist styles.
 */

package com.lockin.app.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Custom Home icon path drawn manually via Compose vector API.
 */
private val HomeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(10f, 20f)
            verticalLineTo(14f)
            horizontalLineTo(14f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            horizontalLineTo(22f)
            lineTo(12f, 3f)
            lineTo(2f, 12f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            horizontalLineTo(10f)
            close()
        }
    }.build()

/**
 * Custom History/List icon path drawn manually via Compose vector API.
 */
private val HistoryIcon: ImageVector
    get() = ImageVector.Builder(
        name = "History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(13f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 16f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            horizontalLineTo(4f)
            close()
        }
    }.build()

/**
 * Custom Wallet icon path drawn manually via Compose vector API.
 * Ensures the app has a pixel-perfect wallet representation without extra dependencies.
 */
private val WalletIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Wallet",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(21f, 18f)
            verticalLineTo(8f)
            horizontalLineTo(3f)
            verticalLineTo(18f)
            curveTo(3f, 19.1f, 3.9f, 20f, 5f, 20f)
            horizontalLineTo(19f)
            curveTo(20.1f, 20f, 21f, 19.1f, 21f, 18f)
            close()
            moveTo(19f, 12f)
            curveTo(17.9f, 12f, 17f, 11.1f, 17f, 10f)
            curveTo(17f, 8.9f, 17.9f, 8f, 19f, 8f)
            horizontalLineTo(21f)
            verticalLineTo(12f)
            horizontalLineTo(19f)
            close()
            moveTo(20f, 4f)
            horizontalLineTo(4f)
            curveTo(2.89f, 4f, 2.01f, 4.89f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.11f, 2.9f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.11f, 22f, 18f)
            verticalLineTo(6f)
            curveTo(22f, 4.89f, 21.1f, 4f, 20f, 4f)
            close()
        }
    }.build()

/**
 * Sealed class representing items on the bottom navigation bar.
 */
sealed class BottomNavItem(
    val route: LockInRoute,
    val icon: ImageVector,
    val label: String
) {
    object Home : BottomNavItem(LockInRoute.Home, HomeIcon, "HOME")
    object History : BottomNavItem(LockInRoute.History, HistoryIcon, "HISTORY")
    object Wallet : BottomNavItem(LockInRoute.Wallet(), WalletIcon, "WALLET")
}

/**
 * Renders the bottom navigation bar for top-level screens (Home, History, Wallet).
 * Applies flat styling and high-contrast colors matching the design rules in AGENTS.md.
 *
 * @param backStack The Navigation 3 back stack.
 * @param modifier Composable modifier.
 */
@Composable
fun LockInBottomNavBar(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier
) {
    val currentRoute = backStack.lastOrNull() as? LockInRoute

    // Show bottom navigation bar ONLY on Home, History, and Wallet screens
    val showBottomBar = currentRoute is LockInRoute.Home ||
            currentRoute is LockInRoute.History ||
            currentRoute is LockInRoute.Wallet

    if (!showBottomBar) return

    val navItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.History,
        BottomNavItem.Wallet
    )

    NavigationBar(
        modifier = modifier,
        containerColor = Color(0xFF1C1C1E), // Surface color
        tonalElevation = 0.dp
    ) {
        navItems.forEach { item ->
            val selected = when (item) {
                BottomNavItem.Home -> currentRoute is LockInRoute.Home
                BottomNavItem.History -> currentRoute is LockInRoute.History
                BottomNavItem.Wallet -> currentRoute is LockInRoute.Wallet
            }

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        // Switch top-level tabs by resetting the back stack
                        backStack.clear()
                        backStack.add(item.route)
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(text = item.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFFF3B30),   // Accent Red
                    selectedTextColor = Color(0xFFFF3B30),   // Accent Red
                    unselectedIconColor = Color(0xFF8E8E93), // OnSurfaceMuted
                    unselectedTextColor = Color(0xFF8E8E93), // OnSurfaceMuted
                    indicatorColor = Color.Transparent       // Minimalist look: no selection background pill
                )
            )
        }
    }
}
