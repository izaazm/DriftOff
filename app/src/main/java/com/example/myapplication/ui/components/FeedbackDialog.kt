package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.WakeUpFeeling

/**
 * Dialog for collecting morning sleep feedback from the user.
 */
@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, feeling: WakeUpFeeling, notes: String?) -> Unit
) {
    var rating by remember { mutableIntStateOf(3) }
    var selectedFeeling by remember { mutableStateOf(WakeUpFeeling.OKAY) }
    var notes by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Good Morning! ☀️",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "How well did you sleep last night?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                // Star rating
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { rating = star }
                        ) {
                            Icon(
                                imageVector = if (star <= rating) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Filled.StarBorder
                                },
                                contentDescription = "$star stars",
                                tint = if (star <= rating) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                
                Text(
                    text = "How do you feel?",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Feeling selection
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeelingButton(
                        emoji = "😫",
                        feeling = WakeUpFeeling.TERRIBLE,
                        selected = selectedFeeling == WakeUpFeeling.TERRIBLE,
                        onClick = { selectedFeeling = WakeUpFeeling.TERRIBLE }
                    )
                    FeelingButton(
                        emoji = "😞",
                        feeling = WakeUpFeeling.POOR,
                        selected = selectedFeeling == WakeUpFeeling.POOR,
                        onClick = { selectedFeeling = WakeUpFeeling.POOR }
                    )
                    FeelingButton(
                        emoji = "😐",
                        feeling = WakeUpFeeling.OKAY,
                        selected = selectedFeeling == WakeUpFeeling.OKAY,
                        onClick = { selectedFeeling = WakeUpFeeling.OKAY }
                    )
                    FeelingButton(
                        emoji = "😊",
                        feeling = WakeUpFeeling.GOOD,
                        selected = selectedFeeling == WakeUpFeeling.GOOD,
                        onClick = { selectedFeeling = WakeUpFeeling.GOOD }
                    )
                    FeelingButton(
                        emoji = "😄",
                        feeling = WakeUpFeeling.GREAT,
                        selected = selectedFeeling == WakeUpFeeling.GREAT,
                        onClick = { selectedFeeling = WakeUpFeeling.GREAT }
                    )
                }
                
                // Optional notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, selectedFeeling, notes.ifBlank { null }) }
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}

@Composable
private fun FeelingButton(
    emoji: String,
    feeling: WakeUpFeeling, // why the hell is this registered as unused??
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            null
        } else {
            ButtonDefaults.outlinedButtonBorder(enabled = true)
        },
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
