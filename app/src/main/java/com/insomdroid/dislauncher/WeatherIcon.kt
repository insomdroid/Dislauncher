package com.insomdroid.dislauncher

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun WeatherIcon(
    condition: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "condition-icon")
    val drift by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "cloud-drift"
    )
    val sunAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000)),
        label = "sun-rotation"
    )
    Canvas(modifier = modifier.size(82.dp).semantics { contentDescription = condition }) {
        val scale = size.minDimension / 100f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun circle(x: Float, y: Float, radius: Float, alpha: Float = 1f) {
            drawCircle(color.copy(alpha = alpha), radius * scale, point(x, y))
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 3.5f, alpha: Float = 1f) {
            drawLine(color.copy(alpha = alpha), point(x1, y1), point(x2, y2), width * scale, cap = StrokeCap.Round)
        }
        fun cloud(offset: Float = 0f, alpha: Float = 1f) {
            val shift = offset + drift
            circle(34f + shift, 48f, 15f, alpha)
            circle(52f + shift, 38f, 21f, alpha)
            circle(69f + shift, 49f, 14f, alpha)
            drawRoundRect(
                color.copy(alpha = alpha),
                topLeft = point(22f + shift, 46f),
                size = Size(57f * scale, 22f * scale),
                cornerRadius = CornerRadius(11f * scale)
            )
        }
        val lower = condition.lowercase()
        when {
            lower.contains("clear") && !lower.contains("cloud") -> {
                val base = sunAngle * (PI.toFloat() / 180f)
                for (i in 0 until 8) {
                    val angle = base + i * (PI.toFloat() / 4f)
                    line(50f + cos(angle) * 25f, 42f + sin(angle) * 25f,
                        50f + cos(angle) * 34f, 42f + sin(angle) * 34f, 3f)
                }
                circle(50f, 42f, 19f)
            }
            lower.contains("thunder") -> {
                cloud()
                val bolt = Path().apply {
                    moveTo(54f * scale, 61f * scale)
                    lineTo(43f * scale, 78f * scale)
                    lineTo(52f * scale, 78f * scale)
                    lineTo(47f * scale, 93f * scale)
                    lineTo(64f * scale, 72f * scale)
                    lineTo(55f * scale, 72f * scale)
                    close()
                }
                drawPath(bolt, color)
            }
            lower.contains("rain") || lower.contains("shower") || lower.contains("drizzle") -> {
                cloud()
                line(34f, 76f + drift, 29f, 86f + drift, 3f)
                line(51f, 76f - drift, 46f, 86f - drift, 3f)
                line(68f, 76f + drift, 63f, 86f + drift, 3f)
            }
            lower.contains("snow") -> {
                cloud()
                circle(33f, 81f + drift, 2.8f)
                circle(50f, 85f - drift, 2.8f)
                circle(67f, 80f + drift, 2.8f)
            }
            lower.contains("fog") -> {
                line(24f, 42f, 76f, 42f, 4f)
                line(17f, 53f + drift, 67f, 53f + drift, 4f)
                line(31f, 64f - drift, 82f, 64f - drift, 4f)
            }
            lower.contains("cloud") -> cloud()
            else -> {
                cloud()
                line(32f, 78f + drift, 27f, 87f + drift, 3f)
                line(52f, 78f - drift, 47f, 87f - drift, 3f)
                line(71f, 78f + drift, 66f, 87f + drift, 3f)
            }
        }
    }
}
