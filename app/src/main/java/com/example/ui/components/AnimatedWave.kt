package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.withFrameNanos
import com.example.model.NoLyricsDisplay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Picks the animation for the Now Playing card when a song has no lyrics. */
@Composable
fun AnimatedVisual(
    style: NoLyricsDisplay,
    isPlaying: Boolean,
    art: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val transition = rememberInfiniteTransition(label = "visual")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.12f,
        animationSpec = tween(500),
        label = "amplitude"
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
        ),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            NoLyricsDisplay.FLOWING_WAVES -> Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                for (layer in 0 until 4) {
                    val direction = if (layer % 2 == 0) 1f else -1f
                    val path = Path()
                    var x = 0f
                    while (x <= w) {
                        val y = h / 2f + h * 0.14f * (1f + layer * 0.25f) * amplitude *
                            sin(x / w * 2f * PI.toFloat() * (1.5f + layer) + direction * phase + layer)
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += 6f
                    }
                    drawPath(
                        path = path,
                        color = (if (layer % 2 == 0) primary else secondary).copy(alpha = 0.75f - layer * 0.12f),
                        style = Stroke(width = 5f - layer)
                    )
                }
            }

            NoLyricsDisplay.PULSE_RINGS -> Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minOf(size.width, size.height) * 0.48f
                for (i in 0 until 4) {
                    val t = ((phase / (2f * PI.toFloat())) + i / 4f) % 1f
                    drawCircle(
                        color = primary.copy(alpha = (1f - t) * 0.6f * (0.25f + 0.75f * amplitude)),
                        radius = maxRadius * t,
                        center = center,
                        style = Stroke(width = 6f)
                    )
                }
                val pulse = 1f + 0.08f * sin(phase * 2f) * amplitude
                drawCircle(primary.copy(alpha = 0.55f), radius = maxRadius * 0.16f * pulse, center = center)
            }

            NoLyricsDisplay.VINYL -> {
                var angle by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(isPlaying) {
                    var last = 0L
                    while (isPlaying) {
                        withFrameNanos { now ->
                            if (last != 0L) angle = (angle + (now - last) / 1_000_000_000f * 40f) % 360f
                            last = now
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.86f)
                        .aspectRatio(1f)
                        .rotate(angle)
                        .clip(CircleShape)
                        .background(Color(0xFF0B0B0F)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f
                        for (k in 0 until 9) {
                            drawCircle(
                                Color.White.copy(alpha = 0.06f),
                                radius = r * (0.45f + k * 0.06f),
                                center = c,
                                style = Stroke(width = 1.5f)
                            )
                        }
                    }
                    if (art != null) {
                        Image(
                            bitmap = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(0.38f).clip(CircleShape)
                        )
                    } else {
                        Box(Modifier.fillMaxSize(0.38f).clip(CircleShape).background(primary))
                    }
                    Box(Modifier.fillMaxSize(0.05f).clip(CircleShape).background(Color(0xFF0B0B0F)))
                }
            }

            else -> Canvas(Modifier.fillMaxSize()) {
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
}
