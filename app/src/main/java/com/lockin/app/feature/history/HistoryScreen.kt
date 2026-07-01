/*
 * File: app/src/main/java/com/lockin/app/feature/history/HistoryScreen.kt
 * Purpose: Renders the LockIn focus sessions history dashboard.
 * Displays statistics cards (total locks, completion rate, longest streak, total duration),
 * chronological session history logs grouped by relative week, and a bottom sheet details timeline.
 */

package com.lockin.app.feature.history

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.lockin.app.core.domain.model.Session
import com.lockin.app.core.domain.model.SessionEvent
import com.lockin.app.core.domain.model.SessionStatus
import com.lockin.app.ui.components.EmptyState
import com.lockin.app.ui.components.LockInButton
import com.lockin.app.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Main History Composable screen.
 * Displays aggregate lock statistics and chronological session list.
 *
 * @param onNavigateBack Callback to return to previous dashboard.
 * @param viewModel The Hilt-injected HistoryViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedEvents by viewModel.selectedSessionEvents.collectAsState()

    var activeDetailSession by remember { mutableStateOf<Session?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0D0D)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is HistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOADING LOGS...",
                            color = Color(0xFF8E8E93),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is HistoryUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ERROR RETRIEVING HISTORY",
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
                is HistoryUiState.Success -> {
                    val data = state.data

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                    ) {
                        // Title Header
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
                                text = "HISTORY",
                                color = Color(0xFFF5F5F7),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (data.sessions.isEmpty()) {
                            // Empty state display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyState(
                                    icon = WarningIcon,
                                    message = "No sessions yet. Start your first LockIn.",
                                    ctaText = "START SESSION",
                                    onCtaClick = onNavigateBack
                                )
                            }
                        } else {
                            // Stats Summary Card and chronological list
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                item {
                                    HistorySummaryCard(data = data)
                                }

                                // Group sessions by relative week category
                                val groupedSessions = groupSessionsByWeek(data.sessions)
                                groupedSessions.forEach { (weekTitle, sessionsList) ->
                                    item {
                                        Text(
                                            text = weekTitle,
                                            color = Color(0xFF8E8E93),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }

                                    items(sessionsList) { session ->
                                        SessionHistoryRow(
                                            session = session,
                                            onClick = {
                                                activeDetailSession = session
                                                viewModel.loadEventsForSession(session.sessionId)
                                                showDetailSheet = true
                                            }
                                        )
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    // Audit event trail bottom sheet details panel
                    if (showDetailSheet && activeDetailSession != null) {
                        SessionDetailSheet(
                            session = activeDetailSession!!,
                            events = selectedEvents,
                            onDismiss = {
                                showDetailSheet = false
                                activeDetailSession = null
                                viewModel.clearSelectedSessionEvents()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders aggregate stats cards at the top of the history screen.
 */
@Composable
private fun HistorySummaryCard(data: HistoryUiStateData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF48484A), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FOCUS METRICS",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Two-row, two-column grid stats layout
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = "TOTAL LOCKS",
                    value = data.totalSessionsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "COMPLETION RATE",
                    value = String.format(Locale.getDefault(), "%.0f%%", data.completionRatePercentage),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = "LONGEST STREAK",
                    value = "${data.longestStreak} DAYS",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "TIME LOCKED IN",
                    value = formatTotalFocusTime(data.totalTimeLockedInMs),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFF8E8E93),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Individual chronological row card representing a single focus session.
 */
@Composable
private fun SessionHistoryRow(
    session: Session,
    onClick: () -> Unit
) {
    val durationMin = (session.targetEndTime - session.startTime) / 60000
    val durationText = if (durationMin >= 60) "${durationMin / 60}h ${durationMin % 60}m" else "${durationMin}m"
    
    val formattedTime = remember(session.startTime) {
        val dateFormat = SimpleDateFormat("MMM dd · hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        dateFormat.format(Date(session.startTime))
    }

    val isCompleted = session.status == SessionStatus.COMPLETED
    val statusText = session.status.name
    val statusColor = when (session.status) {
        SessionStatus.COMPLETED -> Color(0xFF34C759) // flat green
        SessionStatus.BROKEN -> Color(0xFFFF3B30)    // flat red
        SessionStatus.ACTIVE -> Color(0xFF0A84FF)    // flat blue
        SessionStatus.PENDING -> Color(0xFFFF9500)   // flat amber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1E))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = durationText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedTime,
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "₹${session.penaltyAmount / 100}",
                color = if (isCompleted) Color(0xFF8E8E93) else Color(0xFFFF3B30),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = ChevronRightIcon,
                contentDescription = "Details",
                tint = Color(0xFF48484A),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Granular events log timeline displayed on tapping a history row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailSheet(
    session: Session,
    events: List<SessionEvent>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val formattedDate = remember(session.startTime) {
        val format = SimpleDateFormat("EEEE, MMMM dd, yyyy · hh:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        format.format(Date(session.startTime))
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
                text = "SESSION AUDIT TRAIL",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formattedDate,
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Summary console section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, Color(0xFF48484A), RectangleShape)
                    .padding(12.dp)
            ) {
                ConsoleRow("SESSION ID", session.sessionId)
                ConsoleRow("STAKE AMOUNT", "₹${session.penaltyAmount / 100} INR")
                ConsoleRow("OUTCOME", session.status.name)
                val durationMin = (session.targetEndTime - session.startTime) / 60000
                ConsoleRow("TARGET LENGTH", "$durationMin MINUTES")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "EVENT TIMELINE",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Events timeline list
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RETRIEVING AUDIT LOGS...",
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
                    items(events) { event ->
                        val formattedTime = remember(event.timestamp) {
                            val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
                                timeZone = TimeZone.getDefault()
                            }
                            format.format(Date(event.timestamp))
                        }

                        val color = when (event.eventType) {
                            "BREAK_CONFIRMED" -> Color(0xFFFF3B30)
                            "COMPLETED" -> Color(0xFF34C759)
                            "VPN_GAP" -> Color(0xFFFF9500)
                            "BREAK_ATTEMPT" -> Color(0xFFFF9500)
                            else -> Color(0xFF8E8E93)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C2E))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[$formattedTime]",
                                color = Color(0xFF8E8E93),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(85.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.eventType,
                                    color = color,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                event.metadata?.let { meta ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = meta,
                                        color = Color(0xFF8E8E93),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LockInButton(
                text = "CLOSE TIMELINE",
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun ConsoleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            color = Color(0xFF8E8E93),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Utility function to group sessions by relative calendar weeks.
 */
private fun groupSessionsByWeek(sessions: List<Session>): Map<String, List<Session>> {
    val grouped = LinkedHashMap<String, ArrayList<Session>>()
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis

    val dateFormat = SimpleDateFormat("yyyy-ww", Locale.getDefault())
    val currentWeekYear = dateFormat.format(Date(now))
    
    calendar.add(Calendar.WEEK_OF_YEAR, -1)
    val lastWeekYear = dateFormat.format(Date(calendar.timeInMillis))

    sessions.forEach { session ->
        val sessionWeekYear = dateFormat.format(Date(session.startTime))
        val groupTitle = when (sessionWeekYear) {
            currentWeekYear -> "THIS WEEK"
            lastWeekYear -> "LAST WEEK"
            else -> {
                val sessionYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(session.startTime))
                val nowYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(now))
                if (sessionYear == nowYear) {
                    SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(session.startTime)).uppercase(Locale.getDefault())
                } else {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(session.startTime)).uppercase(Locale.getDefault())
                }
            }
        }
        if (!grouped.containsKey(groupTitle)) {
            grouped[groupTitle] = ArrayList()
        }
        grouped[groupTitle]?.add(session)
    }
    return grouped
}

/**
 * Formats focus duration milliseconds into readable text string: "Xh Ym" or "Ym".
 */
private fun formatTotalFocusTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}H ${minutes}M" else "${minutes}M"
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

private val ChevronRightIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ChevronRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineTo(16f, 12f)
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
