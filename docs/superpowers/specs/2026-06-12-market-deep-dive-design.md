# Market Deep-Dive — v0.9 Design

Status: **approved for autonomous build** (Grant: "do the market deep dive and report
back when its done with 0.9"). Build target: versionCode 4 / versionName "0.9".

## Goal

Make the in-app Central Market feel like the real one: browse by the game's category
tree, see in-depth per-item stats, richer charting, and a watchlist with sorting and
optional price targets. All keyless via arsha.io (no API key, honors the app's
no-background-poll principle).

## What arsha gives us (probed live 2026-06-12/13)

- `GET /v2/{region}/item?id=csv` → `name,id,sid,minEnhance,maxEnhance,basePrice,`
  `currentStock,totalTrades,priceMin,priceMax,lastSoldPrice,lastSoldTime`. The current
  `MarketPrice` **drops** totalTrades/priceMin/priceMax/min/maxEnhance — free stats.
- `GET /v2/{region}/GetWorldMarketList?mainCategory=M&subCategory=S` → rows of
  `{name,id,currentStock,totalTrades,basePrice,mainCategory,subCategory}`. This is the
  category-browse backbone. (subCategory defaults to 1 when omitted.)
- Category code→name tree is NOT served by arsha. Bundled as an asset from the canonical
  `andreivreja/veliainn-market-resources/data/categories.json` (verified against live
  arsha: main 1=Main Weapon, 15=Armor, 20=Accessory, 25=Material…). 17 mains, ~12KB.
- arsha intermittently 500s (Imperva) — every new call degrades silently like the rest.

## Scope

### Data layer
1. **Enrich `MarketPrice`**: add `totalTrades, priceMin, priceMax, minEnhance, maxEnhance`
   (Long/Int, default 0, appended so positional construction is unaffected). Update
   `parseMarketPrices` + `parseSearchPrices` (search row carries totalTrades).
2. **`MarketListing`** DTO + `MarketSource.categoryList(main, sub)` backed by
   `GetWorldMarketList`, parser `parseMarketListings`, 30-min cache keyed `cat:M:S`.
3. **`MarketCategories`**: bundle `assets/market_categories.json`; pure `parseCategories`
   + `MarketCategoryRepository` (lazy asset load, like `ItemIndexRepository`).
4. **Price targets**: add `targetPrice: Long = 0` to `Favorite` (safe — Json omits
   defaults, round-trip test holds). `FavoritesRepository.setTarget(itemId, target)`.

### UI (all inside the existing self-navigating `MarketScreen`)
5. **Browse mode**: main view gains a "BROWSE BY CATEGORY" grid of the 17 mains →
   subcategory list → category item list (`GetWorldMarketList`, sortable by
   price/trades/name) → item detail. Nav via a small sealed `MarketNav` state.
6. **In-depth stats** on `ItemDetailScreen`: a STATS panel above the enhancement table —
   base price, stock, total trades (lifetime volume), price floor/ceiling (min/max),
   last sold + relative time, and base-vs-lastSold spread %.
7. **Charting upgrades**: 7d/30d/90d range toggle (filter the 90-day series), min/max
   guide lines + last-price label, and an enhancement-level selector (re-fetch
   `history(itemId, sid)`) when the item has multiple sids.
8. **Watchlist**: sort control (name/price/trades); show total trades on rows; a 🎯
   target affordance (set/clear target silver) with an at-a-glance "hit" indicator when
   live base price ≤ target. Foreground-only; no background polling, no notifications
   (deferred — would need the alarm engine and conflicts with the no-poll principle).

## Non-goals (v0.9)
- Background price alerts / push notifications (future; needs alarm-engine integration).
- The keyed BDO Alerts market source (still pending the API key).
- Per-category live price prefetch (lists show arsha's list-level basePrice; live detail
  on tap) — keeps request volume sane.

## Testing
- `MarketCategoriesTest`: parse → 17 mains, names, sub counts/names.
- `ArshaParsingTest`: update enriched `MarketPrice` expectations (fixtures already carry
  the fields); add `parseMarketListings` case (+`marketlist.json` fixture).
- Existing 51 tests stay green.

## Risk / mitigation
- Big UI diff, build+smoke verification only (no visual test rig). Mitigation: keep new
  composables small and isolated; lean on existing patterns (GradeMonogram, section
  headers, ApiResult degradation); pure logic unit-tested.
- arsha flakiness: all new fetches return ApiResult and degrade to a "temporarily
  unavailable" line, never a crash.
