package com.roninai.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roninai.app.ui.theme.RoninHighlight
import com.roninai.app.ui.theme.RoninVoiceActive
import com.roninai.app.ui.theme.RoninVoiceIdle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceWaveform(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    barCount: Int = 32,
    primaryColor: Color = if (isActive) RoninVoiceActive else RoninVoiceIdle,
    accentColor: Color = RoninHighlight
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2
        val radius = this.size.width / 2 - 8.dp.toPx()

        // Outer reactive ring
        val outerRadius = radius * pulse
        drawCircle(
            color = primaryColor.copy(alpha = 0.15f),
            radius = outerRadius,
            center = Offset(centerX, centerY)
        )

        // Inner ring
        drawCircle(
            color = primaryColor.copy(alpha = 0.3f),
            radius = radius * 0.6f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )

        // Center dot
        drawCircle(
            color = if (isActive) RoninVoiceActive else primaryColor,
            radius = 6.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        // Waveform bars arranged in a circle
        val barWidth = 3.dp.toPx()
        val maxBarHeight = radius * 0.35f

        for (i in 0 until barCount) {
            val angle = (2.0 * PI * i / barCount).toFloat()
            val x = centerX + radius * 0.75f * cos(angle.toDouble()).toFloat()
            val y = centerY + radius * 0.75f * sin(angle.toDouble()).toFloat()

            // Wave factor based on angle and time
            val waveFactor = (sin(angle.toDouble() * 3 + phase.toDouble()).toFloat() * 0.5f + 0.5f)
            val amplitudeFactor = amplitude.coerceIn(0f, 1f)
            val barHeight = maxBarHeight * (0.2f + 0.8f * waveFactor * amplitudeFactor)

            val barColor = if (i % 4 == 0) accentColor else primaryColor
            val alpha = 0.4f + 0.6f * waveFactor

            val startX = x - (barHeight / 2) * sin(angle.toDouble()).toFloat()
            val startY = y + (barHeight / 2) * cos(angle.toDouble()).toFloat()
            val endX = x + (barHeight / 2) * sin(angle.toDouble()).toFloat()
            val endY = y - (barHeight / 2) * cos(angle.toDouble()).toFloat()

            drawLine(
                color = barColor.copy(alpha = alpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // Glow effect when active
        if (isActive && amplitude > 0.1f) {
            drawCircle(
                color = RoninVoiceActive.copy(alpha = amplitude * 0.2f),
                radius = radius * 0.5f * amplitude,
                center = Offset(centerX, centerY)
            )
        }
    }
}

@Composable
fun MiniWaveform(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 5
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / (barCount * 2f)
            val maxHeight = size.height * 0.8f

            for (i in 0 until barCount) {
                val x = barWidth * (i * 2 + 1)
                val normalizedAmp = (amplitude * (0.5f + 0.5f * sin(i.toDouble())).toFloat()).coerceIn(0.1f, 1f)
                val barHeight = maxHeight * normalizedAmp

                drawLine(
                    color = if (isActive) RoninVoiceActive else RoninVoiceIdle,
                    start = Offset(x, size.height / 2 - barHeight / 2),
                    end = Offset(x, size.height / 2 + barHeight / 2),
                    strokeWidth = barWidth * 0.6f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
