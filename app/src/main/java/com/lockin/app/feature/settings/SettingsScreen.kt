/*
 * File: app/src/main/java/com/lockin/app/feature/settings/SettingsScreen.kt
 * Purpose: Renders the LockIn Settings screen, allowing users to configure
 * their VPN allowlist, auto top-up balances/thresholds, and saved payment methods.
 * Gated during active focus sessions to block modifications.
 */

package com.lockin.app.feature.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockin.app.core.domain.model.AllowedApp
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.LockInTextField
import com.lockin.app.ui.components.SectionHeader
import java.util.Locale

/**
 * Main Settings Composable screen.
 * Displays sections for Whitelisted Apps, Auto Top-Up configuration, and saved payments.
 * Blocks editing when an active lock session is ongoing.
 *
 * @param onNavigateBack Callback to return to the previous screen.
 * @param viewModel The Hilt-injected SettingsViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()

    var showAddAppSheet by remember { mutableStateOf(false) }
    var showPaymentVerificationDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D) // flat dark background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SettingsUiState.Loading -> {
                    // Minimalist progress indicator
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOADING CONFIG...",
                            color = Color(0xFF8E8E93),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is SettingsUiState.Error -> {
                    // Full-width flat error banner
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ERROR RETRIEVING SETTINGS",
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = Color(0xFF8E8E93),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LockInButton(
                            text = "GO BACK",
                            onClick = onNavigateBack
                        )
                    }
                }
                is SettingsUiState.Success -> {
                    val data = state.data

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                    ) {
                        // Header section with flat back arrow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = ArrowBackIcon,
                                    contentDescription = "Navigate Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "SETTINGS",
                                color = Color(0xFFF5F5F7),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active focus session warning banner (locked state)
                        if (data.isSessionActive) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(1.dp, Color(0xFFFF9500), RoundedCornerShape(4.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0x1AFF9500)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = WarningIcon,
                                        contentDescription = "Session Active",
                                        tint = Color(0xFFFF9500),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "SETTINGS LOCKED DURING ACTIVE FOCUS SESSION",
                                        color = Color(0xFFFF9500),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Scrollable settings options
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Section 1: Allowlist
                            item {
                                AllowlistSection(
                                    customApps = data.customAllowedApps,
                                    defaultApps = data.defaultAllowedApps,
                                    isLocked = data.isSessionActive,
                                    onAddAppClick = { showAddAppSheet = true },
                                    onRemoveApp = { pkg -> viewModel.removeAllowedApp(pkg) }
                                )
                            }

                            // Section 2: Auto Top-Up
                            item {
                                AutoTopUpSection(
                                    enabled = data.autoTopUpEnabled,
                                    thresholdPaise = data.autoTopUpThresholdPaise,
                                    amountPaise = data.autoTopUpAmountPaise,
                                    savedMethod = data.savedPaymentMethodLabel ?: "No Method Stored",
                                    isLocked = data.isSessionActive,
                                    onToggle = { enabled -> viewModel.toggleAutoTopUp(enabled) },
                                    onThresholdChange = { threshold -> viewModel.updateAutoTopUpThreshold(threshold) },
                                    onAmountChange = { amount -> viewModel.updateAutoTopUpAmount(amount) }
                                )
                            }

                            // Section 3: Payment Method
                            item {
                                PaymentMethodSection(
                                    savedMethod = data.savedPaymentMethodLabel ?: "No saved payment method",
                                    isLocked = data.isSessionActive,
                                    onChangeMethodClick = { showPaymentVerificationDialog = true }
                                )
                            }

                            
                            // Bottom spacing
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    // Add Custom App searchable bottom sheet panel
                    if (showAddAppSheet) {
                        AddAppSheet(
                            installedApps = data.installedApps,
                            customApps = data.customAllowedApps,
                            defaultApps = data.defaultAllowedApps,
                            onDismiss = { showAddAppSheet = false },
                            onAppSelected = { pkg, name ->
                                viewModel.addAllowedApp(pkg, name)
                                showAddAppSheet = false
                            }
                        )
                    }

                    // Simulated payment verification for change method
                    if (showPaymentVerificationDialog) {
                        AlertDialog(
                            onDismissRequest = { showPaymentVerificationDialog = false },
                            title = {
                                Text(
                                    text = "VERIFY PAYMENT METHOD",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            },
                            text = {
                                Text(
                                    text = "To register a new automatic payment method, we will execute a minimum verification deposit of ₹50. The amount will be credited to your wallet balance on success.",
                                    color = Color(0xFF8E8E93)
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showPaymentVerificationDialog = false
                                        activity?.let { act ->
                                            viewModel.changePaymentMethod(
                                                activity = act,
                                                onSuccess = {
                                                    Toast.makeText(context, "Payment Method Registered!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, "Registration Failed: $err", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "PROCEED (₹50)",
                                        color = Color(0xFFFF3B30),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showPaymentVerificationDialog = false }
                                ) {
                                    Text(
                                        text = "CANCEL",
                                        color = Color(0xFF8E8E93),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            },
                            containerColor = Color(0xFF1C1C1E),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                }
            }
        }
    }
}

/**
 * Section block managing default and custom allowed apps bypassing the VPN.
 */
