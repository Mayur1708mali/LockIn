/*
 * File: C:/Users/mayur/AndroidStudioProjects/LockIn/app/src/main/java/com/lockin/app/feature/wallet/WalletScreen.kt
 * Purpose: Renders the LockIn Wallet screen, presenting balances, transaction logs,
 * manual deposits sheet (with mock Razorpay payments), and secure withdrawals sheet (with biometric validation).
 */

package com.lockin.app.feature.wallet

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockin.app.core.domain.model.TransactionType
import com.lockin.app.core.domain.model.Wallet
import com.lockin.app.core.domain.model.WalletTransaction
import com.lockin.app.ui.components.EmptyState
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.LockInTextField
import com.lockin.app.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Composable for the LockIn Wallet screen.
 * Displays current available/held balances, lists historical transaction logs,
 * and manages bottom sheets for depositing and withdrawing funds.
 *
 * @param openWithdrawalSheet True to immediately pop open the withdrawal panel.
 * @param onNavigateBack Callback to navigate back to the previous screen.
 * @param onNavigateHome Callback to return to the Home dashboard (e.g. CTA).
 * @param viewModel Hilt-injected viewmodel for processing wallet interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    openWithdrawalSheet: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: WalletViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsState()

    var showAddMoneySheet by remember { mutableStateOf(false) }
    var showWithdrawSheet by remember { mutableStateOf(false) }
    var showMockPaymentDialog by remember { mutableStateOf(false) }

    // Auto-open withdrawal sheet if requested by navigation parameters
    LaunchedEffect(openWithdrawalSheet) {
        if (openWithdrawalSheet) {
            showWithdrawSheet = true
        }
    }

    // React to successful deposit/withdrawal actions by showing feedback toast and closing sheets
    LaunchedEffect(uiState.depositSuccess) {
        if (uiState.depositSuccess) {
            Toast.makeText(context, "Deposit Successful!", Toast.LENGTH_SHORT).show()
            showAddMoneySheet = false
            viewModel.clearErrors()
        }
    }

    LaunchedEffect(uiState.withdrawalSuccess) {
        if (uiState.withdrawalSuccess) {
            Toast.makeText(context, "Withdrawal Request Submitted!", Toast.LENGTH_LONG).show()
            showWithdrawSheet = false
            viewModel.clearErrors()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D) // Minimalist #0D0D0D background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                            contentDescription = "Back",
                            tint = Color(0xFFF5F5F7)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SectionHeader(
                        label = "WALLET BALANCE & TRANSACTIONS",
                        title = "MY WALLET"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.isWalletLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOADING WALLET...",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF8E8E93)
                        )
                    }
                } else {
                    // Balance display card structure
                    BalanceSection(
                        wallet = uiState.wallet,
                        onAddMoneyClick = {
                            viewModel.clearErrors()
                            showAddMoneySheet = true
                        },
                        onWithdrawClick = {
                            viewModel.clearErrors()
                            showWithdrawSheet = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // History ledger
                Text(
                    text = "TRANSACTION HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E8E93)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isTransactionsLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOADING HISTORY...",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF8E8E93)
                        )
                    }
                } else if (uiState.transactions.isEmpty()) {
                    // Empty state (Task 13.7)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = WalletIcon,
                            message = "No transactions yet. Start your first session.",
                            ctaText = "LOCK IN NOW",
                            onCtaClick = onNavigateHome
                        )
                    }
                } else {
                    TransactionList(
                        transactions = uiState.transactions,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Modal Add Money bottom sheet (Task 13.4)
    if (showAddMoneySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddMoneySheet = false
                viewModel.clearErrors()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1C1C1E), // Flat Surface #1C1C1E
            contentColor = Color(0xFFF5F5F7),
            shape = RectangleShape, // Design System: 0dp bottom sheets
            dragHandle = null
        ) {
            AddMoneySheetContent(
                amountPaise = uiState.depositAmountPaise,
                isProcessing = uiState.isDepositProcessing,
                error = uiState.depositError,
                onSelectPreset = { amtRupees ->
                    viewModel.initiateDeposit(amtRupees * 100)
                },
                onCustomAmountChange = { text ->
                    val rupees = text.toIntOrNull() ?: 0
                    viewModel.initiateDeposit(rupees * 100)
                },
                onCheckoutClick = {
                    activity?.let { viewModel.startDeposit(it) }
                },
                onClose = {
                    showAddMoneySheet = false
                    viewModel.clearErrors()
                }
            )
        }
    }

    // Modal Withdraw bottom sheet (Task 13.5)
    if (showWithdrawSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showWithdrawSheet = false
                viewModel.clearErrors()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1C1C1E), // Flat Surface #1C1C1E
            contentColor = Color(0xFFF5F5F7),
            shape = RectangleShape, // Design System: 0dp bottom sheets
            dragHandle = null
        ) {
            WithdrawSheetContent(
                availableBalancePaise = uiState.wallet?.availableBalance ?: 0,
                withdrawAmountRupees = uiState.withdrawAmountRupees,
                isProcessing = uiState.isWithdrawalProcessing,
                error = uiState.withdrawalError,
                isBiometricAvailable = viewModel.isBiometricAvailable(),
                onAmountChange = { viewModel.updateWithdrawAmount(it) },
                onWithdrawClick = {
                    activity?.let { viewModel.initiateWithdrawal(it) }
                },
                onSimulateBiometric = {
                    viewModel.simulateBiometricWithdrawal()
                },
                onClose = {
                    showWithdrawSheet = false
                    viewModel.clearErrors()
                }
            )
        }
    }

    // Razorpay Mock Payment Gateway dialog (Prior to Phase 14 full integration)
    if (showMockPaymentDialog) {
        val displayAmountRupees = uiState.depositAmountPaise / 100.0
        AlertDialog(
            onDismissRequest = { showMockPaymentDialog = false },
            title = {
                Text(
                    text = "Razorpay Mock Gateway",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Simulate UPI deposit payment of ₹${String.format(Locale.getDefault(), "%.2f", displayAmountRupees)} to credit in-app wallet balance."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMockPaymentDialog = false
                        viewModel.handleDepositSuccess("pay_mock_${System.currentTimeMillis()}")
                    }
                ) {
                    Text("SIMULATE SUCCESS", color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMockPaymentDialog = false
                        viewModel.handleDepositFailure("Payment cancelled by user")
                    }
                ) {
                    Text("SIMULATE FAILURE", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF2C2C2E), // Surface variant #2C2C2E
            titleContentColor = Color(0xFFF5F5F7),
            textContentColor = Color(0xFF8E8E93),
            shape = RoundedCornerShape(8.dp) // Max 8dp cards
        )
    }
}

