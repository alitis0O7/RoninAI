package com.roninai.app.ui.theme

import androidx.compose.ui.graphics.Color

val RoninPrimary = Color(0xFF1A1A2E)
val RoninSecondary = Color(0xFF16213E)
val RoninAccent = Color(0xFF0F3460)
val RoninHighlight = Color(0xFF00D4FF)
val RoninSurface = Color(0xFF0A0A1A)
val RoninOnSurface = Color(0xFFE0E0E0)
val RoninError = Color(0xFFFF4444)
val RoninSuccess = Color(0xFF44FF88)
val RoninWarning = Color(0xFFFFAA00)
val RoninVoiceActive = Color(0xFF00FFAA)
val RoninVoiceIdle = Color(0xFF555555)

val RoninSurfaceElevated = Color(0xFF12122A)
val RoninCardBg = Color(0xFF1A1A35)
val RoninBorder = Color(0xFF2A2A4A)
val RoninTextSecondary = Color(0xFF888899)
val RoninUserBubble = Color(0xFF0F3460)
val RoninAssistantBubble = Color(0xFF1A1A2E)
val RoninToolCall = Color(0xFF2A1A3E)
val RoninDiffAdded = Color(0xFF1A3A1A)
val RoninDiffRemoved = Color(0xFF3A1A1A)
val RoninDiffHeader = Color(0xFF1A2A3E)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = RoninHighlight,
    secondary = RoninAccent,
    tertiary = RoninVoiceActive,
    background = RoninSurface,
    surface = RoninCardBg,
    surfaceVariant = RoninSurfaceElevated,
    onPrimary = RoninSurface,
    onSecondary = RoninOnSurface,
    onTertiary = RoninSurface,
    onBackground = RoninOnSurface,
    onSurface = RoninOnSurface,
    onSurfaceVariant = RoninTextSecondary,
    error = RoninError,
    onError = RoninOnSurface,
    outline = RoninBorder
)
