package com.gpowell.bdoboss

import com.gpowell.bdoboss.ui.formatSilver
import org.junit.Assert.assertEquals
import org.junit.Test

class SilverFormatTest {

    @Test fun `billions show up to two decimals`() {
        assertEquals("1.23b", formatSilver(1_230_000_000))
        assertEquals("124b", formatSilver(124_000_000_000))
        assertEquals("1b", formatSilver(1_000_000_000))
    }

    @Test fun `millions trim trailing zeros`() {
        assertEquals("350m", formatSilver(350_000_000))
        assertEquals("4.5m", formatSilver(4_500_000))
        assertEquals("1.49m", formatSilver(1_490_000))
    }

    @Test fun `thousands use k`() {
        assertEquals("980k", formatSilver(980_000))
        assertEquals("1k", formatSilver(1_000))
        assertEquals("1.5k", formatSilver(1_500))
    }

    @Test fun `under one thousand is raw`() {
        assertEquals("0", formatSilver(0))
        assertEquals("999", formatSilver(999))
        assertEquals("42", formatSilver(42))
    }
}
