package com.gpowell.bdoboss.data.market

/**
 * Thin holder that picks the active [MarketSource].
 *
 * Default is keyless arsha with an automatic blackdesertmarket fallback
 * ([FallbackMarketSource]) — arsha's upstream Imperva blocks can last minutes,
 * and the mirror keeps prices/categories loading through them. When the BDO
 * Alerts API key lands (phase 2), a keyed BdoAlertsSource (backed by
 * [com.gpowell.bdoboss.data.api.BdoAlertsApi]'s market endpoints) slots in as the
 * primary here.
 *
 * Construct once near the activity root and share — each source's in-memory
 * cache (30-min prices / 5-min waitlist) lives for the instance's lifetime.
 */
class MarketRepository(
    private val source: MarketSource = FallbackMarketSource(
        primary = ArshaSource(),
        secondary = BlackDesertMarketSource(),
    ),
) : MarketSource by source
