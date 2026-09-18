package com.roninai.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.roninai.app.data.entity.MessageEntity
import com.roninai.app.data.entity.MessageRole
import com.roninai.app.data.entity.MessageType
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninToolCall
import com.roninai.app.ui.theme.RoninUserBubble

@Composable
fun ChatMessageBubble(
    message: MessageEntity,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL_CALL || message.role == MessageRole.TOOL_RESULT

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Adb,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = when {
                    isUser -> "You"
                    isTool -> "Tool"
                    else -> "RoninAI"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (isUser) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(RoninUserBubble),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Message bubble
        val bubbleColor = when {
            isUser -> RoninUserBubble
            isTool -> RoninToolCall
            else -> MaterialTheme.colorScheme.surface
        }

        val cornerRadius = when {
            isUser -> RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            else -> RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(cornerRadius)
                .background(bubbleColor)
                .padding(12.dp)
                .animateContentSize()
        ) {
            Column {
                if (message.imageBase64 != null) {
                    Text(
                        text = "[Image attached]",
                        style = MaterialTheme.typography.labelMedium,
                        color = RoninHighlight
                    )
                }

                Text(
                    text = message.content.ifEmpty {
                        if (message.isStreaming) "Thinking..." else ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (isTool) FontFamily.Monospace else FontFamily.Default
                )

                if (message.toolName != null) {
                    Text(
                        text = "Tool: ${message.toolName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoninHighlight.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (message.cached) {
                    Text(
                        text = "cached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (message.errorMessage != null) {
                    Text(
                        text = message.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
