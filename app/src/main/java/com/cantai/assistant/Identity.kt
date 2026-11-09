package com.cantai.assistant

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Cantai {
    val Black = Color(0xFF050607)
    val White = Color(0xFFFFFFFF)
    val Cyan = Color(0xFF00E5FF)
    val Turquoise = Color(0xFF00FFD1)
    val Yellow = Color(0xFFFFE600)
}

/** Teal gradient mixed from the palette, with a turquoise glow behind the core. */
fun Modifier.cantaiBackground(): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            listOf(
                lerp(Cantai.Black, Cantai.Cyan, 0.50f),
                lerp(Cantai.Black, Cantai.Cyan, 0.22f),
                lerp(Cantai.Black, Cantai.Turquoise, 0.10f)
            )
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(Cantai.Turquoise.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.20f),
            radius = size.width * 0.9f
        )
    )
}

/** The Gemini (♊) symbol drawn as paths inside a square box. */
fun DrawScope.drawGeminiMark(
    topLeft: Offset,
    box: Float,
    brush: Brush,
    strokeWidth: Float,
    alpha: Float = 1f
) {
    fun x(u: Float) = topLeft.x + u * box
    fun y(v: Float) = topLeft.y + v * box
    val path = Path().apply {
        moveTo(x(0.20f), y(0.20f))
        cubicTo(x(0.40f), y(0.3067f), x(0.60f), y(0.3067f), x(0.80f), y(0.20f))
        moveTo(x(0.20f), y(0.80f))
        cubicTo(x(0.40f), y(0.6933f), x(0.60f), y(0.6933f), x(0.80f), y(0.80f))
        moveTo(x(0.38f), y(0.27f))
        lineTo(x(0.38f), y(0.73f))
        moveTo(x(0.62f), y(0.27f))
        lineTo(x(0.62f), y(0.73f))
    }
    drawPath(
        path = path,
        brush = brush,
        alpha = alpha,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
fun GeminiMark(modifier: Modifier = Modifier, markSize: Dp = 32.dp) {
    Canvas(modifier = modifier.size(markSize)) {
        val box = size.minDimension
        val brush = Brush.linearGradient(
            listOf(Cantai.Cyan, Cantai.Turquoise),
            start = Offset.Zero,
            end = Offset(box, box)
        )
        drawGeminiMark(Offset.Zero, box, brush, box * 0.20f, alpha = 0.18f)
        drawGeminiMark(Offset.Zero, box, brush, box * 0.085f)
    }
}

enum class CoreState { Idle, Listening, Thinking, Speaking, Error }

@Composable
fun AiCore(
    state: CoreState,
    level: Float,
    modifier: Modifier = Modifier,
    coreSize: Dp = 200.dp
) {
    val infinite = rememberInfiniteTransition(label = "core")
    val pulse by infinite.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val slow by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "slow"
    )
    val fast by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "fast"
    )
    val ripple by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ripple"
    )
    val smoothLevel by animateFloatAsState(level, tween(120), label = "level")

    Canvas(modifier = modifier.size(coreSize)) {
        val c = center
        val r = size.minDimension / 2f
        val boost = if (state == CoreState.Listening) smoothLevel * 0.30f else 0f
        val accent = if (state == CoreState.Error) Cantai.Yellow else Cantai.Turquoise
        val ringColor = when (state) {
            CoreState.Error, CoreState.Listening -> Cantai.Yellow
            else -> Cantai.Cyan
        }

        val glowR = r * (pulse + boost)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    accent.copy(alpha = 0.65f),
                    Cantai.Cyan.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = c,
                radius = glowR
            ),
            radius = glowR,
            center = c
        )

        if (state == CoreState.Speaking) {
            for (i in 0..1) {
                val t = (ripple + i * 0.5f) % 1f
                drawCircle(
                    color = Cantai.Turquoise.copy(alpha = (1f - t) * 0.7f),
                    radius = r * (0.45f + 0.55f * t),
                    center = c,
                    style = Stroke(2.dp.toPx())
                )
            }
        }

        drawCircle(
            color = ringColor,
            radius = r * 0.62f,
            center = c,
            style = Stroke(3.dp.toPx())
        )

        val orbit = r * 0.80f
        val arcTopLeft = Offset(c.x - orbit, c.y - orbit)
        val arcSize = Size(orbit * 2f, orbit * 2f)
        if (state == CoreState.Thinking) {
            for (i in 0..2) {
                rotate(degrees = fast + i * 120f, pivot = c) {
                    drawArc(
                        color = if (i == 1) Cantai.Cyan else Cantai.Turquoise,
                        startAngle = 0f,
                        sweepAngle = 50f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        } else {
            rotate(degrees = slow, pivot = c) {
                drawArc(
                    color = Cantai.Yellow,
                    startAngle = 0f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        val discR = r * 0.34f * (1f + boost * 0.5f)
        drawCircle(color = accent, radius = discR, center = c)
        val box = discR * 2.2f
        drawGeminiMark(
            topLeft = Offset(c.x - box / 2f, c.y - box / 2f),
            box = box,
            brush = SolidColor(Cantai.Black),
            strokeWidth = box * 0.085f
        )
    }
}
