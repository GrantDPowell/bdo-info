package com.gpowell.bdoboss.ui.hub

/**
 * Tiny keyless ad/tracker blocker for the Hub WebView.
 *
 * A curated set of ad-network / tracker base domains (no full hosts-file). The
 * WebView's [android.webkit.WebViewClient.shouldInterceptRequest] consults
 * [isAdHost] per request and serves an empty response for matches, so
 * full-screen interstitials and banner scripts never load. Deliberately small
 * and conservative: every entry here is a pure ad/tracking domain so we don't
 * risk breaking site logins or first-party content (the reason Discord/OAuth
 * still works — none of its hosts are on this list).
 *
 * Matching is suffix-based: a blocked base domain `doubleclick.net` also covers
 * `ads.g.doubleclick.net`, `stats.g.doubleclick.net`, etc.
 */
object AdBlocker {

    /**
     * Base domains to block. Suffix-matched, so list the registrable domain
     * (e.g. `googlesyndication.com`), not individual subdomains.
     */
    val blockedDomains: Set<String> = setOf(
        // Google ad stack
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        // Amazon ads
        "amazon-adsystem.com",
        "adsystem.amazon.com",
        // Major ad exchanges / SSPs
        "adnxs.com",            // AppNexus / Xandr
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "casalemedia.com",
        "smartadserver.com",
        "adform.net",
        "3lift.com",            // TripleLift
        "sharethrough.com",
        "indexww.com",          // Index Exchange
        "bidswitch.net",
        "districtm.io",
        "gumgum.com",
        "yieldmo.com",
        "media.net",
        "sonobi.com",
        "spotxchange.com",
        "spotx.tv",
        "teads.tv",
        "outbrain.com",
        "taboola.com",
        "revcontent.com",
        "mgid.com",
        "adblade.com",
        "contentad.net",
        "zergnet.com",
        "propellerads.com",
        "popads.net",
        "popcash.net",
        "adcash.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        // Trackers / analytics commonly bundled with ads
        "scorecardresearch.com",
        "quantserve.com",
        "quantcount.com",
        "moatads.com",
        "adsafeprotected.com",  // Integral Ad Science
        "doubleverify.com",
        "bluekai.com",
        "demdex.net",           // Adobe Audience Manager
        "rlcdn.com",            // LiveRamp
        "crwdcntrl.net",        // Lotame
        "agkn.com",
        "mathtag.com",          // MediaMath
        "adsrvr.org",           // The Trade Desk
        "everesttech.net",
        "krxd.net",             // Salesforce/Krux
        "bidr.io",              // Beeswax
        "serving-sys.com",      // Sizmek
        "amplitude.com",
        "mixpanel.com",
        "hotjar.com",
        "fullstory.com",
        "mouseflow.com",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "chartbeat.com",
        "newrelic.com",
        "nr-data.net",
        "segment.com",
        "segment.io",
    )

    /**
     * True if [host] is, or is a subdomain of, any [blockedDomains] entry.
     * Null/blank hosts are never blocked.
     */
    /**
     * Login-critical hosts that must NEVER be blocked, even if a future blocklist
     * entry would otherwise match — captcha (hCaptcha/reCAPTCHA/Cloudflare/Arkose),
     * Discord, and OAuth providers. Keeps "Log in with Discord" flows working.
     */
    private val allowlist: Set<String> = setOf(
        "hcaptcha.com", "recaptcha.net", "gstatic.com", "google.com",
        "cloudflare.com", "challenges.cloudflare.com", "arkoselabs.com", "funcaptcha.com",
        "discord.com", "discord.gg", "discordapp.com", "discordapp.net",
        "accounts.google.com",
    )

    private fun Set<String>.matches(host: String) =
        any { domain -> host == domain || host.endsWith(".$domain") }

    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trimEnd('.')
        if (allowlist.matches(h)) return false
        return blockedDomains.matches(h)
    }
}