/**
 * Custom layout rendering the available and held balances in flat layout.
 */
@Composable
private fun BalanceSection(
    wallet: Wallet?,
    onAddMoneyClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    val available = wallet?.availableBalance ?: 0
    val held = wallet?.heldBalance ?: 0
    val autoTopUp = wallet?.autoTopUpEnabled ?: false

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), // Surface #1C1C1E
        shape = RoundedCornerShape(8.dp), // Max 8dp card corners
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF48484A), RoundedCornerShape(8.dp)) // Gray Outline #48484A
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "AVAILABLE BALANCE",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPaiseToRupees(available),
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF5F5F7)
                )

                if (autoTopUp) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF34C759).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF34C759), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AUTO TOP-UP ON",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Held Balance row displayed strictly if active session holds money (Task 13.3)
            if (held > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF48484A))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = LockIcon,
                            contentDescription = "Held",
                            tint = Color(0xFFFF9500), // AccentAmber for warnings/locks
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Held in Active Session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF9500),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = formatPaiseToRupees(held),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9500)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action CTAs (Primary red-fill and Secondary outlined)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LockInButton(
                        text = "ADD MONEY",
                        onClick = onAddMoneyClick
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    LockInButton(
                        text = "WITHDRAW",
                        onClick = onWithdrawClick,
                        isSecondary = true
                    )
                }
            }
        }
    }
}

/**
 * Modal contents for manual Razorpay deposits sheet.
 */
