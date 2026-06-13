package com.gpowell.bdoboss.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.bossIcons
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.Monogram
import com.gpowell.bdoboss.ui.theme.goldGlow

/** Short monogram for a boss name: initials of multi-word names, else first two letters. */
fun bossMonogram(boss: String): String {
    val words = boss.trim().split(" ").filter { it.isNotBlank() }
    return if (words.size > 1) {
        words.joinToString("") { it.first().uppercase() }
    } else {
        boss.take(2).uppercase()
    }
}

/**
 * Faceted boss tile: real portrait inside a rounded-square with a gold hairline (and optional
 * glow for the NEXT/imminent spawn), falling back to a boss-gold rarity Monogram.
 */
@Composable
fun BossTile(boss: String, size: Dp = 44.dp, glow: Boolean = false) {
    val res = bossIcons[boss]
    val shape = RoundedCornerShape(12.dp)
    if (res != null) {
        Box(
            Modifier
                .size(size)
                .then(if (glow) Modifier.goldGlow(shape, 10.dp) else Modifier)
                .clip(shape)
                .background(BdoColors.surface2)
                .border(1.dp, BdoColors.goldLine, shape),
        ) {
            Image(
                painter = painterResource(res),
                contentDescription = boss,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        }
    } else {
        Monogram(text = bossMonogram(boss), grade = 3, size = size, glow = glow)
    }
}

/** Boss portrait; falls back to the colored monogram circle when no art exists. */
@Composable
fun BossIcon(boss: String, size: Dp = 28.dp) {
    val res = bossIcons[boss]
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = boss,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bossColors[boss] ?: Color.Gray),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                boss.first().toString(),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp,
            )
        }
    }
}

/** Item icon loaded from assets/item_icons/<iconFile>; falls back to a gold-tinted box. */
@Composable
fun ItemIcon(iconFile: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap = remember(iconFile) {
        if (iconFile.isBlank()) null
        else runCatching {
            context.assets.open("item_icons/$iconFile").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x33D4AF37)),
            contentAlignment = Alignment.Center,
        ) { Text("⚔", fontSize = 16.sp) }
    }
}
