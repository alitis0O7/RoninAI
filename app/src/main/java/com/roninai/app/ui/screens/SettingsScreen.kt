package com.roninai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.roninai.app.data.entity.SessionEntity
import com.roninai.app.engine.EngineInfo
import com.roninai.app.engine.EngineState
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninSurface
import com.roninai.app.ui.theme.RoninSuccess
import com.roninai.app.ui.theme.RoninTextSecondary
import com.roninai.app.ui.theme.RoninError
import com.roninai.app.ui.theme.RoninWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    engineInfo: EngineInfo,
    sessions: List<SessionEntity>,
    onBack: () -> Unit,
    onClearSession: (Long) -> Unit,
    onSessionClick: (Long) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = RoninHighlight
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoninSurface
                )
            )
        },
        containerColor = RoninSurface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Engine Status
            item {
                SectionHeader("Engine Status")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = when (engineInfo.state) {
                                    EngineState.RUNNING -> RoninSuccess
                                    EngineState.ERROR -> RoninError
                                    else -> RoninWarning
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = engineInfo.state.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (engineInfo.uptime > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Uptime: ${formatUptime(engineInfo.uptime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoninTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (engineInfo.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = engineInfo.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = RoninError
                            )
                        }
                    }
                }
            }

            // Sessions
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Sessions (${sessions.size})")
            }

            items(sessions, key = { it.id }) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onSessionClick(session.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateFormat.format(Date(session.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = RoninTextSecondary
                            )
                            if (session.workspacePath != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = RoninHighlight,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = session.workspacePath.substringAfterLast('/'),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoninHighlight
                                    )
                                }
                            }
                        }

                        Row {
                            if (session.isVoiceSession) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice session",
                                    tint = RoninHighlight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            IconButton(
                                onClick = { onClearSession(session.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete session",
                                    tint = RoninError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = RoninHighlight,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun formatUptime(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "%dh %dm %ds".format(hours, minutes % 60, seconds % 60)
        minutes > 0 -> "%dm %ds".format(minutes, seconds % 60)
        else -> "%ds".format(seconds)
    }
}