@Composable
private fun AddMoneySheetContent(
    amountPaise: Int,
    isProcessing: Boolean,
    error: String?,
    onSelectPreset: (Int) -> Unit,
    onCustomAmountChange: (String) -> Unit,
    onCheckoutClick: () -> Unit,
    onClose: () -> Unit
) {
    val presets = listOf(100, 200, 500, 1000)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ADD MONEY TO WALLET",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Black,
                color = Color(0xFFF5F5F7)
            )
            Text(
                text = "CANCEL",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFFFF3B30),
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(4.dp),
                fontWeight = FontWeight.Bold
            )
        }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.1f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = error,
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Horizontal preset chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SELECT PRESET AMOUNT",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { amt ->
                    val selected = (amountPaise == amt * 100)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (selected) Color(0xFFFF3B30).copy(alpha = 0.1f) else Color(0xFF2C2C2E),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) Color(0xFFFF3B30) else Color(0xFF48484A),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectPreset(amt) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "₹$amt",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color(0xFFFF3B30) else Color(0xFFF5F5F7)
                        )
                    }
                }
            }
        }

        // Custom amount input (Rupees)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "OR ENTER CUSTOM AMOUNT (₹)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold
            )
            LockInTextField(
                value = if (amountPaise > 0) (amountPaise / 100).toString() else "",
                onValueChange = onCustomAmountChange,
                placeholder = "Minimum ₹50",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val displayAmountRupees = amountPaise / 100
        LockInButton(
            text = "Secure Checkout (₹$displayAmountRupees)",
            enabled = amountPaise >= 5000 && !isProcessing,
            onClick = onCheckoutClick
        )
    }
}

/**
 * Modal contents for manual withdrawals sheet.
 */
@Composable
private fun WithdrawSheetContent(
    availableBalancePaise: Int,
    withdrawAmountRupees: String,
    isProcessing: Boolean,
    error: String?,
    isBiometricAvailable: Boolean,
    onAmountChange: (String) -> Unit,
    onWithdrawClick: () -> Unit,
    onSimulateBiometric: () -> Unit,
    onClose: () -> Unit
) {
    val amountInRupees = withdrawAmountRupees.toIntOrNull() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WITHDRAW TO BANK",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Black,
                color = Color(0xFFF5F5F7)
            )
            Text(
                text = "CANCEL",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFFFF3B30),
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(4.dp),
                fontWeight = FontWeight.Bold
            )
        }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.1f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = error,
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Available balance preview row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C2C2E), RoundedCornerShape(4.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Available Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93)
            )
            Text(
                text = formatPaiseToRupees(availableBalancePaise),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5F5F7)
            )
        }

        // Custom amount input (Rupees)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "WITHDRAW AMOUNT (₹)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold
            )
            LockInTextField(
                value = withdrawAmountRupees,
                onValueChange = onAmountChange,
                placeholder = "Minimum ₹50",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        // Destination details
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "WITHDRAWAL DESTINATION",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Saved Bank UPI / Account (Managed via Razorpay)",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF5F5F7)
            )
        }

        // Processing disclaimer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF48484A), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = WarningIcon,
                    contentDescription = "Alert",
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Withdrawals are manually processed in 3–5 bank working days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Biometric Trigger Button or Emulator Developer Mode Bypass Card
        if (isBiometricAvailable) {
            LockInButton(
                text = "BIOMETRIC WITHDRAW",
                enabled = amountInRupees >= 50 && (amountInRupees * 100) <= availableBalancePaise && !isProcessing,
                onClick = onWithdrawClick
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF9500), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DEVELOPER BYPASS MODE",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9500)
                    )
                    Text(
                        text = "No strong biometric scanner enrolled. Press button to simulate authentication check.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF5F5F7),
                        textAlign = TextAlign.Center
                    )
                    LockInButton(
                        text = "Simulate Auth & Withdraw",
                        enabled = amountInRupees >= 50 && (amountInRupees * 100) <= availableBalancePaise && !isProcessing,
                        onClick = onSimulateBiometric
                    )
                }
            }
        }
    }
}

/**
 * Ledger showing list of all past transactions.
 */
@Composable
private fun TransactionList(
    transactions: List<WalletTransaction>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(transactions) { tx ->
            TransactionRow(tx = tx)
        }
    }
}

/**
 * Single ledger row mapping direction colors and icons.
 */
