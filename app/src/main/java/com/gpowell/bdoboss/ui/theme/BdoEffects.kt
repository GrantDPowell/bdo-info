package com.gpowell.bdoboss.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Global gate for the §9.4 animated effects (On by default; Calm disables loops). */
val LocalEffectsEnabled = compositionLocalOf { true }

// ── A. Ambient gold dust ──────────────────────────────────────────────────────
private class Mote(
    var x: Float, var y: Float, val r: Float,
    val vy: Float, val vx: Float, val a: Float,
    var tw: Float, val tws: Float,
)

/**
 * A drifting field of gold motes (rises slowly, gentle wander + twinkle). One Canvas
 * driven by a frame loop — pure draw-phase, no tree recomposition. Place behind the dial.
 */
@Composable
fun GoldDust(modifier: Modifier = Modifier, count: Int = 34) {
    val motes = remember {
        List(count) {
            Mote(
                x = Random.nextFloat(), y = Random.nextFloat(),
                r = Random.nextFloat() * 2.4f + 0.8f,
                vy = -(Random.nextFloat() * 0.0007f + 0.0002f),
                vx = (Random.nextFloat() - 0.5f) * 0.0005f,
                a = Random.nextFloat() * 0.6f + 0.2f,
                tw = Random.nextFloat() * (2f * PI.toFloat()),
                tws = Random.nextFloat() * 0.05f + 0.015f,
            )
        }
    }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                for (m in motes) {
                    m.y += m.vy; m.x += m.vx; m.tw += m.tws
                    if (m.y < -0.02f) { m.y = 1.02f; m.x = Random.nextFloat() }
                    if (m.x < -0.02f) m.x = 1.02f else if (m.x > 1.02f) m.x = -0.02f
                }
                tick = now
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") tick // read to redraw each frame
        val gold = BdoColors.goldHi
        for (m in motes) {
            val a = (m.a * (0.55f + 0.45f * sin(m.tw))).coerceIn(0f, 1f)
            val c = Offset(m.x * size.width, m.y * size.height)
            drawCircle(gold.copy(alpha = a * 0.22f), radius = m.r * 5f, center = c)  // soft bloom
            drawCircle(gold.copy(alpha = a * 0.55f), radius = m.r * 2.4f, center = c) // glow
            drawCircle(gold.copy(alpha = a), radius = m.r, center = c)                // core
        }
    }
}

// ── E. Specular shimmer brush for the big countdown ────────────────────────────
/**
 * A gold→near-white→gold band that rakes across glyphs. Uses RepeatMode.Reverse (ping-pong)
 * so the highlight sweeps smoothly back and forth — a plain looping phase would snap back
 * each cycle and look choppy.
 */
@Composable
fun shimmerGoldBrush(enabled: Boolean): Brush {
    if (!enabled) return SolidColor(BdoColors.goldHi)
    val t = rememberInfiniteTransition(label = "shimmer")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "phase",
    )
    val x = phase * 620f
    return Brush.linearGradient(
        colors = listOf(BdoColors.gold, Color(0xFFFFF6DC), BdoColors.gold),
        start = Offset(x - 210f, 0f),
        end = Offset(x + 210f, 0f),
        tileMode = TileMode.Mirror,
    )
}

// ── G. Spark burst (target HIT) ─────────────────────────────────────────────────
/** Repeating radial ember burst over an icon. Quiet "ka-ching" for a HIT watchlist row. */
@Composable
fun SparkBurst(modifier: Modifier = Modifier, color: Color = BdoColors.goldHi) {
    val t = rememberInfiniteTransition(label = "spark")
    val p by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p",
    )
    Canvas(modifier) {
        val window = 900f / 2600f
        if (p > window) return@Canvas
        val burst = (p / window).coerceIn(0f, 1f)
        val n = 9
        for (i in 0 until n) {
            val ang = (i / n.toFloat()) * 2f * PI.toFloat()
            val dist = (16f + (i % 3) * 5f) * burst
            val c = Offset(
                center.x + cos(ang) * dist * density,
                center.y + sin(ang) * dist * density,
            )
            val a = 0.85f * (1f - burst)
            drawCircle(color.copy(alpha = a * 0.5f), radius = 4f * density * (1f - burst), center = c)
            drawCircle(color.copy(alpha = a), radius = 2f * density * (1f - burst * 0.5f), center = c)
        }
    }
}

// ── Sparklines (deterministic series for list rows / spotlight) ─────────────────
/** Deterministic pseudo-random walk used for row/spotlight sparklines (mirrors prototype). */
fun sparkSeries(seed: Int, n: Int, up: Boolean): List<Float> {
    var s = (seed * 999).mod(233280)
    fun rnd(): Float { s = (s * 9301 + 49297).mod(233280); return s / 233280f }
    val out = ArrayList<Float>(n)
    var v = 50f
    repeat(n) {
        v += (rnd() - 0.5f) * 14f + (if (up) 0.8f else -0.8f)
        v = v.coerceIn(8f, 92f)
        out.add(v)
    }
    return out
}

@Composable
fun MiniSparkline(values: List<Float>, up: Boolean, modifier: Modifier = Modifier) {
    val c = if (up) BdoColors.up else BdoColors.down
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min(); val max = values.max()
        val span = (max - min).coerceAtLeast(0.001f)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v - min) / span * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, c.copy(alpha = 0.95f), style = Stroke(width = 1.6f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Full-width area sparkline with gradient fill + glow — the spotlight hero. */
@Composable
fun AreaSparkline(values: List<Float>, up: Boolean, modifier: Modifier = Modifier) {
    val c = if (up) BdoColors.up else BdoColors.down
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min(); val max = values.max()
        val span = (max - min).coerceAtLeast(0.001f)
        val stepX = size.width / (values.size - 1)
        fun yOf(v: Float) = size.height - (v - min) / span * size.height
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            if (i == 0) line.moveTo(x, yOf(v)) else line.lineTo(x, yOf(v))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(c.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(line, c, style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
