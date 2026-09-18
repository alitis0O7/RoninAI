package com.roninai.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roninai.app.data.entity.MessageEntity
import com.roninai.app.ui.components.ChatInputBar
import com.roninai.app.ui.components.ChatMessageBubble
import com.roninai.app.ui.components.VoiceWaveform
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninSurface
import com.roninai.app.ui.theme.RoninVoiceActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: Long,
    messages: List<MessageEntity>,
    isProcessing: Boolean,
    streamingContent: String,
    voiceAmplitude: Float,
    isVoiceListening: Boolean,
    voiceState: String,
    workspacePath: String?,
    onSendMessage: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onVoiceToggle: () -> Unit,
    onOpenDiff: (String, String) -> Unit,
    onOpenTerminal: () -> Unit,
    onAssignFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewSession: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        // Handle camera capture - would convert to base64
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onSendImage(it) }
    }

    var showImageOptions by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RoninAI",
                            style = MaterialTheme.typography.titleLarge,
                            color = RoninHighlight
                        )
                        if (workspacePath != null) {
                            Text(
                                text = workspacePath.substringAfterLast('/'),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New session",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onAssignFolder) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Assign workspace",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onOpenTerminal) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Terminal",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoninSurface
                )
            )
        },
        floatingActionButton = {
            if (isVoiceListening) {
                FloatingActionButton(
                    onClick = onVoiceToggle,
                    containerColor = RoninVoiceActive.copy(alpha = 0.3f),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop voice",
                        tint = RoninVoiceActive
                    )
                }
            }
        },
        containerColor = RoninSurface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Voice waveform overlay
            AnimatedVisibility(
                visible = isVoiceListening,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(RoninSurface.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    VoiceWaveform(
                        amplitude = voiceAmplitude,
                        isActive = isVoiceListening,
                        size = 120.dp
                    )
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Empty state
                if (messages.isEmpty() && streamingContent.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "RONIN",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = RoninHighlight.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "How can I help you?",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(message = message)
                }

                // Streaming indicator
                if (streamingContent.isNotEmpty() && isProcessing) {
                    item {
                        ChatMessageBubble(
                            message = MessageEntity(
                                sessionId = sessionId,
                                role = com.roninai.app.data.entity.MessageRole.ASSISTANT,
                                content = streamingContent,
                                isStreaming = true
                            )
                        )
                    }
                }
            }

            // Input bar
            ChatInputBar(
                onSendMessage = onSendMessage,
                onVoiceToggle = onVoiceToggle,
                onCameraClick = {
                    galleryLauncher.launch("image/*")
                },
                isProcessing = isProcessing,
                isListening = isVoiceListening,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
