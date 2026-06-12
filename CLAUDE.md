# CLAUDE.md — BDO Info

Unofficial Android companion app for Black Desert Online NA: world boss timers,
exact-alarm notifications, drop tables, live server data. Built 2026-06-12.

## Identity (DO NOT MIX UP)

- **applicationId:** `org.okimasha.bdoinfo` (PERMANENT — registered on Play Console;
  OkimaSha is Grant's gaming/Discord handle)
- **Kotlin namespace:** `com.gpowell.bdoboss` (intentionally different; only the
  applicationId was rebranded — don't "fix" this)
- **App display name:** "BDO Info" (`strings.xml` app_name)
- **GitHub:** `github.com/GrantDPowell/bdo-info` — personal account. The remote
  URL embeds the username (`https://GrantDPowell@github.com/...`) so the stored
  credentials stay scoped to this account; never strip the username from the
  remote URL. Local git config is pinned to
  `grantdpowell <grantdpowell@users.noreply.github.com>` — keep it that way.

## Build (Windows)

- `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` before EVERY
  gradle command — system java is 1.8 and will fail. SDK: `%LOCALAPPDATA%\Android\Sdk`.
- `./gradlew :app:testDebugUnitTest assembleDebug --no-daemon` — tests (45+, all
  must pass) + debug APK
- `./gradlew assembleRelease bundleRelease --no-daemon` — release APK/AAB, signed via
  the untracked `keystore.properties` → `keystore/bdoinfo-release.jks` (BOTH
  gitignored, never committed; if they're lost the app identity is unrecoverable —
  keep off-machine backups)
- Install: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/release/app-release.apk`
- **Bump `versionCode` for every new Play upload** (currently 1, versionName "0.6")
- Image tooling on this machine: NO magick/ffmpeg/python — use PowerShell +
  .NET System.Drawing (System.Drawing enum args must use full
  `[System.Drawing.Drawing2D...]` types, string casts fail in inline -Command)

## Architecture (3 layers — keep this separation)

1. **Local schedule engine** (the backbone — notifications NEVER touch network):
   `assets/schedule_na.json` → `ScheduleRepository` (bundled vs version-gated
   override) → `SpawnCalculator` (DST-safe, 16-day window, merges co-spawns per
   instant) → `ReminderExpander` (settings × spawns → triggers; quiet-rule
   suppression) → `AlarmScheduler.rearm()` (exact alarms, 48h window + a 24h
   self-refresh alarm so sparse configs never starve; codes persisted in
   filesDir/armed_codes.txt for cancellation) → `AlarmReceiver`/`BootReceiver`.
   Rearm triggers: app open, every settings change, boot, time/tz change, app
   update (MY_PACKAGE_REPLACED), every alarm fire, OTA schedule update.
2. **Live layer** (foreground only): `BossAlertsSocket` (OkHttp WS) →
   `LiveState` StateFlow → LiveHeader. Connect onStart, disconnect onStop.
3. **Feed layer** (PHASE 2, not built): BDO Alerts REST needs `X-API-Key`;
   DataStore slot `bdoalerts_api_key` already reserved in SettingsRepository.

## BDO domain knowledge

- **13 NA world bosses:** Kzarka, Kutum, Nouver, Karanda, Garmoth, Offin, Vell,
  Uturi, Sangoon, Bulgasal, Quint, Muraka, Golden Pig King. Uturi/Sangoon/
  Bulgasal/GPK are the newer "Morning Light" bosses (2023+).
- **Schedule:** fixed weekly grid, 68 slots, stored as wall-clock times in
  `America/Los_Angeles` (PA publishes in PST/PDT). Verified cell-by-cell against
  mmotimer.com. Quirks: Thursday has no 03:00 slot, Saturday no 21:15; Sunday
  evening spawn is 17:15 (not 17:00 — mmotimer shows a display duplicate);
  Vell spawns exactly 2×/week (Wed 17:00, Sun 14:00); Garmoth daily at 12:00;
  Quint+Muraka pair on Tue/Thu 20:15. Co-spawns = multiple bosses in one slot.
- **OTA schedule updates are LIVE:** `MainActivity.SCHEDULE_URL` points at this
  repo's raw `schedule_na.json`. To fix boss times after a PA patch: edit the
  asset on GitHub, **bump its `version` field**, push — installs pick it up on
  next launch (override only applies when remote version > bundled version).
- **Drop data** (`assets/boss_info.json`): community-estimated, compiled from
  ~24 sources (GrumpyG, fandom wiki, patch notes, forums). Confidence enum:
  `official` (only for mechanics like Vell bundles/Garmoth Bloodstone pity, never
  for percentages) | `community-estimated` | `unknown`. Notable: Kzarka box
  ~0.3–4% tiered by damage contribution; Garmoth's/Vell's Heart ~0.01% (order of
  magnitude only); Muraka Dim Ogre Ring ~1%; Morning Light bosses have almost no
  published rates (mostly `unknown`). Don't invent percentages.
- **Boss portraits** (`drawable-nodpi/boss_*.png`): from mmotimer.com
  (`https://mmotimer.com/img/<name>_big.png`). **Item icons**
  (`assets/item_icons/*.png`, 30 of 49 items): from bdocodex — their autocomplete
  endpoint is `https://bdocodex.com/ac.php?l=us&term=<name>` (query.php is a dead
  end), icons are `.webp` only (the `.png` URLs serve a placeholder); converted to
  PNG. 19 items skipped = bundles/pools/combos with no single in-game icon → gold
  fallback tile in UI. "Kzarka's Latent Aura" is "Latent Boss Aura" on bdocodex.

## BDO Alerts (bdoalerts.net — community service by LoadingMagic)

- **WebSocket (keyless, in production use):** `wss://api.bdoalerts.net/ws?region=na`.
  Messages: `connection_ack` (carries `cached_data` for all channels),
  `boss_timers` (30s; `data.bosses[].{boss_name,next_spawn,time_until}` —
  `next_spawn` is ISO-8601 WITH UTC offset like `-07:00`), `reset_timers` (30s;
  `data.daily_reset.countdown` / `data.weekly_reset.countdown` strings),
  `cave_status` (60s; `data.status` = "OPEN"/"CLOSED"), `heartbeat`. Client sends
  `{"type":"ping"}` every 25s. Their feed includes bosses not in our schedule
  (e.g. "Black Wings") — drift logging skips unknown names.
- **REST API (phase 2):** needs `X-API-Key` header; endpoints of interest:
  `/api/news`, `/api/coupons`, `/api/maintenance-status`. OpenAPI at
  `https://api.bdoalerts.net/openapi.json`. **API key application submitted
  2026-06-12** (Project "BDO Info", Discord: OkimaSha, email
  grantpowell911@gmail.com, tier Medium 100-1000/day, promised: client-side
  caching ≤1 fetch/hour/device, no background polling). When the key arrives:
  build the News tab; the key gets pasted into app settings (DataStore), NEVER
  committed to this public repo. Support: info@bdoalerts.net.

## Hard-won Android gotchas (don't regress these)

- `checkSelfPermission(POST_NOTIFICATIONS)` returns DENIED on API < 33 — always
  gate with `Build.VERSION.SDK_INT >= 33` or notifications silently die on
  Android 8–12.
- Notification small icons MUST be monochrome (the bell); the boss portrait goes
  in `setLargeIcon`.
- Transparent Scaffold (`containerColor = Transparent`) loses its content color →
  bare Text renders black-on-black. Fix: explicit `contentColor = onBackground`.
- M3 `Surface` (non-clickable overload) blocks touch input — use Box for
  decorative overlays on clickable cards (NEXT chip bug).
- Per-frame animation values must be read in draw phase (`drawBehind`/
  `graphicsLayer` lambdas), not passed as composable params — else 60fps
  recomposition (Schedule "breathe" highlight bug).
- BroadcastReceiver coroutines: wrap work in `runCatching` (uncaught = process
  crash at boot) and always `goAsync()`/`finish()`.
- Quiet rules: midnight-crossing windows attribute to the START day (Sunday
  23:00→08:00 covers Monday 02:30). Settings saves wrap in
  `withContext(NonCancellable)` so tab-switching can't cancel between
  DataStore write and alarm rearm.
- Unit tests read `src/main/assets` via classloader (test resources srcDir wired
  in app/build.gradle.kts) — never via relative File paths.

## Play Store status (as of 2026-06-12)

- App created in Play Console, package registered with RELEASE key fingerprint
  (SHA-256 `99:69:4D:...:2D`). Listing assets: icon =
  `research/launcher/launcher_source.png` (512×512), feature graphic generated by
  `feature_graphic.ps1` (regenerate via PowerShell System.Drawing). Listing copy
  in `store-listing.md`. Privacy policy: `docs/privacy.html` via GitHub Pages →
  `https://grantdpowell.github.io/bdo-info/privacy.html`.
- v0.6 AAB submitted to the **closed testing** track for Google review.
- **Path to production:** closed test needs ≥12 opted-in testers for ≥14
  continuous days, then Grant applies for production access (Google asks
  questions about the test).
- **Known review risks:** (1) `USE_EXACT_ALARM` is policy-restricted to
  alarm/calendar apps — if flagged, drop it from the manifest (the app already
  falls back to `SCHEDULE_EXACT_ALARM` + in-app grant flow); (2) the launcher
  icon is Pearl Abyss's Black Desert Mobile flame emblem — fine for testing, but
  production review may flag impersonation; a custom gold-on-dark icon is the
  fallback plan; (3) listing must keep "unofficial fan app" language.

## Open items

1. **Closed test running** — needs 12 testers × 14 days before production
   application.
2. **BDO Alerts API key pending** → build News/Events/Coupons tab (phase 2);
   key goes in app settings, never in the repo.
3. **Keystore backup** — `keystore/bdoinfo-release.jks` + password in
   `keystore.properties` must be backed up off-machine (password manager).
4. Tablet screenshots skipped (optional) — emulator capture is the plan if ever
   needed.
5. Minor polish backlog: normalize boss portrait sizes (mix of 90px and 144px),
   compress `ic_launcher_foreground.png` (~449KB), entrance animations replay on
   every tab switch (could be first-visit-only), schedule screen goes stale past
   midnight if app stays open.
