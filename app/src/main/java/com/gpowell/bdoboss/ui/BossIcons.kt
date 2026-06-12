package com.gpowell.bdoboss.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.R

val bossIcons: Map<String, Int> = mapOf(
    "Kzarka" to R.drawable.boss_kzarka,
    "Kutum" to R.drawable.boss_kutum,
    "Nouver" to R.drawable.boss_nouver,
    "Karanda" to R.drawable.boss_karanda,
    "Garmoth" to R.drawable.boss_garmoth,
    "Offin" to R.drawable.boss_offin,
    "Vell" to R.drawable.boss_vell,
    "Uturi" to R.drawable.boss_uturi,
    "Sangoon" to R.drawable.boss_sangoon,
    "Bulgasal" to R.drawable.boss_bulgasal,
    "Quint" to R.drawable.boss_quint,
    "Muraka" to R.drawable.boss_muraka,
    "Golden Pig King" to R.drawable.boss_golden_pig_king,
)

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
