# BDO Info

An unofficial Android companion app for **Black Desert Online (NA)** — world boss
timers, configurable spawn notifications, drop tables, and live server data.

> Not affiliated with Pearl Abyss or Black Desert Online.

## Features

- **Boss timers** — every upcoming world boss spawn with live countdowns, rendered
  in your local timezone (DST-safe)
- **Notifications that actually fire** — exact alarms, fully offline, surviving
  reboots and app updates. Per-boss toggles, multiple lead times (5/10/15/30/60 min),
  and per-day quiet-hour rules with midnight-crossing windows
- **7-day schedule** — the full weekly spawn grid, next spawn highlighted
- **Drop tables** — real item icons and community-estimated drop rates for all
  13 bosses, honestly labeled (Pearl Abyss doesn't publish official rates)
- **Live layer** — boss countdowns, daily/weekly reset timers, and Golden Pig Cave
  status via the [BDO Alerts](https://bdoalerts.net) public WebSocket
- **Over-the-air schedule updates** — when boss times change, the app picks up the
  new schedule from this repo without needing an app update

## Tech

Kotlin · Jetpack Compose (Material 3) · AlarmManager exact alarms · DataStore ·
OkHttp WebSocket · kotlinx-serialization · minSdk 26

Notifications never depend on the network: the spawn schedule is bundled
(`app/src/main/assets/schedule_na.json`) and alarms are computed on-device. The
WebSocket only powers the live UI while the app is open.

## Building

Requires JDK 17+ and the Android SDK.

```
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (needs keystore.properties, not in repo)
./gradlew :app:testDebugUnitTest # unit tests
```

## Updating the boss schedule

Edit `app/src/main/assets/schedule_na.json`, bump its `version` field, and push.
Installed apps fetch the new schedule on next launch.

## Credits

- Live data: [BDO Alerts](https://bdoalerts.net)
- Item icons: [BDO Codex](https://bdocodex.com)
- Boss schedule reference: [mmotimer](https://mmotimer.com/bdo/)

## Privacy

The app collects nothing. [Privacy policy](https://grantdpowell.github.io/bdo-info/privacy.html)
