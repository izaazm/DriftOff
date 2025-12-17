package com.example.myapplication.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.DrowsinessState
import com.example.myapplication.ui.components.FeedbackDialog
import com.example.myapplication.ui.theme.*
import com.example.myapplication.ui.viewmodel.SleepViewModel
import java.util.Locale

/**
 * Main dashboard screen showing drowsiness score and service controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.camera.core.ExperimentalGetImage
@Composable
fun DashboardScreen(
    viewModel: SleepViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val adaptiveMultiplier by viewModel.adaptiveMultiplier.collectAsState()
    val showFeedback by viewModel.showFeedbackDialog.collectAsState()
    
    // Real-time score from service
    val currentScore by viewModel.currentScore.collectAsState()
    val currentState by viewModel.currentState.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    
    // Animated score for smooth transitions
    val animatedScore by animateFloatAsState(
        targetValue = currentScore,
        animationSpec = tween(500),
        label = "score"
    )
    
    // Show feedback dialog
    if (showFeedback) {
        FeedbackDialog(
            onDismiss = { viewModel.dismissFeedbackDialog() },
            onSubmit = { rating, feeling, notes ->
                viewModel.submitFeedback(rating, feeling, notes)
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("DriftOff",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SleepNavy,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Analytics",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SleepNavy, SleepIndigo)
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // State indicator
                StateIndicator(
                    state = currentState,
                    isActive = isMonitoring
                )

                // Drowsiness Score Gauge
                DrowsinessGauge(
                    score = animatedScore,
                    isActive = isMonitoring
                )
                
                // Main toggle button
                ServiceToggleButton(
                    isEnabled = settings.isEnabled,
                    onClick = { viewModel.toggleService() }
                )
                
                // Quick stats
                if (isMonitoring) {
                    QuickStatsCard(
                        adaptiveMultiplier = adaptiveMultiplier,
                        sleepWindow = "${settings.startHour}:${settings.startMinute.toString().padStart(2, '0')} - ${settings.endHour}:${settings.endMinute.toString().padStart(2, '0')}"
                    )
                }
                
                // Feedback button
                OutlinedButton(
                    onClick = { viewModel.showFeedback() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MoonGlow
                    )
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rate Last Night's Sleep")
                }
            }
        }
    }
}

@Composable
private fun DrowsinessGauge(
    score: Float,
    isActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val gaugeColor = when {
        score >= 70 -> SleepingBlue
        score >= 50 -> DrowsyAmber
        score >= 30 -> SleepPurple
        else -> AwakeGreen
    }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 20.dp.toPx()

            // Background arc
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth)
            )
            
            // Progress arc
            if (isActive) {
                drawArc(
                    color = gaugeColor.copy(alpha = pulseAlpha),
                    startAngle = 135f,
                    sweepAngle = (score / 100f) * 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isActive) score.toInt().toString() else "--",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Drowsiness Score",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StateIndicator(
    state: DrowsinessState,
    isActive: Boolean
) {
    val (stateText, color, icon) = when {
        !isActive -> Triple("Inactive", Color.Gray, Icons.Default.PowerSettingsNew)
        else -> when (state) {
            DrowsinessState.LIKELY_SLEEPING -> Triple("Likely Sleeping", SleepingBlue, Icons.Default.Bedtime)
            DrowsinessState.DROWSY -> Triple("Drowsy", DrowsyAmber, Icons.Default.NightsStay)
            DrowsinessState.RELAXING -> Triple("Relaxing", SleepPurple, Icons.Default.SelfImprovement)
            DrowsinessState.AWAKE -> Triple("Awake", AwakeGreen, Icons.Default.WbSunny)
        }
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stateText,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ServiceToggleButton(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth(0.6f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) SoftRed else AwakeGreen,
            contentColor = Color.White
        ),
        shape = CircleShape
    ) {
        Icon(
            if (isEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isEnabled) "Stop Monitoring" else "Start Monitoring",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickStatsCard(
    adaptiveMultiplier: Float,
    sleepWindow: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Quick Stats",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    icon = Icons.Default.Schedule,
                    label = "Sleep Window",
                    value = sleepWindow
                )
                StatItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = "Sensitivity",
                    value = String.format(Locale.US, "%.1fx", adaptiveMultiplier)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MoonGlow.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
