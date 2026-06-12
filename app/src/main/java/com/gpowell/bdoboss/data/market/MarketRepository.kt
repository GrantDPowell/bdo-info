package com.gpowell.bdoboss.data.market

/**
 * Thin holder that picks the active [MarketSource].
 *
 * Today that is always the keyless [ArshaSource]. When the BDO Alerts API key
 * lands (phase 2), a keyed BdoAlertsSource (backed by
 * [com.gpowell.bdoboss.data.api.BdoAlertsApi]'s market endpoints) slots in here:
 * prefer it when the stored key is non-blank, fall back to arsha otherwise.
 *
 * Construct once near the activity root and share — the source's in-memory
 * cache (30-min prices / 5-min waitlist) lives for the instance's lifetime.
 */
class MarketRepository(
    private val source: MarketSource = ArshaSource(),
) : MarketSource by source
