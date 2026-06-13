package com.gpowell.bdoboss.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.R

/**
 * "Obsidian Vitrine" design language — a jeweler's display case for a dark-fantasy MMO.
 * Premium obsidian + gold, gold treated as light (reserved for live/active/imminent),
 * a faceted-gem motif (chamfered card corners + a rotated-square "diamond" marker).
 *
 * All design tokens live here so screens read from one source of truth. The MaterialTheme
 * wrapper maps these into M3 color/type/shape roles so untouched components inherit the look.
 */
/** A full color palette. Two ship: [VitrinePalette] (warm gold) and [EclipsePalette] (cool cyan). */
data class BdoPalette(
    val bg0: Color, val bg1: Color,
    val surface1: Color, val surface2: Color, val surface3: Color, val surfaceHi: Color,
    val gold: Color, val goldHi: Color, val goldDeep: Color, val goldLine: Color, val goldGlow: Color, val onGold: Color,
    val onBg: Color, val onMuted: Color, val onFaint: Color,
    val up: Color, val down: Color, val live: Color, val live2: Color,
    val line: Color, val lineStrong: Color, val scrim: Color,
)

val VitrinePalette = BdoPalette(
    bg0 = Color(0xFF0A0907), bg1 = Color(0xFF13100A),
    surface1 = Color(0xFF16130D), surface2 = Color(0xFF1E1A11), surface3 = Color(0xFF231E14), surfaceHi = Color(0xFF2A2417),
    gold = Color(0xFFCBA135), goldHi = Color(0xFFEAC766), goldDeep = Color(0xFF8A7220),
    goldLine = Color(0xFFCBA135).copy(alpha = 0.30f), goldGlow = Color(0xFFCBA135).copy(alpha = 0.55f), onGold = Color(0xFF130F06),
    onBg = Color(0xFFF3ECDD), onMuted = Color(0xFFB4AB98), onFaint = Color(0xFF7B7464),
    up = Color(0xFF76C765), down = Color(0xFFE2604A), live = Color(0xFFCBA135), live2 = Color(0xFFEAC766),
    line = Color(0xFFF3ECDD).copy(alpha = 0.07f), lineStrong = Color(0xFFF3ECDD).copy(alpha = 0.13f), scrim = Color(0xFF060503).copy(alpha = 0.74f),
)

/** Cool, daring alternative — obsidian-blue with a cyan "live" glow. */
val EclipsePalette = BdoPalette(
    bg0 = Color(0xFF06070B), bg1 = Color(0xFF0B0D14),
    surface1 = Color(0xFF0F121B), surface2 = Color(0xFF161A26), surface3 = Color(0xFF1B2030), surfaceHi = Color(0xFF222840),
    gold = Color(0xFFE3C46B), goldHi = Color(0xFFF6E3A6), goldDeep = Color(0xFF9C8743),
    goldLine = Color(0xFFE3C46B).copy(alpha = 0.26f), goldGlow = Color(0xFF78D6E8).copy(alpha = 0.55f), onGold = Color(0xFF0A0B10),
    onBg = Color(0xFFEEF1F8), onMuted = Color(0xFF9AA3B8), onFaint = Color(0xFF5E6678),
    up = Color(0xFF56D6A0), down = Color(0xFFFF6B6B), live = Color(0xFF78D6E8), live2 = Color(0xFFA6ECF8),
    line = Color(0xFFEEF1F8).copy(alpha = 0.07f), lineStrong = Color(0xFFEEF1F8).copy(alpha = 0.14f), scrim = Color(0xFF040509).copy(alpha = 0.76f),
)

/**
 * Active palette accessor. Backed by Compose state so reads inside composables observe
 * palette changes — flipping [BdoColors.palette] re-themes the whole app with no call-site
 * changes (every screen reads `BdoColors.x`).
 */
object BdoColors {
    var palette: BdoPalette by mutableStateOf(VitrinePalette)

    val bg0 get() = palette.bg0
    val bg1 get() = palette.bg1
    val surface1 get() = palette.surface1
    val surface2 get() = palette.surface2
    val surface3 get() = palette.surface3
    val surfaceHi get() = palette.surfaceHi
    val gold get() = palette.gold
    val goldHi get() = palette.goldHi
    val goldDeep get() = palette.goldDeep
    val goldLine get() = palette.goldLine
    val goldGlow get() = palette.goldGlow
    val onGold get() = palette.onGold
    val onBg get() = palette.onBg
    val onMuted get() = palette.onMuted
    val onFaint get() = palette.onFaint
    val up get() = palette.up
    val down get() = palette.down
    val live get() = palette.live
    val live2 get() = palette.live2
    val line get() = palette.line
    val lineStrong get() = palette.lineStrong
    val scrim get() = palette.scrim
}

/** Rarity / grade tints (0..5) — item monogram tiles, rarity dots. */
val RarityTints = listOf(
    Color(0xFFC7CAD2), // 0 Base (white-gray)
    Color(0xFF5FC36A), // 1 Uncommon (green)
    Color(0xFF4E8FE0), // 2 Rare (blue)
    Color(0xFFD7A93A), // 3 Boss (gold)
    Color(0xFFEA7733), // 4 Epic (orange-red)
    Color(0xFFD23B53), // 5 Legend (crimson)
)

