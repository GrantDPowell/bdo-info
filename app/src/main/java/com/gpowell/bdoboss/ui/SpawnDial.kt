package com.gpowell.bdoboss.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import androidx.compose.ui.input.pointer.pointerInput
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
private const val Rv = 146f   // ~12% larger ring (fills more of the width)
private const val WINDOW_MS = 24L * 3600L * 1000L

private data class DialNode(val spawn: Spawn, val ms: Long, val deg: Float, val displayDeg: Float = deg)

/** A persistent constellation star for one boss: fraction along its spoke, size, twinkle offset. */
private class DialStar(val frac: Float, val size: Float, val tw: Float)

/**
 * Spread bead angles so spawns close in time don't stack. Keeps the true time order but
 * enforces a minimum visual gap (the dial is only ~14°/hour, so bosses an hour or two
 * apart would otherwise overlap).
 */
private fun spreadNodes(nodes: List<DialNode>, minGap: Float = 17f): List<DialNode> {
    val sorted = nodes.sortedBy { it.deg }
    var last = -1000f
    return sorted.map { n ->
        val d = if (n.deg - last < minGap) last + minGap else n.deg
        last = d
        n.copy(displayDeg = d.coerceAtMost(345f))
    }
}

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
        val raw = spawns.map { s ->
            val ms = Duration.between(now, s.at).toMillis()
            val frac = (ms.toFloat() / WINDOW_MS).coerceIn(0f, 0.92f)
            DialNode(s, ms, frac * 330f)
        }.filter { it.ms >= -60_000 }
        spreadNodes(raw)
    }
    val next = nodes.minByOrNull { if (it.ms < -60_000) Long.MAX_VALUE else it.ms } ?: nodes.firstOrNull()

    // Fixed layout (no scroll): ribbon at top, dial centered, Up Next pinned to the bottom
    // so the dial gets the most space.
    Column(
        Modifier.fillMaxSize().padding(top = 2.dp, bottom = 12.dp),
    ) {
        headerContent?.invoke()
        if (next == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("No upcoming spawns", color = BdoColors.onFaint)
            }
            return@Column
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            DialCanvas(nodes = nodes, next = next, now = now, fx = fx, onSpawnClick = onSpawnClick)
        }
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
    // Constellation: a star twinkle phase + a pulse that travels along the connecting line.
    val twinkle by infinite.animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "twinkle",
    )
    val constPulse by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "constPulse",
    )

    // Persistent stars: one per boss, seeded by spawn time (+ a manual regen seed for the
    // spin easter egg) so a boss KEEPS its star (35–95% out, varied size, twinkle offset).
    // The set changes only when a boss comes/goes or the wheel is spun.
    val scope = rememberCoroutineScope()
    var regenSeed by remember { mutableIntStateOf(0) }
    val regen = remember { Animatable(1f) }   // 1 = settled; animates 0→1 to ignite stars
    val spin = remember { Animatable(0f) }     // boss-wheel spin (easter egg), degrees
    val bossKey = nodes.map { it.spawn.at.epochSecond }.sorted().joinToString(",")
    val stars = remember(bossKey, regenSeed) {
        nodes.associate { n ->
            val r = kotlin.random.Random(n.spawn.at.epochSecond * 73L + regenSeed)
            n.spawn.at.epochSecond to DialStar(
                frac = 0.35f + r.nextFloat() * 0.60f,
                size = 0.80f + r.nextFloat() * 1.70f,  // min held at 0.8; max raised
                tw = r.nextFloat() * (2 * Math.PI).toFloat(),
            )
        }
    }
    val measure = remember { androidx.compose.ui.graphics.PathMeasure() }

    // Ignite the constellation whenever the boss set changes OR the wheel is spun.
    LaunchedEffect(bossKey, regenSeed) {
        regen.snapTo(0f)
        regen.animateTo(1f, tween(900 + nodes.size * 170, easing = LinearEasing))
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        val wPx = with(density) { maxWidth.toPx() }
        val scale = wPx / 360f
        val hDp = with(density) { (372f * scale).toDp() }
        fun p(r: Float, deg: Float) = polar(CXv * scale, CYv * scale, r * scale, deg)

        Box(
            Modifier
                .fillMaxWidth()
                .height(hDp)
                // Easter egg: long-press the dial to spin the boss-wheel one revolution,
                // which regenerates the constellation (stars re-ignite one by one).
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        scope.launch {
                            if (spin.isRunning) return@launch
                            regenSeed++
                            spin.snapTo(0f)
                            spin.animateTo(360f, tween(1300, easing = FastOutSlowInEasing))
                            spin.snapTo(0f)
                        }
                    })
                },
        ) {
            if (fx) GoldDust(Modifier.fillMaxSize(), count = 60)

            Canvas(Modifier.fillMaxSize()) {
                val cx = CXv * scale; val cy = CYv * scale; val R = Rv * scale
                val glowC = Offset(cx, cy - R * 0.05f)

                // amplified core glow — a wide soft bloom + a brighter inner core
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(BdoColors.goldGlow.copy(alpha = 0.22f), Color.Transparent),
                        center = glowC, radius = R * 1.35f,
                    ),
                    radius = R * 1.35f, center = glowC,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(BdoColors.goldGlow.copy(alpha = 0.5f), Color.Transparent),
                        center = glowC, radius = R * 0.78f,
                    ),
                    radius = R * 0.78f, center = glowC,
                )
                // faint concentric depth rings
                drawCircle(BdoColors.line, radius = R * 0.55f, center = Offset(cx, cy), style = Stroke(width = 1f * scale))
                drawCircle(BdoColors.line, radius = R * 0.78f, center = Offset(cx, cy), style = Stroke(width = 1f * scale))
                // outer rim glow
                drawCircle(BdoColors.goldGlow.copy(alpha = 0.18f), radius = R + 6f * scale, center = Offset(cx, cy), style = Stroke(width = 6f * scale))

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

                // progress wedge: top(now) -> next, with a soft glow underlay
                val wedgeSweep = next.displayDeg.coerceAtLeast(2f)
                drawArc(
                    color = BdoColors.goldGlow.copy(alpha = 0.55f),
                    startAngle = -90f, sweepAngle = wedgeSweep, useCenter = false,
                    topLeft = Offset(cx - R, cy - R),
                    size = androidx.compose.ui.geometry.Size(R * 2, R * 2),
                    style = Stroke(width = 7f * scale, cap = StrokeCap.Round),
                )
                drawArc(
                    color = BdoColors.goldHi,
                    startAngle = -90f, sweepAngle = wedgeSweep, useCenter = false,
                    topLeft = Offset(cx - R, cy - R),
                    size = androidx.compose.ui.geometry.Size(R * 2, R * 2),
                    style = Stroke(width = 3f * scale, cap = StrokeCap.Round),
                )

                // ── Constellation: per-boss twinkling stars that IGNITE one-by-one (BDO
                // enhancement style — slam-in + white flash + shockwave ring + rays), linked
                // in angular order by a line that draws on as each star lands. Spins with the
                // wheel. Regenerates (replays the ignite) on boss change or the spin easter egg.
                if (fx && nodes.size >= 2) {
                    val sorted = nodes.sortedBy { it.displayDeg }
                    val n = sorted.size
                    val white = Color(0xFFFFF6DC)
                    fun eo(x: Float) = 1f - (1f - x) * (1f - x)            // ease-out
                    // per-star ignite progress along the regen timeline (staggered)
                    fun rawOf(i: Int): Float {
                        val start = (i.toFloat() / n) * 0.72f
                        return ((regen.value - start) / 0.22f).coerceIn(0f, 1f)
                    }
                    val pts = sorted.map { node ->
                        val s = stars[node.spawn.at.epochSecond] ?: DialStar(0.5f, 1f, 0f)
                        Triple(p(Rv * s.frac, node.displayDeg + spin.value), s, node)
                    }

                    // links draw on, gated by the later-igniting endpoint of each segment
                    for (i in 0 until n) {
                        val end = (i + 1) % n
                        val gate = eo(rawOf(if (end > i) end else i))
                        if (gate <= 0f) continue
                        val a0 = pts[i].first
                        val b0 = pts[end].first
                        val tip = Offset(a0.x + (b0.x - a0.x) * gate, a0.y + (b0.y - a0.y) * gate)
                        drawLine(
                            BdoColors.goldHi.copy(alpha = 0.22f * gate),
                            a0, tip, strokeWidth = 1.2f * scale, cap = StrokeCap.Round,
                        )
                    }

                    // traveling pulse — only once the whole loop has settled
                    if (regen.value >= 0.999f) {
                        val path = Path()
                        pts.forEachIndexed { i, (pt, _, _) -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
                        path.close()
                        measure.setPath(path, false)
                        val len = measure.length
                        if (len > 0f) {
                            val pos = measure.getPosition(len * constPulse)
                            drawCircle(BdoColors.goldGlow.copy(alpha = 0.5f), radius = 6f * scale, center = pos)
                            drawCircle(BdoColors.goldHi, radius = 2.2f * scale, center = pos)
                        }
                    }

                    // stars (ignite then twinkle)
                    pts.forEachIndexed { i, (pt, s, node) ->
                        val raw = rawOf(i)
                        if (raw <= 0f) return@forEachIndexed
                        val tint = bossDialTint(node.spawn.bosses.first())
                        val tf = 0.55f + 0.45f * kotlin.math.sin(twinkle + s.tw)
                        val base = 2.4f * scale * s.size * (0.7f + 0.5f * tf)
                        val slam = 1f + (1f - eo(raw)) * 1.9f         // heavy slam-in (2.9x → 1x)
                        val rad = base * slam
                        val flash = kotlin.math.sin(raw * Math.PI.toFloat()) // 0→1→0 over ignite
                        val a = raw * (0.5f + 0.5f * tf)

                        // shockwave ring expanding out of the star as it lands
                        if (raw < 1f) {
                            drawCircle(
                                tint.copy(alpha = (1f - raw) * 0.6f),
                                radius = base * (1f + raw * 3.2f), center = pt,
                                style = Stroke(width = 1.6f * scale),
                            )
                        }
                        // halo
                        drawCircle(tint.copy(alpha = a * 0.25f), radius = rad * 2.6f, center = pt)
                        // 4-point rays (boosted during the flash)
                        val ray = rad * (2.2f + flash * 2.0f)
                        val rc = white.copy(alpha = (a + flash).coerceIn(0f, 1f))
                        drawLine(rc, Offset(pt.x - ray, pt.y), Offset(pt.x + ray, pt.y), strokeWidth = 1.1f * scale, cap = StrokeCap.Round)
                        drawLine(rc, Offset(pt.x, pt.y - ray), Offset(pt.x, pt.y + ray), strokeWidth = 1.1f * scale, cap = StrokeCap.Round)
                        // core + white-hot flash
                        drawCircle(BdoColors.goldHi.copy(alpha = a), radius = rad, center = pt)
                        if (flash > 0.01f) drawCircle(white.copy(alpha = flash * 0.9f), radius = rad * 0.7f, center = pt)
                    }
                }

                // NOW marker: a glowing gold arrowhead sitting ABOVE the ring, pointing down
                // at the top of the ring (= live "now"; spawns sweep clockwise away from it).
                val ringTopY = cy - R
                val aw = 8f * scale
                val baseY = ringTopY - 17f * scale
                val tipY = ringTopY - 3f * scale
                val arrow = Path().apply {
                    moveTo(cx - aw, baseY)
                    lineTo(cx + aw, baseY)
                    lineTo(cx, tipY)
                    close()
                }
                drawCircle(BdoColors.goldGlow.copy(alpha = 0.5f), radius = 9f * scale, center = Offset(cx, baseY + 4f * scale))
                drawPath(arrow, BdoColors.goldHi)
            }

            // ── boss icons (tappable portraits at their time-angle, spin with the wheel) ──
            nodes.forEach { n ->
                val isNext = n == next
                val tileDp = if (isNext) 50.dp else 42.dp
                val tilePx = with(density) { tileDp.toPx() }
                val pos = p(Rv, n.displayDeg + spin.value)
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

            // center overlay (next spawn) — constrained to sit within the inner ring
            CenterStack(
                next = next,
                fx = fx,
                maxWidth = with(density) { (Rv * 0.55f * 2f * scale).toDp() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = hDp * 0.32f),
                onOpen = { onSpawnClick(next.spawn) },
            )
            androidx.compose.material3.Text(
                "NOW",
                style = BdoType.overline.copy(fontSize = 8.5.sp),
                color = BdoColors.onFaint,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { ((CYv - Rv) * scale).toDp() } - 34.dp),
            )
        }
    }
}

@Composable
private fun CenterStack(
    next: DialNode,
    fx: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val co = next.spawn.bosses.size > 1
    val imminent = next.ms in 0..(10 * 60 * 1000)
    val fmt = remember { DateTimeFormatter.ofPattern("EEE h:mm a") }
    val local = next.spawn.at.atZone(ZoneId.systemDefault())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = maxWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(6.dp),
    ) {
        androidx.compose.material3.Text(
            (if (imminent) "Spawning now" else "Next spawn").uppercase(),
            style = BdoType.overline.copy(fontSize = 8.sp),
            color = if (imminent) BdoColors.live2 else BdoColors.gold,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            next.spawn.bosses.joinToString(" + "),
            style = BdoType.display.copy(fontSize = if (co) 15.sp else 19.sp),
            color = BdoColors.onBg,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            text = dialCountdown(next.ms),
            style = BdoType.hero.copy(
                fontSize = if (imminent) 34.sp else 29.sp,
                brush = if (imminent) null else shimmerGoldBrush(fx),
            ),
            color = if (imminent) BdoColors.live2 else BdoColors.goldHi,
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            fmt.format(local) + if (co) " · Co-spawn" else "",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = BdoColors.onFaint,
            maxLines = 1,
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
