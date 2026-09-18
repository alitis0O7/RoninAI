package com.roninai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roninai.app.ui.theme.RoninDiffAdded
import com.roninai.app.ui.theme.RoninDiffHeader
import com.roninai.app.ui.theme.RoninDiffRemoved
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninSurface
import com.roninai.app.ui.theme.RoninTextSecondary

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNum: Int? = null,
    val newLineNum: Int? = null
)

enum class DiffLineType {
    CONTEXT,
    ADDED,
    REMOVED,
    HEADER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewer(
    oldContent: String,
    newContent: String,
    fileName: String = "diff",
    onBack: () -> Unit
) {
    val diffLines = remember(oldContent, newContent) {
        computeDiff(oldContent, newContent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Diff Viewer",
                            style = MaterialTheme.typography.titleMedium,
                            color = RoninHighlight
                        )
                        Text(
                            text = fileName,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoninSurface
                )
            )
        },
        containerColor = RoninSurface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            // Stats header
            val added = diffLines.count { it.type == DiffLineType.ADDED }
            val removed = diffLines.count { it.type == DiffLineType.REMOVED }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "+$added",
                    color = RoninDiffAdded.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "-$removed",
                    color = RoninDiffRemoved.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Diff lines
            diffLines.forEach { line ->
                val bgColor = when (line.type) {
                    DiffLineType.ADDED -> RoninDiffAdded
                    DiffLineType.REMOVED -> RoninDiffRemoved
                    DiffLineType.HEADER -> RoninDiffHeader
                    DiffLineType.CONTEXT -> RoninSurface
                }

                val prefix = when (line.type) {
                    DiffLineType.ADDED -> "+"
                    DiffLineType.REMOVED -> "-"
                    DiffLineType.HEADER -> "@"
                    DiffLineType.CONTEXT -> " "
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    // Line numbers
                    val lineNumStr = buildString {
                        line.oldLineNum?.let { append("%4d".format(it)) } ?: append("    ")
                        append(" ")
                        line.newLineNum?.let { append("%4d".format(it)) } ?: append("    ")
                    }

                    Text(
                        text = lineNumStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontFamily = FontFamily.Monospace,
                        color = RoninTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Text(
                        text = "$prefix ${line.content}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun computeDiff(oldContent: String, newContent: String): List<DiffLine> {
    val oldLines = oldContent.lines()
    val newLines = newContent.lines()
    val result = mutableListOf<DiffLine>()

    // LCS-based diff
    val lcs = computeLCS(oldLines, newLines)
    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var oldLineNum = 1
    var newLineNum = 1

    result.add(DiffLine(DiffLineType.HEADER, "@@ Diff @@" ))

    while (oldIdx < oldLines.size || newIdx < newLines.size) {
        if (lcsIdx < lcs.size) {
            // Output removed lines before next LCS match
            while (oldIdx < oldLines.size && oldLines[oldIdx] != lcs[lcsIdx]) {
                result.add(
                    DiffLine(
                        type = DiffLineType.REMOVED,
                        content = oldLines[oldIdx],
                        oldLineNum = oldLineNum,
                        newLineNum = null
                    )
                )
                oldIdx++
                oldLineNum++
            }

            // Output added lines before next LCS match
            while (newIdx < newLines.size && newLines[newIdx] != lcs[lcsIdx]) {
                result.add(
                    DiffLine(
                        type = DiffLineType.ADDED,
                        content = newLines[newIdx],
                        oldLineNum = null,
                        newLineNum = newLineNum
                    )
                )
                newIdx++
                newLineNum++
            }

            // Output context line (LCS match)
            if (lcsIdx < lcs.size) {
                result.add(
                    DiffLine(
                        type = DiffLineType.CONTEXT,
                        content = lcs[lcsIdx],
                        oldLineNum = oldLineNum,
                        newLineNum = newLineNum
                    )
                )
                oldIdx++
                newIdx++
                oldLineNum++
                newLineNum++
                lcsIdx++
            }
        } else {
            // Remaining lines
            while (oldIdx < oldLines.size) {
                result.add(
                    DiffLine(
                        type = DiffLineType.REMOVED,
                        content = oldLines[oldIdx],
                        oldLineNum = oldLineNum,
                        newLineNum = null
                    )
                )
                oldIdx++
                oldLineNum++
            }
            while (newIdx < newLines.size) {
                result.add(
                    DiffLine(
                        type = DiffLineType.ADDED,
                        content = newLines[newIdx],
                        oldLineNum = null,
                        newLineNum = newLineNum
                    )
                )
                newIdx++
                newLineNum++
            }
        }
    }

    return result
}

private fun computeLCS(a: List<String>, b: List<String>): List<String> {
    val m = a.size
    val n = b.size
    val dp = Array(m + 1) { IntArray(n + 1) }

    for (i in 1..m) {
        for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }

    val result = mutableListOf<String>()
    var i = m
    var j = n
    while (i > 0 && j > 0) {
        when {
            a[i - 1] == b[j - 1] -> {
                result.add(a[i - 1])
                i--
                j--
            }
            dp[i - 1][j] > dp[i][j - 1] -> i--
            else -> j--
        }
    }

    return result.reversed()
}