fun rarityTint(grade: Int): Color = RarityTints[grade.coerceIn(0, RarityTints.lastIndex)]

/** A rarity tint per boss, for the Spawn Dial beads / monograms (visual variety). */
fun bossDialTint(boss: String): Color = rarityTint(
    when (boss) {
        "Garmoth", "Kzarka", "Bulgasal" -> 5
        "Nouver", "Kutum" -> 4
        "Vell", "Offin" -> 2
        "Karanda", "Uturi" -> 1
        else -> 3
    },
)

// ── Fonts ────────────────────────────────────────────────────────────────────
val GeistSans = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Geist Mono — all numerics (countdowns, prices, stock/volume). Monospaced = tabular. */
val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
)

/** Marcellus — Roman inscriptional caps, used for boss & item names (engraved-stone gravitas). */
val MarcellusDisplay = FontFamily(Font(R.font.marcellus_regular, FontWeight.Normal))

/** Extra brand text styles not covered by M3 slots (hero countdown, overline, mono figures). */
object BdoType {
    val hero = TextStyle(
        fontFamily = GeistMono, fontSize = 40.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 42.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum",
    )
    val heroSm = TextStyle(
        fontFamily = GeistMono, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = "tnum",
    )
    val num = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val numS = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val overline = TextStyle(
        fontFamily = GeistSans, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 13.sp, letterSpacing = 1.6.sp,
    )
    /** Marcellus display — boss & item names (Orrery direction). */
    val display = TextStyle(
        fontFamily = MarcellusDisplay, fontSize = 22.sp, fontWeight = FontWeight.Normal,
        lineHeight = 26.sp, letterSpacing = 1.sp,
    )
}

private val BdoTypography = Typography(
    displayLarge = BdoType.hero,
    displayMedium = BdoType.heroSm,
    headlineSmall = TextStyle(
        fontFamily = GeistMono, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = "tnum",
    ),
    titleLarge = TextStyle(
        fontFamily = GeistSans, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp, letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistSans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GeistSans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp, letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(fontFamily = GeistSans, fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = GeistSans, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = GeistSans, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = GeistSans, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = GeistSans, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = GeistSans, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

private val BdoShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
)

private fun bdoColorScheme(p: BdoPalette) = darkColorScheme(
    primary = p.gold,
    onPrimary = p.onGold,
    primaryContainer = p.surfaceHi,
    onPrimaryContainer = p.goldHi,
    secondary = p.goldHi,
    onSecondary = p.onGold,
    background = p.bg0,
    onBackground = p.onBg,
    surface = p.surface1,
    onSurface = p.onBg,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.onMuted,
    surfaceContainerLowest = p.bg1,
    surfaceContainerLow = p.surface1,
    surfaceContainer = p.surface1,
    surfaceContainerHigh = p.surface2,
    surfaceContainerHighest = p.surface2,
    outline = p.goldLine,
    outlineVariant = p.line,
    error = p.down,
    onError = p.onGold,
    scrim = p.scrim,
)

/** Backwards-compatible accent handle used across screens (tracks the active palette). */
val BdoGold: Color get() = BdoColors.gold

/** Standard motion easings (handoff §3). Use with tween(durationMillis, easing = ...). */
object Motion {
    val enter = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val emphasis = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/** Root background gradient (bg0 -> bg1 -> bg0). Computed so it tracks the active palette. */
val BdoBackground: Brush
    get() = Brush.verticalGradient(
        0f to BdoColors.bg0,
        0.5f to BdoColors.bg1,
        1f to BdoColors.bg0,
    )

/**
 * Faceted-gem card shape: rounded corners except a 45° chamfer cut into the top-right —
 * the ownable motif. Used for "hero" surfaces (LIVE header, NEXT spawn, price card).
 */
class FacetedShape(
    private val corner: Dp = 16.dp,
    private val notch: Dp = 16.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }.coerceAtMost(size.minDimension / 2f)
        val n = with(density) { notch.toPx() }.coerceAtMost(size.width)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - n, 0f)
            lineTo(w, n)                       // chamfer
            lineTo(w, h - r)
            quadraticBezierTo(w, h, w - r, h)  // bottom-right
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r) // bottom-left
            lineTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)    // top-left
            close()
        }
        return Outline.Generic(path)
    }
}

/** Convenience screen padding token. */
val ScreenPadding = PaddingValues(horizontal = 16.dp)

@Composable
fun BdoBossTheme(eclipse: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (eclipse) EclipsePalette else VitrinePalette
    // Drive the global accessor so every BdoColors.* read re-themes on toggle.
    SideEffect { BdoColors.palette = palette }
    MaterialTheme(
        colorScheme = bdoColorScheme(palette),
        typography = BdoTypography,
        shapes = BdoShapes,
        content = content,
    )
}