@Composable
private fun AllowlistSection(
    customApps: List<AllowedApp>,
    defaultApps: List<DefaultAppInfo>,
    isLocked: Boolean,
    onAddAppClick: () -> Unit,
    onRemoveApp: (String) -> Unit
) {
    var expandedDefaultApps by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            label = "ALLOWLIST CONFIG",
            title = "VPN BYPASS"
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        // Custom Whitelisted Apps counter label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CUSTOM APPS (${customApps.size}/3)",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom allowed apps list
        if (customApps.isEmpty()) {
            Text(
                text = "No custom apps whitelisted.",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            customApps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1C1E))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            color = Color(0xFFF5F5F7),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = app.packageName,
                            color = Color(0xFF8E8E93),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(
                        onClick = { onRemoveApp(app.packageName) },
                        enabled = !isLocked,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = DeleteIcon,
                            contentDescription = "Remove App",
                            tint = if (isLocked) Color(0xFF48484A) else Color(0xFFFF3B30),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add custom app button
        LockInButton(
            text = "ADD APP TO ALLOWLIST",
            onClick = onAddAppClick,
            enabled = !isLocked && customApps.size < 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Default WHitelisted apps expandable row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedDefaultApps = !expandedDefaultApps }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LockIcon,
                contentDescription = "Default apps status",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (expandedDefaultApps) "HIDE SYSTEM DEFAULT APPS" else "SHOW SYSTEM DEFAULT APPS (5)",
                color = Color(0xFF34C759),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        if (expandedDefaultApps) {
            Spacer(modifier = Modifier.height(8.dp))
            defaultApps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = app.appName,
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "SYSTEM BYPASS",
                        color = Color(0xFF0A84FF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                HorizontalDivider(color = Color(0xFF1C1C1E))
            }
        }
    }
}

/**
 * Section handling automatic wallet recharge toggle and configuration selectors.
 */
@Composable
private fun AutoTopUpSection(
    enabled: Boolean,
    thresholdPaise: Int,
    amountPaise: Int,
    savedMethod: String,
    isLocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onAmountChange: (Int) -> Unit
) {
    val thresholds = listOf(10000, 20000, 50000, 100000) // ₹100, ₹200, ₹500, ₹1000
    val amounts = listOf(10000, 20000, 50000, 100000)

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            label = "WALLET PROTECTION",
            title = "AUTO TOP-UP"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Toggle Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1E))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AUTO TOP-UP WALLET",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Automatically recharge your wallet when available balance drops below configuration.",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF34C759),
                    checkedTrackColor = Color(0x6634C759),
                    uncheckedThumbColor = Color(0xFF8E8E93),
                    uncheckedTrackColor = Color(0xFF2C2C2E)
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Threshold Picker Selector
        Text(
            text = "MINIMUM THRESHOLD BEFORE TOP-UP",
            color = if (enabled && !isLocked) Color(0xFFF5F5F7) else Color(0xFF8E8E93),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            thresholds.forEach { amt ->
                val isSelected = thresholdPaise == amt
                val activeColor = Color(0xFFFF3B30) // Red accent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected && enabled) activeColor else Color(0xFF1C1C1E),
                            shape = RectangleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected && enabled) activeColor else Color(0xFF48484A),
                            shape = RectangleShape
                        )
                        .clickable(enabled = enabled && !isLocked) { onThresholdChange(amt) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹${amt / 100}",
                        color = if (isSelected && enabled) Color.White else if (enabled) Color(0xFFF5F5F7) else Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Top-Up Amount Picker Selector
        Text(
            text = "AUTO RECHARGE REFILL AMOUNT",
            color = if (enabled && !isLocked) Color(0xFFF5F5F7) else Color(0xFF8E8E93),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            amounts.forEach { amt ->
                val isSelected = amountPaise == amt
                val activeColor = Color(0xFFFF3B30)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected && enabled) activeColor else Color(0xFF1C1C1E),
                            shape = RectangleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected && enabled) activeColor else Color(0xFF48484A),
                            shape = RectangleShape
                        )
                        .clickable(enabled = enabled && !isLocked) { onAmountChange(amt) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹${amt / 100}",
                        color = if (isSelected && enabled) Color.White else if (enabled) Color(0xFFF5F5F7) else Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Section detailing current payment instrument and access to replace it.
 */
@Composable
private fun PaymentMethodSection(
    savedMethod: String,
    isLocked: Boolean,
    onChangeMethodClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            label = "GATEWAY CONFIG",
            title = "PAYMENT SYSTEM"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1C1E))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SAVED CARD / INSTRUMENT",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = savedMethod,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LockInButton(
            text = "CHANGE PAYMENT INSTRUMENT",
            onClick = onChangeMethodClick,
            enabled = !isLocked
        )
    }
}


