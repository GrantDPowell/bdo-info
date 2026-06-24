package com.gpowell.bdoboss.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.Monogram

/**
 * A BDO class icon (bundled in drawable as `class_<slug>.png`, slug = lowercase class name
 * with spaces removed, e.g. "Dark Knight" → class_darkknight). Falls back to the gold
 * [Monogram] initials tile when the class is unknown / blank.
 */
@Composable
fun ClassIcon(className: String, size: Dp = 38.dp, grade: Int = 0, glow: Boolean = false) {
    val ctx = LocalContext.current
    val resId = remember(className) {
        val slug = className.lowercase().filter { it.isLetterOrDigit() }
        if (slug.isBlank()) 0 else ctx.resources.getIdentifier("class_$slug", "drawable", ctx.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = className,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .background(BdoColors.surface2),
        )
    } else {
        Monogram(text = className.take(2).uppercase(), grade = grade, size = size, glow = glow)
    }
}
