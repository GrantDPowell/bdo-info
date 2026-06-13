package com.gpowell.bdoboss.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Soft gold outer glow — reserved for live / NEXT / imminent / focused surfaces. */
fun Modifier.goldGlow(shape: Shape, elevation: Dp = 16.dp): Modifier =
    this.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = BdoColors.goldGlow,
        spotColor = BdoColors.goldGlow,
    )

/** The faceted-gem motif: a small rotated square. The brand bullet/marker. */
@Composable
fun Diamond(
    size: Dp = 7.dp,
    color: Color = BdoColors.gold,
    glow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .then(if (glow) Modifier.goldGlow(RoundedCornerShape(1.dp), 6.dp) else Modifier)
            .rotate(45f)
            .clip(RoundedCornerShape(1.dp))
            .background(color),
    )
}

/**
 * Engraved-hairline card — the signature surface. Depth comes from a 1dp gold hairline
 * (no Material drop shadow on dark). `facet` chamfers the top-right corner; `glow` adds the
 * reserved gold outer glow. Clickable variant adds a subtle press-scale.
 */
@Composable
fun BdoCard(
    modifier: Modifier = Modifier,
    facet: Boolean = false,
    glow: Boolean = false,
    raised: Boolean = false,
    hairline: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape: Shape = if (facet) FacetedShape() else RoundedCornerShape(16.dp)
    val surf = if (raised) BdoColors.surface2 else BdoColors.surface1
    val borderColor = if (hairline) BdoColors.goldLine else BdoColors.line

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardPress",
    )

    var m = modifier
    if (glow) m = m.goldGlow(shape)
    m = m
        .let { if (onClick != null) it.graphicsScale(scale) else it }
        .clip(shape)
        .background(surf)
    if (onClick != null) {
        m = m.clickable(
            interactionSource = interaction,
            indication = ripple(color = BdoColors.gold),
            onClick = onClick,
        )
    }
    m = m.border(BorderStroke(1.dp, borderColor), shape)

    Column(modifier = m.padding(contentPadding), content = content)
}

private fun Modifier.graphicsScale(scale: Float): Modifier =
    this.graphicsLayer { scaleX = scale; scaleY = scale }

/**
 * Item-icon fallback: a rarity-tinted rounded square with the item/boss initials and a
 * faceted corner notch. `glow` adds a soft rarity-colored halo.
 */
@Composable
fun Monogram(
    text: String,
    grade: Int = 0,
    size: Dp = 44.dp,
    radius: Dp = 12.dp,
    glow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = rarityTint(grade)
    val shape = RoundedCornerShape(radius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(if (glow) Modifier.goldGlow(shape, 10.dp) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.20f), c.copy(alpha = 0.06f))))
            .border(1.dp, c.copy(alpha = 0.42f), shape),
    ) {
        Text(
            text = text,
            color = c,
            fontFamily = GeistSans,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = (size.value * 0.34f).coerceAtMost(20f).sp,
                letterSpacing = (-0.5).sp,
            ),
        )
        // faceted corner notch
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(4.dp)
                .rotate(45f)
                .clip(RoundedCornerShape(1.dp))
                .background(c.copy(alpha = 0.7f)),
        )
    }
}

/** UPPERCASE overline label with the diamond bullet. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Diamond(size = 6.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            style = BdoType.overline,
            color = BdoColors.onFaint,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Filter / sort / range chip. */
@Composable
fun BdoChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (active) Modifier.goldGlow(shape, 8.dp) else Modifier)
            .clip(shape)
            .background(if (active) BdoColors.gold.copy(alpha = 0.16f) else BdoColors.surface2)
            .border(1.dp, if (active) BdoColors.goldLine else BdoColors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) BdoColors.goldHi else BdoColors.onMuted,
            maxLines = 1,
        )
    }
}

/** Segmented sub-tab selector: a surface track with a gold-gradient pill on the active tab. */
@Composable
fun BdoSubTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(BdoColors.surface1)
            .border(1.dp, BdoColors.line, trackShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            val pillShape = RoundedCornerShape(10.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .then(if (on) Modifier.goldGlow(pillShape, 8.dp) else Modifier)
                    .clip(pillShape)
                    .background(
                        if (on) Brush.verticalGradient(listOf(BdoColors.goldHi, BdoColors.gold))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
            ) {
                Text(
                    t,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) BdoColors.onGold else BdoColors.onMuted,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

enum class PillStatus { ONLINE, OFFLINE, BUNDLED, CHECKING }

/** Status pill — Online (pulsing dot + glow) / Offline (static) / Bundled (gold) / Checking. */
@Composable
fun BdoStatusPill(status: PillStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        PillStatus.ONLINE -> "Online" to BdoColors.up
        PillStatus.OFFLINE -> "Offline" to BdoColors.down
        PillStatus.BUNDLED -> "Bundled" to BdoColors.gold
        PillStatus.CHECKING -> "Checking…" to BdoColors.onFaint
    }
    val shape = RoundedCornerShape(50)
    val pulse = rememberInfiniteTransition(label = "pillPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.34f), shape)
            .padding(start = 8.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val animate = status == PillStatus.ONLINE
        Box(
            Modifier
                .size(6.dp)
                .then(
                    if (status != PillStatus.OFFLINE) {
                        Modifier.drawBehind {
                            drawCircle(color = color, radius = size.minDimension * 0.9f, center = center, alpha = 0.5f)
                        }
                    } else Modifier,
                )
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = if (animate) dotAlpha else 1f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = BdoType.overline.copy(letterSpacing = 1.sp), color = color)
    }
}
