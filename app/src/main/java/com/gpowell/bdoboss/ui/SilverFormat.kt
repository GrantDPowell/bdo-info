package com.gpowell.bdoboss.ui

import java.util.Locale

/**
 * Compact BDO silver formatting: 1_230_000_000 → "1.23b", 350_000_000 → "350m",
 * 4_500_000 → "4.5m", 980_000 → "980k", values under 1000 → raw digits.
 * Up to two decimals, trailing zeros trimmed.
 */
fun formatSilver(v: Long): String {
    if (v < 1000) return v.toString()
    val (divisor, suffix) = when {
        v >= 1_000_000_000 -> 1_000_000_000.0 to "b"
        v >= 1_000_000 -> 1_000_000.0 to "m"
        else -> 1_000.0 to "k"
    }
    val scaled = String.format(Locale.US, "%.2f", v / divisor)
        .trimEnd('0')
        .trimEnd('.')
    return scaled + suffix
}
