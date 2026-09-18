package com.roninai.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninSurfaceElevated
import com.roninai.app.ui.theme.RoninVoiceActive

@Composable
fun ChatInputBar(
    onSendMessage: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onCameraClick: () -> Unit,
    isProcessing: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    val micColor by animateColorAsState(
        targetValue = if (isListening) RoninVoiceActive else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "mic_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RoninSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Camera button
        IconButton(
            onClick = onCameraClick,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Attach image",
                modifier = Modifier.size(22.dp)
            )
        }

        // Text input
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                ),
            placeholder = {
                Text(
                    text = if (isListening) "Listening..." else "Message RoninAI...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = RoninHighlight
            ),
            maxLines = 4,
            enabled = !isProcessing
        )

        // Voice / Send button
        if (inputText.isBlank()) {
            IconButton(
                onClick = onVoiceToggle,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isListening) RoninVoiceActive.copy(alpha = 0.2f) else Color.Transparent,
                        shape = CircleShape
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = micColor
                )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Start voice",
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isProcessing) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = RoninHighlight.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = RoninHighlight,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                ),
                enabled = inputText.isNotBlank() && !isProcessing
            ) {
                if (isProcessing) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
