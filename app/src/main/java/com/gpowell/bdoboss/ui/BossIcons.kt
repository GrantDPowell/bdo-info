package com.gpowell.bdoboss.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.bossIcons

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
