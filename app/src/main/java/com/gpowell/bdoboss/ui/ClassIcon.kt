package com.gpowell.bdoboss.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.R
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.Monogram

/**
 * Resolve a BDO class name to its bundled drawable. Uses an explicit compile-checked map
 * (NOT resources.getIdentifier) because this app's namespace `com.gpowell.bdoboss` differs
 * from its applicationId `org.okimasha.bdoinfo`, which makes getIdentifier(packageName,…)
 * unreliable — it was silently returning 0 and falling everything back to monograms.
 */
fun classDrawable(className: String): Int? = when (className.lowercase().filter { it.isLetterOrDigit() }) {
    "archer" -> R.drawable.class_archer
    "berserker" -> R.drawable.class_berserker
    "corsair" -> R.drawable.class_corsair
    "darkknight" -> R.drawable.class_darkknight
    "drakania" -> R.drawable.class_drakania
    "guardian" -> R.drawable.class_guardian
    "hashashin" -> R.drawable.class_hashashin
    "kunoichi" -> R.drawable.class_kunoichi
    "lahn" -> R.drawable.class_lahn
    "maegu" -> R.drawable.class_maegu
    "maehwa" -> R.drawable.class_maehwa
    "musa" -> R.drawable.class_musa
    "mystic" -> R.drawable.class_mystic
    "ninja" -> R.drawable.class_ninja
    "nova" -> R.drawable.class_nova
    "ranger" -> R.drawable.class_ranger
    "sage" -> R.drawable.class_sage
    "scholar" -> R.drawable.class_scholar
    "shai" -> R.drawable.class_shai
    "sorceress" -> R.drawable.class_sorceress
    "striker" -> R.drawable.class_striker
    "tamer" -> R.drawable.class_tamer
    "valkyrie" -> R.drawable.class_valkyrie
    "warrior" -> R.drawable.class_warrior
    "witch" -> R.drawable.class_witch
    "wizard" -> R.drawable.class_wizard
    "woosa" -> R.drawable.class_woosa
    "deadeye" -> R.drawable.class_deadeye
    "dosa" -> R.drawable.class_dosa
    "wukong" -> R.drawable.class_wukong
    "seraph" -> R.drawable.class_seraph
    else -> null
}

/**
 * A BDO class icon, falling back to the gold [Monogram] initials tile when the class has no
 * bundled image.
 */
@Composable
fun ClassIcon(className: String, size: Dp = 38.dp, grade: Int = 0, glow: Boolean = false) {
    val res = classDrawable(className)
    if (res != null) {
        // Portraits are busts — crop square biased to the top so the face shows.
        Image(
            painter = painterResource(res),
            contentDescription = className,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .background(BdoColors.surface2),
            alignment = androidx.compose.ui.Alignment.TopCenter,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        Monogram(text = className.take(2).uppercase(), grade = grade, size = size, glow = glow)
    }
}
