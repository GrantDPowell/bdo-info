package com.gpowell.bdoboss.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.GoldDust
import com.gpowell.bdoboss.ui.theme.LocalEffectsEnabled
import com.gpowell.bdoboss.ui.theme.bossDialTint
import com.gpowell.bdoboss.ui.theme.shimmerGoldBrush
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// dial geometry in a 360 x 372 design space (scaled to width at runtime)
private const val CXv = 180f
private const val CYv = 184f
private const val Rv = 130f
private const val WINDOW_MS = 24L * 3600L * 1000L

private data class DialNode(val spawn: Spawn, val ms: Long, val deg: Float)

private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
    val a = (deg - 90f) * (Math.PI.toFloat() / 180f)
    return Offset(cx + r * cos(a), cy + r * sin(a))
}

/**
 * The ORRERY Spawn Dial — a radial astrolabe of the next 24h of spawns. Each boss sits at its
 * time-angle as a real portrait you tap to open its detail; the next boss shockwaves and the
 * countdown to it shimmers at the center. A thread of fate links spawns chronologically.
 */
@Composable
fun SpawnDial(
    spawns: List<Spawn>,
    onSpawnClick: (Spawn) -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val fx = LocalEffectsEnabled.current
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1000) } }

    val nodes = remember(spawns, now) {
        spawns.map { s ->
            val ms = Duration.between(now, s.at).toMillis()
            val frac = (ms.toFloat() / WINDOW_MS).coerceIn(0f, 0.92f)
            DialNode(s, ms, frac * 330f)
        }.filter { it.ms >= -60_000 }
    }
    val next = nodes.minByOrNull { if (it.ms < -60_000) Long.MAX_VALUE else it.ms } ?: nodes.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 2.dp, bottom = 16.dp),
    ) {
        headerContent?.invoke()
        if (next == null) {
            Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("No upcoming spawns", color = BdoColors.onFaint)
            }
            return@Column
        }

        DialCanvas(nodes = nodes, next = next, now = now, fx = fx, onSpawnClick = onSpawnClick)
        UpNextStrip(nodes = nodes.filter { it != next }.take(6), onOpen = onSpawnClick)
    }
}

