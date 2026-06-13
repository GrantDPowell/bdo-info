package com.gpowell.bdoboss

import com.gpowell.bdoboss.ui.hub.AdBlocker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    @Test
    fun `exact ad domain is blocked`() {
        assertTrue(AdBlocker.isAdHost("doubleclick.net"))
        assertTrue(AdBlocker.isAdHost("taboola.com"))
    }

    @Test
    fun `subdomains of ad domains are blocked`() {
        assertTrue(AdBlocker.isAdHost("ads.g.doubleclick.net"))
        assertTrue(AdBlocker.isAdHost("pagead2.googlesyndication.com"))
        assertTrue(AdBlocker.isAdHost("cdn.taboola.com"))
    }

    @Test
    fun `matching is case-insensitive and tolerates trailing dot`() {
        assertTrue(AdBlocker.isAdHost("ADS.DOUBLECLICK.NET"))
        assertTrue(AdBlocker.isAdHost("doubleclick.net."))
    }

    @Test
    fun `legitimate and login hosts are never blocked`() {
        // Discord / OAuth flow must stay clear of the blocklist.
        assertFalse(AdBlocker.isAdHost("discord.com"))
        assertFalse(AdBlocker.isAdHost("cdn.discordapp.com"))
        assertFalse(AdBlocker.isAdHost("accounts.google.com"))
        assertFalse(AdBlocker.isAdHost("bdocodex.com"))
        assertFalse(AdBlocker.isAdHost("garmoth.com"))
    }

    @Test
    fun `a domain that merely ends with an ad domain's text is not blocked`() {
        // "notdoubleclick.net" must NOT match "doubleclick.net" (needs a dot boundary).
        assertFalse(AdBlocker.isAdHost("notdoubleclick.net"))
        assertFalse(AdBlocker.isAdHost("mytaboola.com"))
    }

    @Test
    fun `null or blank host is not blocked`() {
        assertFalse(AdBlocker.isAdHost(null))
        assertFalse(AdBlocker.isAdHost(""))
        assertFalse(AdBlocker.isAdHost("   "))
    }
}
