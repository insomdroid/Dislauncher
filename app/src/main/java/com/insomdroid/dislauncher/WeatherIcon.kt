package com.insomdroid.dislauncher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun WeatherIcon(
    condition: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var hasAppeared by remember(condition) { mutableStateOf(false) }
    LaunchedEffect(condition) { hasAppeared = true }
    val reveal by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "weather-icon-reveal"
    )
    Canvas(
        modifier = modifier.graphicsLayer {
            translationY = 16f * (1f - reveal)
            scaleX = 0.88f + 0.12f * reveal
            scaleY = 0.88f + 0.12f * reveal
            alpha = reveal
        }
            .semantics { contentDescription = condition }
    ) {
        val scale = size.minDimension / 112f
        val inset = (size.minDimension - 100f * scale) / 2f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(
            x1: Float, y1: Float, x2: Float, y2: Float,
            width: Float = 2.8f, alpha: Float = 1f
        ) {
            drawLine(
                color.copy(alpha = alpha),
                point(x1, y1),
                point(x2, y2),
                width * scale,
                cap = StrokeCap.Round
            )
        }
        fun outlineCircle(x: Float, y: Float, radius: Float, width: Float = 3f, alpha: Float = 1f) {
            drawCircle(
                color.copy(alpha = alpha),
                radius = radius * scale,
                center = point(x, y),
                style = Stroke(width = width * scale)
            )
        }
        fun sun(cx: Float = 39f, cy: Float = 37f, radius: Float = 17f) {
            outlineCircle(cx, cy, radius, 3f)
            for (i in 0 until 8) {
                val angle = i * (PI.toFloat() / 4f)
                line(
                    cx + cos(angle) * (radius + 5f),
                    cy + sin(angle) * (radius + 5f),
                    cx + cos(angle) * (radius + 12f),
                    cy + sin(angle) * (radius + 12f),
                    3f
                )
            }
        }
        fun cloud() {
            val path = Path().apply {
                moveTo(22f * scale, 70f * scale)
                cubicTo(14f * scale, 70f * scale, 9f * scale, 64f * scale, 9f * scale, 56f * scale)
                cubicTo(9f * scale, 48f * scale, 15f * scale, 42f * scale, 23f * scale, 41f * scale)
                cubicTo(24f * scale, 28f * scale, 34f * scale, 19f * scale, 47f * scale, 19f * scale)
                cubicTo(60f * scale, 19f * scale, 69f * scale, 28f * scale, 71f * scale, 40f * scale)
                cubicTo(82f * scale, 39f * scale, 91f * scale, 47f * scale, 91f * scale, 57f * scale)
                cubicTo(91f * scale, 65f * scale, 84f * scale, 71f * scale, 75f * scale, 71f * scale)
                close()
            }
            drawPath(
                path,
                color,
                style = Stroke(width = 3.5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        fun rain() {
            line(30f, 79f, 25f, 88f, 3f)
            line(50f, 80f, 45f, 89f, 3f)
            line(70f, 79f, 65f, 88f, 3f)
        }

        withTransform({ translate(left = inset, top = inset) }) {
        val lower = condition.lowercase()
        when {
            lower.contains("clear") && !lower.contains("cloud") -> sun(50f, 48f, 18f)
            lower.contains("thunder") -> {
                cloud()
                val bolt = Path().apply {
                    moveTo(53f * scale, 70f * scale)
                    lineTo(43f * scale, 86f * scale)
                    lineTo(51f * scale, 86f * scale)
                    lineTo(46f * scale, 98f * scale)
                    lineTo(64f * scale, 77f * scale)
                    lineTo(55f * scale, 77f * scale)
                    close()
                }
                drawPath(bolt, color)
            }
            lower.contains("rain") || lower.contains("shower") || lower.contains("drizzle") -> {
                cloud()
                rain()
            }
            lower.contains("snow") -> {
                cloud()
                drawCircle(color, 1.8f * scale, point(31f, 84f))
                drawCircle(color, 1.8f * scale, point(50f, 89f))
                drawCircle(color, 1.8f * scale, point(69f, 84f))
            }
            lower.contains("fog") -> {
                line(21f, 42f, 79f, 42f, 3f)
                line(14f, 54f, 72f, 54f, 3f)
                line(26f, 66f, 85f, 66f, 3f)
            }
            lower.contains("cloud") -> {
                sun()
                cloud()
            }
            else -> cloud()
        }
        }
    }
}
