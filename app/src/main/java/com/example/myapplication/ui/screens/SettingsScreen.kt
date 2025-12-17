package com.example.myapplication.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.viewmodel.SleepViewModel
import java.util.Locale

/**
 * Settings screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.camera.core.ExperimentalGetImage
@Composable
fun SettingsScreen(
    viewModel: SleepViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val adaptiveMultiplier by viewModel.adaptiveMultiplier.collectAsState()
    val context = LocalContext.current

    // Check permissions
    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    val hasMicrophonePermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Schedule Section
            SettingsSection(title = "Sleep Schedule", icon = Icons.Default.Schedule) {
                TimePickerRow(
                    label = "Start Time",
                    hour = settings.startHour,
                    minute = settings.startMinute,
                    onTimeSelected = { h, m ->
                        viewModel.updateSettings(settings.copy(startHour = h, startMinute = m))
                    }
                )
                TimePickerRow(
                    label = "End Time",
                    hour = settings.endHour,
                    minute = settings.endMinute,
                    onTimeSelected = { h, m ->
                        viewModel.updateSettings(settings.copy(endHour = h, endMinute = m))
                    }
                )
            }
            
            HorizontalDivider()
            
            // Phone Adjustments Section
            SettingsSection(title = "Phone Adjustments", icon = Icons.Default.PhoneAndroid) {
                SwitchRow(
                    label = "Adjust Brightness",
                    description = "Reduce screen brightness when drowsy",
                    checked = settings.adjustBrightness,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(adjustBrightness = it))
                    }
                )
                
                if (settings.adjustBrightness) {
                    SliderRow(
                        label = "Target Brightness",
                        value = settings.targetBrightness,
                        valueRange = 0.05f..0.5f,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(targetBrightness = it))
                        },
                        valueLabel = "${(settings.targetBrightness * 100).toInt()}%"
                    )

                    SliderRow(
                        label = "Max Brightness Level",
                        value = settings.maxBrightness.toFloat(),
                        valueRange = 50f..255f,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(maxBrightness = it.toInt()))
                        },
                        valueLabel = settings.maxBrightness.toString()
                    )
                }
                
                SwitchRow(
                    label = "Adjust Volume",
                    description = "Reduce media and notification volume",
                    checked = settings.adjustVolume,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(adjustVolume = it))
                    }
                )
                
                if (settings.adjustVolume) {
                    SliderRow(
                        label = "Target Volume",
                        value = settings.targetVolume,
                        valueRange = 0f..0.3f,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(targetVolume = it))
                        },
                        valueLabel = "${(settings.targetVolume * 100).toInt()}%"
                    )
                }
                
                SwitchRow(
                    label = "Enable Do Not Disturb",
                    description = "Block notifications when sleeping",
                    checked = settings.enableDnd,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(enableDnd = it))
                    }
                )
            }
            
            HorizontalDivider()

            // Audio Detection Section
            SettingsSection(title = "Audio Detection", icon = Icons.Default.Mic) {
                SwitchRow(
                    label = "Enable Audio Sampling",
                    description = "Use microphone to detect ambient noise levels (opt-in for privacy)",
                    checked = settings.useAudioSampling,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(useAudioSampling = it))
                    }
                )

                // Show warning if enabled but no permission
                if (settings.useAudioSampling && !hasMicrophonePermission) {
                    PermissionWarning(
                        message = "Microphone permission not granted. Audio sampling will not work until you grant permission in app settings."
                    )
                }
            }

            HorizontalDivider()
            
            // Detection Thresholds Section
            SettingsSection(title = "Detection Thresholds", icon = Icons.Default.Tune) {
                SliderRow(
                    label = "Drowsy Threshold",
                    value = settings.drowsyThreshold.toFloat(),
                    valueRange = 30f..60f,
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(drowsyThreshold = it.toInt()))
                    },
                    valueLabel = settings.drowsyThreshold.toString()
                )
                
                SliderRow(
                    label = "Sleeping Threshold",
                    value = settings.sleepingThreshold.toFloat(),
                    valueRange = 60f..90f,
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(sleepingThreshold = it.toInt()))
                    },
                    valueLabel = settings.sleepingThreshold.toString()
                )
            }
            
            HorizontalDivider()
            
            // Camera Verification Section
            SettingsSection(title = "Camera Verification", icon = Icons.Default.CameraFront) {
                SwitchRow(
                    label = "Enable Camera Verification",
                    description = "Use front camera to verify sleep state (uses battery)",
                    checked = settings.enableCameraVerification,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(enableCameraVerification = it))
                    }
                )
                
                // Show warning if enabled but no permission
                if (settings.enableCameraVerification && !hasCameraPermission) {
                    PermissionWarning(
                        message = "Camera permission not granted. Sleep verification will not work until you grant permission in app settings."
                    )
                }

                if (settings.enableCameraVerification) {
                    SliderRow(
                        label = "Verification Duration",
                        value = settings.cameraVerificationDuration.toFloat(),
                        valueRange = 5f..20f,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(cameraVerificationDuration = it.toInt()))
                        },
                        valueLabel = "${settings.cameraVerificationDuration}s"
                    )
                }
            }
            
            HorizontalDivider()
            
            // Adaptive Learning Section
            SettingsSection(title = "Adaptive Learning", icon = Icons.Default.Psychology) {
                ListItem(
                    headlineContent = { Text("Current Sensitivity Multiplier") },
                    supportingContent = {
                        Text("Adjusted based on your morning feedback")
                    },
                    trailingContent = {
                        Text(
                            String.format(Locale.US, "%.2fx", adaptiveMultiplier),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetMultiplier() }
                    ) {
                        Text("Reset to Default")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(
    label: String,
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute
    )
    
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            TextButton(onClick = { showPicker = true }) {
                Text(
                    String.format(Locale.US, "%02d:%02d", hour, minute),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    )
    
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Select $label") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(timePickerState.hour, timePickerState.minute)
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionWarning(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD) // Amber warning color
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFF856404)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF856404)
            )
        }
    }
}
