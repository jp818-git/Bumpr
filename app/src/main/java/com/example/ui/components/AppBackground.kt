package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import com.example.model.AppTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.model.BackgroundStyle

@Composable
fun AppBackground(
    backgroundStyle: BackgroundStyle,
    appTheme: AppTheme = AppTheme.NEON_CYAN,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (appTheme == AppTheme.GLASS) {
            GlassBackdrop()
        } else when (backgroundStyle) {
            BackgroundStyle.MUSICAL_STAVE -> {
                Image(
                    painter = painterResource(id = R.drawable.bg_music_stave),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.35f),
                    contentScale = ContentScale.Crop
                )
                // Darkening gradient overlay for optimal text contrast & readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            }
            BackgroundStyle.SOUNDWAVE -> {
                Image(
                    painter = painterResource(id = R.drawable.bg_soundwave),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.40f),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.60f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
            }
            BackgroundStyle.AURORA_GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                radius = 1200f
                            )
                        )
                )
            }
            BackgroundStyle.MINIMAL -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        // Foreground Screen Content
        content()
    }
}

/** Soft colour blobs behind translucent surfaces; this is what gives the glass its frosted look. */
@Composable
private fun GlassBackdrop() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (dark) listOf(Color(0xFF061A22), Color(0xFF0B2A3A), Color(0xFF1A1440))
    else listOf(Color(0xFFE6F6F4), Color(0xFFDDEBFF), Color(0xFFF1E6FF))
    val teal = if (dark) Color(0xFF2BB5A5) else Color(0xFF5EEAD4)
    val violet = if (dark) Color(0xFF6D4BC4) else Color(0xFFA78BFA)
    val sky = if (dark) Color(0xFF2B8FC4) else Color(0xFF7DD3FC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(base))
            .drawBehind {
                val w = size.width
                val h = size.height
                listOf(
                    Triple(teal, Offset(w * 0.15f, h * 0.18f), w * 0.70f),
                    Triple(violet, Offset(w * 0.90f, h * 0.50f), w * 0.80f),
                    Triple(sky, Offset(w * 0.25f, h * 0.90f), w * 0.75f)
                ).forEach { (color, center, radius) ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(color.copy(alpha = if (dark) 0.32f else 0.50f), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
            }
    )
}