@Composable
private fun TransactionRow(tx: WalletTransaction) {
    val isCredit = (tx.direction == "CREDIT")
    val displayAmount = formatPaiseToRupees(tx.amount)
    
    // Select flat colors based on direction
    val amountColor = if (isCredit) Color(0xFF34C759) else Color(0xFFFF3B30) // Green credit vs Red debit
    val sign = if (isCredit) "+" else "-"

    val icon: ImageVector = when (tx.type) {
        TransactionType.DEPOSIT -> AddIcon
        TransactionType.WITHDRAWAL -> KeyboardArrowRightIcon
        TransactionType.AUTO_TOPUP -> AddIcon
        TransactionType.SESSION_HOLD -> LockIcon
        TransactionType.SESSION_RELEASE -> ArrowDownwardIcon
        TransactionType.PENALTY -> ArrowUpwardIcon
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flat circular icon representation
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF2C2C2E), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCredit) Color(0xFF34C759) else Color(0xFF8E8E93),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Transaction details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = tx.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5F5F7)
            )
            Text(
                text = formatDate(tx.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E8E93)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Signed Rupees
        Text(
            text = "$sign$displayAmount",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = amountColor,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Format timestamp into readable calendar strings.
 */
private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(date)
}

/**
 * Translates integer Paise balance units into standard Rupee currency representations.
 */
private fun formatPaiseToRupees(paise: Int): String {
    val rupees = paise / 100.0
    return if (paise % 100 == 0) {
        "₹${paise / 100}"
    } else {
        String.format(Locale.getDefault(), "₹%.2f", rupees)
    }
}

/**
 * Custom vector icons drawn manually to bypass dependency limits.
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

private val WalletIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Wallet",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(21f, 18f)
            verticalLineTo(6f)
            curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f)
            horizontalLineTo(5f)
            curveTo(3.9f, 4f, 3f, 4.9f, 3f, 6f)
            verticalLineTo(18f)
            curveTo(3f, 19.1f, 3.9f, 20f, 5f, 20f)
            horizontalLineTo(19f)
            curveTo(20.1f, 20f, 21f, 19.1f, 21f, 18f)
            close()
            moveTo(19f, 16f)
            horizontalLineTo(16f)
            curveTo(14.9f, 16f, 14f, 15.1f, 14f, 14f)
            curveTo(14f, 12.9f, 14.9f, 12f, 16f, 12f)
            horizontalLineTo(19f)
            verticalLineTo(16f)
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
            curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
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
            curveTo(9f, 4.34f, 10.34f, 3f, 12f, 3f)
            curveTo(13.66f, 3f, 15f, 4.34f, 15f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(9f)
            verticalLineTo(6f)
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
        path(fill = SolidColor(Color.White)) {
            moveTo(1f, 21f)
            horizontalLineTo(23f)
            lineTo(12f, 2f)
            close()
            moveTo(12f, 18f)
            curveTo(11.4f, 18f, 11f, 17.6f, 11f, 17f)
            curveTo(11f, 16.4f, 11.4f, 16f, 12f, 16f)
            curveTo(12.6f, 16f, 13f, 16.4f, 13f, 17f)
            curveTo(13f, 17.6f, 12.6f, 18f, 12f, 18f)
            close()
            moveTo(13f, 14f)
            horizontalLineTo(11f)
            verticalLineTo(10f)
            horizontalLineTo(13f)
            close()
        }
    }.build()

private val ArrowDownwardIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowDownward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 12f)
            lineTo(18.59f, 10.59f)
            lineTo(13f, 16.17f)
            verticalLineTo(4f)
            horizontalLineTo(11f)
            verticalLineTo(16.17f)
            lineTo(5.41f, 10.59f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            close()
        }
    }.build()

private val ArrowUpwardIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowUpward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 12f)
            lineTo(5.41f, 13.41f)
            lineTo(11f, 7.83f)
            verticalLineTo(20f)
            horizontalLineTo(13f)
            verticalLineTo(7.83f)
            lineTo(18.59f, 13.41f)
            lineTo(20f, 12f)
            lineTo(12f, 4f)
            close()
        }
    }.build()

private val KeyboardArrowRightIcon: ImageVector
    get() = ImageVector.Builder(
        name = "KeyboardArrowRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(8.59f, 16.59f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 7.41f)
            lineTo(10f, 6f)
            lineTo(16f, 12f)
            lineTo(10f, 18f)
            close()
        }
    }.build()

