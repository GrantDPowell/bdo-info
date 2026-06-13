package com.gpowell.bdoboss.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
object BdoColors {
    // Backgrounds (app paints a vertical gradient bg0 -> bg1)
    val bg0 = Color(0xFF0A0907)
    val bg1 = Color(0xFF13100A)
    // Surfaces / elevation
    val surface1 = Color(0xFF16130D)
    val surface2 = Color(0xFF1E1A11)
    val surface3 = Color(0xFF231E14)
    val surfaceHi = Color(0xFF2A2417)
    // Gold (the accent — used as light & hairlines, never big fills)
    val gold = Color(0xFFCBA135)
    val goldHi = Color(0xFFEAC766)
    val goldDeep = Color(0xFF8A7220)
    val goldLine = gold.copy(alpha = 0.30f)   // engraved hairline
    val goldGlow = gold.copy(alpha = 0.55f)   // glow hue
    val onGold = Color(0xFF130F06)
    // Text
    val onBg = Color(0xFFF3ECDD)
    val onMuted = Color(0xFFB4AB98)
    val onFaint = Color(0xFF7B7464)
    // Semantics
    val up = Color(0xFF76C765)
    val down = Color(0xFFE2604A)
    val live = gold
    val live2 = goldHi
    // Lines / scrims
    val line = onBg.copy(alpha = 0.07f)
    val lineStrong = onBg.copy(alpha = 0.13f)
    val scrim = Color(0xFF060503).copy(alpha = 0.74f)
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

private val BdoColorScheme = darkColorScheme(
    primary = BdoColors.gold,
    onPrimary = BdoColors.onGold,
    primaryContainer = BdoColors.surfaceHi,
    onPrimaryContainer = BdoColors.goldHi,
    secondary = BdoColors.goldHi,
    onSecondary = BdoColors.onGold,
    background = BdoColors.bg0,
    onBackground = BdoColors.onBg,
    surface = BdoColors.surface1,
    onSurface = BdoColors.onBg,
    surfaceVariant = BdoColors.surface2,
    onSurfaceVariant = BdoColors.onMuted,
    surfaceContainerLowest = BdoColors.bg1,
    surfaceContainerLow = BdoColors.surface1,
    surfaceContainer = BdoColors.surface1,
    surfaceContainerHigh = BdoColors.surface2,
    surfaceContainerHighest = BdoColors.surface2,
    outline = BdoColors.goldLine,
    outlineVariant = BdoColors.line,
    error = BdoColors.down,
    onError = BdoColors.onGold,
    scrim = BdoColors.scrim,
)

/** Backwards-compatible accent handle used across screens. */
val BdoGold = BdoColors.gold

/** Standard motion easings (handoff §3). Use with tween(durationMillis, easing = ...). */
object Motion {
    val enter = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val emphasis = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/** Root background gradient (bg0 -> bg1 -> bg0). */
val BdoBackground = Brush.verticalGradient(
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
fun BdoBossTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BdoColorScheme,
        typography = BdoTypography,
        shapes = BdoShapes,
        content = content,
    )
}
