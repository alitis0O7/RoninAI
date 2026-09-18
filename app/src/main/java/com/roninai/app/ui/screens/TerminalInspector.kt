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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninSurface
import com.roninai.app.ui.theme.RoninSuccess
import com.roninai.app.ui.theme.RoninTextSecondary
import com.roninai.app.ui.theme.RoninError

data class TerminalEntry(
    val command: String,
    val output: String,
    val exitCode: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isToolCall: Boolean = false,
    val toolName: String? = null
) {
    val isSuccess: Boolean get() = exitCode == null || exitCode == 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalInspector(
    entries: List<TerminalEntry>,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Terminal Inspector",
                            style = MaterialTheme.typography.titleMedium,
                            color = RoninHighlight
                        )
                        Text(
                            text = "${entries.size} commands executed",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoninTextSecondary
                        )
                    }
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
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.displayLarge,
                    color = RoninHighlight.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No commands executed yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RoninTextSecondary
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(entries) { entry ->
                    TerminalEntryCard(entry = entry)
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun TerminalEntryCard(entry: TerminalEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (entry.isSuccess) RoninSuccess else RoninError,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = if (entry.isToolCall) "Tool: ${entry.toolName}" else "Command",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry.isToolCall) RoninHighlight else RoninTextSecondary
                )
            }

            entry.exitCode?.let { code ->
                Text(
                    text = "exit: $code",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (code == 0) RoninSuccess else RoninError,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command
        Text(
            text = "$ ${entry.command}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontFamily = FontFamily.Monospace,
            color = RoninHighlight
        )

        // Output
        if (entry.output.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.output,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp)
            )
        }
    }
}
