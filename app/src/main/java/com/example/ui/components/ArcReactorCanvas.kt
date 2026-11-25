package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.hardware.VoiceCaptureState
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.ObsidianVoid
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorOrb(
    captureState: VoiceCaptureState,
    isProcessing: Boolean,
    waveformBands: List<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 138.dp
) {
    val baseAccent = MaterialTheme.colorScheme.primary
    val activeAccent = when {
        isProcessing -> AmberAlert
        captureState == VoiceCaptureState.COMMAND_LISTENING -> EmeraldNominal
        captureState == VoiceCaptureState.SPEAKING -> baseAccent
        captureState == VoiceCaptureState.WAKE_LISTENING -> baseAccent.copy(alpha = 0.85f)
        else -> baseAccent
    }

    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProcessing) 3200 else 10500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProcessing) 2400 else 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val avgEnergy = remember(waveformBands) {
        if (waveformBands.isEmpty()) 0.1f else waveformBands.average().toFloat()
    }
    val cdText = stringResource(R.string.cd_arc_reactor)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(orbSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .semantics { contentDescription = cdText }
                .testTag("arc_reactor_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f

                // Outer holographic aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.28f + avgEnergy * 0.25f),
                            activeAccent.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = maxR
                    ),
                    radius = maxR,
                    center = Offset(cx, cy)
                )

                // Reactive radial spectrum bars around perimeter
                val count = waveformBands.size.coerceAtLeast(1)
                for (i in 0 until count) {
                    val angleRad = Math.toRadians((i * (360.0 / count)) - 90.0)
                    val amp = waveformBands[i].coerceIn(0.08f, 1f)
                    val innerR = maxR * 0.72f
                    val outerR = innerR + maxR * 0.22f * amp
                    drawLine(
                        color = activeAccent.copy(alpha = 0.35f + amp * 0.65f),
                        start = Offset(
                            cx + (innerR * cos(angleRad)).toFloat(),
                            cy + (innerR * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            cx + (outerR * cos(angleRad)).toFloat(),
                            cy + (outerR * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = 3.2f,
                        cap = StrokeCap.Round
                    )
                }

                // Rotating outer segmented telemetry ring
                rotate(outerAngle, pivot = Offset(cx, cy)) {
                    val r1 = maxR * 0.64f
                    for (seg in 0 until 6) {
                        drawArc(
                            color = activeAccent.copy(alpha = if (seg % 2 == 0) 0.9f else 0.35f),
                            startAngle = seg * 60f + 6f,
                            sweepAngle = 44f,
                            useCenter = false,
                            topLeft = Offset(cx - r1, cy - r1),
                            size = Size(r1 * 2f, r1 * 2f),
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner armature ring
                rotate(innerAngle, pivot = Offset(cx, cy)) {
                    val r2 = maxR * 0.48f
                    for (seg in 0 until 4) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.75f),
                            startAngle = seg * 90f + 12f,
                            sweepAngle = 62f,
                            useCenter = false,
                            topLeft = Offset(cx - r2, cy - r2),
                            size = Size(r2 * 2f, r2 * 2f),
                            style = Stroke(width = 3f, cap = StrokeCap.Butt)
                        )
                    }
                }

                // Core plasma disc
                val coreRadius = maxR * 0.30f * (pulse + avgEnergy * 0.22f)
                drawCircle(
                    color = ObsidianVoid,
                    radius = coreRadius * 1.15f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            activeAccent,
                            activeAccent.copy(alpha = 0.1f)
                        ),
                        center = Offset(cx, cy),
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = Offset(cx, cy)
                )
            }

            val stateTag = when {
                isProcessing -> "SYNC"
                captureState == VoiceCaptureState.COMMAND_LISTENING -> "REC"
                captureState == VoiceCaptureState.SPEAKING -> "VOX"
                captureState == VoiceCaptureState.WAKE_LISTENING -> "ARMED"
                else -> "TAP"
            }
            Text(
                text = stateTag,
                style = MaterialTheme.typography.labelSmall,
                color = ObsidianVoid
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        WaveformSpectrumBar(
            bands = waveformBands,
            accentColor = activeAccent,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun WaveformSpectrumBar(
    bands: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bands.isEmpty()) return@Canvas
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (bands.size - 1)
        val barWidth = ((size.width - totalSpacing) / bands.size).coerceAtLeast(2f)
        val midY = size.height / 2f

        bands.forEachIndexed { index, amp ->
            val clamped = amp.coerceIn(0.08f, 1f)
            val barH = (size.height * clamped).coerceAtLeast(3f)
            val x = index * (barWidth + barSpacing)
            drawRoundRect(
                color = accentColor.copy(alpha = 0.35f + clamped * 0.65f),
                topLeft = Offset(x, midY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