@Composable
private fun DialCanvas(
    nodes: List<DialNode>,
    next: DialNode,
    now: Instant,
    fx: Boolean,
    onSpawnClick: (Spawn) -> Unit,
) {
    val density = LocalDensity.current

    // Day/night ring: the 24h window (top = now, clockwise) shaded for local daylight
    // (06:00–18:00) vs night, with a sun at the next local noon and a moon at midnight.
    // Rotates naturally as `now` advances each second.
    val localNow = remember(now) { now.atZone(ZoneId.systemDefault()) }
    val nowHourFrac = localNow.hour + localNow.minute / 60.0
    fun angleFromNow(localHour: Double): Float {
        val h = ((localHour - nowHourFrac) % 24.0 + 24.0) % 24.0
        return (h / 24.0 * 360.0).toFloat()
    }
    val sunDeg = angleFromNow(12.0)
    val moonDeg = angleFromNow(0.0)
    val dayBand = Color(0xFFEAC766)
    val nightBand = Color(0xFF5566A6)
    val infinite = rememberInfiniteTransition(label = "dial")
    val flourish by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(140_000, easing = LinearEasing)), label = "flourish",
    )
    val shock by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "shock",
    )
    val threadPhase by infinite.animateFloat(
        0f, 18f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "thread",
    )
    val halo by infinite.animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "halo",
    )

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val wPx = with(density) { maxWidth.toPx() }
        val scale = wPx / 360f
        val hDp = with(density) { (372f * scale).toDp() }
        fun p(r: Float, deg: Float) = polar(CXv * scale, CYv * scale, r * scale, deg)

        Box(Modifier.fillMaxWidth().height(hDp)) {
            if (fx) GoldDust(Modifier.fillMaxSize())

            Canvas(Modifier.fillMaxSize()) {
                val cx = CXv * scale; val cy = CYv * scale; val R = Rv * scale

                // core glow
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(BdoColors.goldGlow.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(cx, cy - R * 0.05f), radius = R,
                    ),
                    radius = R, center = Offset(cx, cy - R * 0.05f),
                )

                // day / night band (thin ring just inside the rim), 48 half-hour segments
                val bandR = (Rv - 9f) * scale
                val bandStroke = 3f * scale
                for (i in 0 until 48) {
                    val localH = (nowHourFrac + i * 0.5) % 24.0
                    val isDay = localH >= 6.0 && localH < 18.0
                    val a0 = (i / 48f) * 360f - 90f
                    drawArc(
                        color = (if (isDay) dayBand else nightBand).copy(alpha = if (isDay) 0.32f else 0.42f),
                        startAngle = a0,
                        sweepAngle = 360f / 48f + 0.6f,
                        useCenter = false,
                        topLeft = Offset(cx - bandR, cy - bandR),
                        size = androidx.compose.ui.geometry.Size(bandR * 2, bandR * 2),
                        style = Stroke(width = bandStroke, cap = StrokeCap.Butt),
                    )
                }
                // sun glyph (at next local noon)
                val sunC = polar(cx, cy, bandR, sunDeg)
                drawCircle(dayBand.copy(alpha = 0.30f), radius = 9f * scale, center = sunC) // halo
                drawCircle(dayBand, radius = 4.5f * scale, center = sunC)
                for (k in 0 until 8) {
                    val ra = (k / 8f) * 2f * Math.PI.toFloat()
                    val r1 = polar(sunC.x, sunC.y, 6.5f * scale, Math.toDegrees(ra.toDouble()).toFloat())
                    val r2 = polar(sunC.x, sunC.y, 9.5f * scale, Math.toDegrees(ra.toDouble()).toFloat())
                    drawLine(dayBand, r1, r2, strokeWidth = 1.2f * scale, cap = StrokeCap.Round)
                }
                // moon glyph (at next local midnight) — crescent via offset cut-disc
                val moonC = polar(cx, cy, bandR, moonDeg)
                drawCircle(Color(0xFFC9D2EE), radius = 5f * scale, center = moonC)
                drawCircle(BdoColors.bg1, radius = 4.2f * scale, center = Offset(moonC.x + 2.2f * scale, moonC.y - 1.4f * scale))

                // rings
                drawCircle(BdoColors.lineStrong, radius = R, center = Offset(cx, cy), style = Stroke(width = 1f * scale))
                drawCircle(BdoColors.goldLine, radius = R + 10f * scale, center = Offset(cx, cy), style = Stroke(width = 1f * scale))

                // hour ticks
                for (i in 0 until 24) {
                    val deg = i / 24f * 360f
                    val major = i % 6 == 0
                    val outer = p(Rv + if (major) 10f else 6f, deg)
                    val inner = p(Rv, deg)
                    drawLine(
                        if (major) BdoColors.gold.copy(alpha = 0.8f) else BdoColors.onFaint.copy(alpha = 0.4f),
                        inner, outer, strokeWidth = (if (major) 1.6f else 1f) * scale,
                    )
                }

                // rotating flourish diamonds
                rotate(flourish, Offset(cx, cy)) {
                    listOf(0f, 90f, 180f, 270f).forEach { deg ->
                        val pt = polar(cx, cy, R + 10f * scale, deg)
                        rotate(45f, pt) {
                            drawRect(
                                BdoColors.gold.copy(alpha = 0.6f),
                                topLeft = Offset(pt.x - 2.5f * scale, pt.y - 2.5f * scale),
                                size = androidx.compose.ui.geometry.Size(5f * scale, 5f * scale),
                            )
                        }
                    }
                }

                // progress wedge: top(now) -> next
                drawArc(
                    color = BdoColors.goldHi,
                    startAngle = -90f,
                    sweepAngle = next.deg.coerceAtLeast(2f),
                    useCenter = false,
                    topLeft = Offset(cx - R, cy - R),
                    size = androidx.compose.ui.geometry.Size(R * 2, R * 2),
                    style = Stroke(width = 3f * scale, cap = StrokeCap.Round),
                )

                // hand to next
                val hand = p(Rv - 4f, next.deg)
                drawLine(BdoColors.gold.copy(alpha = 0.5f), Offset(cx, cy), hand, strokeWidth = 1.4f * scale)

                // thread of fate
                if (fx && nodes.size > 1) {
                    val sorted = nodes.sortedBy { it.ms }
                    val thread = Path()
                    sorted.forEachIndexed { i, n ->
                        val pt = p(Rv, n.deg)
                        if (i == 0) thread.moveTo(pt.x, pt.y) else thread.lineTo(pt.x, pt.y)
                    }
                    drawPath(
                        thread, BdoColors.gold.copy(alpha = 0.34f),
                        style = Stroke(
                            width = 1f * scale, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * scale, 7f * scale), -threadPhase * scale),
                        ),
                    )
                }

                // shockwave halo behind the NEXT boss
                val np = p(Rv, next.deg)
                val tintNext = bossDialTint(next.spawn.bosses.first())
                if (fx) {
                    listOf(shock, (shock + 0.5f) % 1f).forEach { sp ->
                        drawCircle(
                            tintNext.copy(alpha = 0.6f * (1f - sp)),
                            radius = 26f * scale * (1f + 1.4f * sp), center = np,
                            style = Stroke(width = 1.6f * scale),
                        )
                    }
                }
                drawCircle(tintNext.copy(alpha = 0.18f * halo), radius = 30f * scale, center = np)

                // NOW marker dot
                val nowPt = p(Rv, 0f)
                drawCircle(BdoColors.live2, radius = 3.5f * scale, center = nowPt)
            }

            // ── boss icons (tappable portraits at their time-angle) ──
            nodes.forEach { n ->
                val isNext = n == next
                val tileDp = if (isNext) 50.dp else 42.dp
                val tilePx = with(density) { tileDp.toPx() }
                val pos = p(Rv, n.deg)
                Box(
                    Modifier
                        .offset { IntOffset((pos.x - tilePx / 2f).roundToInt(), (pos.y - tilePx / 2f).roundToInt()) }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSpawnClick(n.spawn) },
                ) {
                    BossTile(n.spawn.bosses.first(), size = tileDp, glow = isNext)
                    if (n.spawn.bosses.size > 1) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(50))
                                .background(BdoColors.surfaceHi)
                                .padding(horizontal = 4.dp),
                        ) {
                            androidx.compose.material3.Text(
                                "+${n.spawn.bosses.size - 1}",
                                style = BdoType.overline.copy(fontSize = 8.sp),
                                color = BdoColors.goldHi,
                            )
                        }
                    }
                }
            }

            // center overlay (next spawn)
            CenterStack(
                next = next,
                fx = fx,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = hDp * 0.30f),
                onOpen = { onSpawnClick(next.spawn) },
            )
            androidx.compose.material3.Text(
                "NOW",
                style = BdoType.overline.copy(fontSize = 8.5.sp),
                color = BdoColors.onFaint,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { ((CYv - Rv) * scale).toDp() } - 16.dp),
            )
        }
    }
}

