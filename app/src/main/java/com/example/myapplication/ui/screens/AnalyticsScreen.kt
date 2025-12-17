package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.SleepAnalyticsSummary
import com.example.myapplication.data.model.SleepSession
import com.example.myapplication.data.model.SleepTrend
import com.example.myapplication.ui.theme.*
import java.time.format.DateTimeFormatter

/**
 * Analytics screen showing sleep insights and history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    summary: SleepAnalyticsSummary,
    recentSessions: List<SleepSession>,
    onNavigateBack: () -> Unit,
    onRefreshData: () -> Unit = {}
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    LaunchedEffect(Unit) {
        onRefreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SleepNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            item {
                SummaryCard(summary)
            }
            
            // Trend Indicator
            item {
                TrendCard(summary.sleepTrend)
            }
            
            // Quick Stats Row
            item {
                QuickStatsRow(summary)
            }
            
            // Recent Sessions Header
            item {
                Text(
                    "Recent Nights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            // Session List
            if (recentSessions.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(recentSessions) { session ->
                    SessionCard(session, dateFormatter)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: SleepAnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SleepIndigo
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Last ${summary.totalNights} Nights",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                val hours = summary.averageSleepDurationMinutes / 60
                val minutes = summary.averageSleepDurationMinutes % 60
                
                Text(
                    text = hours.toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "h ",
                    fontSize = 24.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = minutes.toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "m",
                    fontSize = 24.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Text(
                "Average Sleep Duration",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TrendCard(trend: SleepTrend) {
    val (icon, color, message) = when (trend) {
        SleepTrend.IMPROVING -> Triple(
            Icons.AutoMirrored.Filled.TrendingUp,
            AwakeGreen,
            "Your sleep is improving! 🎉"
        )
        SleepTrend.DECLINING -> Triple(
            Icons.AutoMirrored.Filled.TrendingDown,
            Color(0xFFE57373),
            "Your sleep quality is declining"
        )
        SleepTrend.STABLE -> Triple(
            Icons.AutoMirrored.Filled.TrendingFlat,
            DrowsyAmber,
            "Your sleep is consistent"
        )
        SleepTrend.INSUFFICIENT_DATA -> Triple(
            Icons.Default.Info,
            Color.Gray,
            "Keep tracking for insights"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
private fun QuickStatsRow(summary: SleepAnalyticsSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatBox(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bedtime,
            label = "Avg Bedtime",
            value = summary.averageBedtime,
            color = SleepPurple
        )
        StatBox(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.WbSunny,
            label = "Avg Wake",
            value = summary.averageWakeTime,
            color = DrowsyAmber
        )
        StatBox(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Timer,
            label = "Time to Sleep",
            value = "${summary.averageTimeToFallAsleepMinutes}m",
            color = SleepingBlue
        )
    }
}

@Composable
private fun StatBox(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionCard(session: SleepSession, dateFormatter: DateTimeFormatter) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    session.date.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                val hours = session.totalSleepMinutes / 60
                val minutes = session.totalSleepMinutes % 60
                Text(
                    "${hours}h ${minutes}m",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hours >= 7) AwakeGreen else if (hours >= 5) DrowsyAmber else Color(0xFFE57373)
                )
                
                if (session.disturbanceCount > 0) {
                    Text(
                        "${session.disturbanceCount} disturbance${if (session.disturbanceCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Sleep quality indicator based on duration
                val qualityEmoji = when {
                    session.totalSleepMinutes >= 420 -> "😊"  // 7+ hours
                    session.totalSleepMinutes >= 360 -> "😐"  // 6+ hours
                    session.totalSleepMinutes >= 300 -> "😟"  // 5+ hours
                    else -> "😴"
                }
                Text(
                    qualityEmoji,
                    fontSize = 32.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.NightsStay,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No sleep data yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Start monitoring to track your sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
