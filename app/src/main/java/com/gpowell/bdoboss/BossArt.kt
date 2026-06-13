package com.gpowell.bdoboss

import com.gpowell.bdoboss.R

/** Shared map of boss name → drawable resource id, used by both UI and notifications. */
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
    // Field bosses that appear in the live feed (bundled .webp so they work offline / on VPN).
    "Black Shadow" to R.drawable.boss_black_shadow,
)
