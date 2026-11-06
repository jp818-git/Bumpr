package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Animated equalizer-style bars. They move while playing and settle low when paused. */
@Composable
fun AnimatedWave(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.12f,
        animationSpec = tween(500),
        label = "wave_amplitude"
    )

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    primary.copy(alpha = 0.30f),
                    secondary.copy(alpha = 0.18f),
                    Color(0xFF070F1E).copy(alpha = 0.85f)
                )
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bars = 28
            val slot = size.width / bars
            val barWidth = slot * 0.55f
            val maxHeight = size.height * 0.6f
            for (i in 0 until bars) {
                val motion = abs(sin(phase + i * 0.45f) * cos(2 * phase + i * 0.2f))
                val h = (maxHeight * (0.2f + 0.8f * motion) * amplitude).coerceAtLeast(barWidth)
                drawRoundRect(
                    color = (if (i % 2 == 0) primary else secondary).copy(alpha = 0.75f),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }
        }
    }
}