@Composable
private fun CenterStack(
    next: DialNode,
    fx: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val co = next.spawn.bosses.size > 1
    val imminent = next.ms in 0..(10 * 60 * 1000)
    val fmt = remember { DateTimeFormatter.ofPattern("EEE h:mm a") }
    val local = next.spawn.at.atZone(ZoneId.systemDefault())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen() }.padding(8.dp),
    ) {
        androidx.compose.material3.Text(
            (if (imminent) "Spawning now" else "Next spawn").uppercase(),
            style = BdoType.overline.copy(fontSize = 9.sp),
            color = if (imminent) BdoColors.live2 else BdoColors.gold,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            next.spawn.bosses.joinToString(" + "),
            style = BdoType.display.copy(fontSize = if (co) 17.sp else 22.sp),
            color = BdoColors.onBg,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            text = dialCountdown(next.ms),
            style = BdoType.hero.copy(
                fontSize = if (imminent) 40.sp else 34.sp,
                brush = if (imminent) null else shimmerGoldBrush(fx),
            ),
            color = if (imminent) BdoColors.live2 else BdoColors.goldHi,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            fmt.format(local) + if (co) " · Co-spawn" else "",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = BdoColors.onFaint,
        )
    }
}

@Composable
private fun UpNextStrip(nodes: List<DialNode>, onOpen: (Spawn) -> Unit) {
    if (nodes.isEmpty()) return
    com.gpowell.bdoboss.ui.theme.SectionLabel("Up next", Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        nodes.forEach { n ->
            val b = n.spawn.bosses.first()
            BdoCard(
                modifier = Modifier.width(120.dp),
                onClick = { onOpen(n.spawn) },
                contentPadding = PaddingValues(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BossTile(b, size = 30.dp)
                    if (n.spawn.bosses.size > 1) {
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.material3.Text(
                            "+${n.spawn.bosses.size - 1}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = BdoColors.goldHi,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Text(
                    n.spawn.bosses.first(),
                    style = BdoType.display.copy(fontSize = 14.sp),
                    color = BdoColors.onBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    dialCountdown(n.ms),
                    style = BdoType.num.copy(fontSize = 14.sp),
                    color = BdoColors.goldHi,
                )
            }
        }
    }
}

private fun dialCountdown(ms: Long): String {
    if (ms <= 0) return "LIVE"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
}