/**
 * Modal Bottom Sheet enabling users to search and add installed launcher apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppSheet(
    installedApps: List<InstalledAppInfo>,
    customApps: List<AllowedApp>,
    defaultApps: List<DefaultAppInfo>,
    onDismiss: () -> Unit,
    onAppSelected: (String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    // Pre-calculate lists of whitelisted package names to filter them out of launcher search
    val customPackageNames = remember(customApps) { customApps.map { it.packageName } }
    val defaultPackageNames = remember(defaultApps) { defaultApps.map { it.packageName } }

    val filteredApps = remember(installedApps, searchQuery, customPackageNames, defaultPackageNames) {
        installedApps.filter { app ->
            // Filter out default whitelisted and already whitelisted custom apps
            !defaultPackageNames.contains(app.packageName) &&
            !customPackageNames.contains(app.packageName) &&
            (app.appName.contains(searchQuery, ignoreCase = true) ||
             app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        dragHandle = null,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "ADD APP TO BYPASS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Search filter field
            LockInTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "SEARCH INSTALLED APPS...",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO MATCHING APPLICATIONS FOUND",
                        color = Color(0xFF8E8E93),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C2E))
                                .clickable { onAppSelected(app.packageName, app.appName) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = app.packageName,
                                    color = Color(0xFF8E8E93),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Icon(
                                imageVector = AddIcon,
                                contentDescription = "Add app",
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LockInButton(
                text = "CLOSE",
                onClick = onDismiss
            )
        }
    }
}

/*
 * Custom Vector Icons drawn to prevent external dependencies.
 */
private val ArrowBackIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.8f)
            lineTo(13.4f, 5.4f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.4f, 18.6f)
            lineTo(7.8f, 13f)
            horizontalLineTo(20f)
            close()
        }
    }.build()

private val WarningIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Warning",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color(0xFFFF9500))) {
            moveTo(1f, 21f)
            horizontalLineTo(23f)
            lineTo(12f, 2f)
            lineTo(1f, 21f)
            close()
            moveTo(13f, 18f)
            horizontalLineTo(11f)
            verticalLineTo(16f)
            horizontalLineTo(13f)
            verticalLineTo(18f)
            close()
            moveTo(13f, 14f)
            horizontalLineTo(11f)
            verticalLineTo(10f)
            horizontalLineTo(13f)
            verticalLineTo(14f)
            close()
        }
    }.build()

private val DeleteIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Delete",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            horizontalLineTo(16f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            close()
            moveTo(19f, 4f)
            horizontalLineTo(15.5f)
            lineTo(14.5f, 3f)
            horizontalLineTo(9.5f)
            lineTo(8.5f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(19f)
            verticalLineTo(4f)
            close()
        }
    }.build()

private val LockIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Lock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(18f, 8f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            curveTo(17f, 3.2f, 14.8f, 1f, 12f, 1f)
            curveTo(9.2f, 1f, 7f, 3.2f, 7f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(6f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
            close()
            moveTo(9f, 6f)
            curveTo(9f, 4.3f, 10.3f, 3f, 12f, 3f)
            curveTo(13.7f, 3f, 15f, 4.3f, 15f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(9f)
            verticalLineTo(6f)
            close()
        }
    }.build()

private val AddIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Add",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(19f, 13f)
            horizontalLineTo(13f)
            verticalLineTo(19f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(11f)
            verticalLineTo(5f)
            horizontalLineTo(13f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            close()
        }
    }.build()
