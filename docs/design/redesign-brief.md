# BDO Info — Visual Redesign Brief (hand this whole document to a design-focused Claude)

> Paste everything below the line into a fresh Claude session. It is written *to that
> Claude*. It contains the full feature/screen inventory, the constraints, and — critically
> — the exact deliverable format so the output can be implemented directly into the real
> Jetpack Compose app by an engineer (me) without guesswork.

---

## 0. Your role and mission

You are a senior product designer + motion designer. Your job is to **redesign the visual
language, layout, and motion of an existing Android app called “BDO Info”** so it becomes
**gorgeous, unique, modern, minimal, and deeply intuitive** — without removing any features.

You are **not** writing the app. You are producing a **single, implementable design
specification** that an engineer will translate into Jetpack Compose + Material 3. That means
your output must be **concrete and buildable**: real hex colors, real `dp` spacing, real type
sizes/weights, real animation durations (`ms`) and easing curves, and per-screen layouts —
**never** vague mood words alone. Every adjective must be backed by a value.

Think of yourself as delivering a **design system + screen-by-screen spec**, the kind a good
engineer can pick up and build in order.

---

## 1. What the app is, and its soul

**BDO Info** is an unofficial Android companion app for **Black Desert Online (NA)** — a
dark-fantasy MMORPG. It does three things for players:

1. **World-boss timers & alarms** — never miss a boss spawn (this is time-critical, glanceable).
2. **Central Market** — browse/track item prices, history, and trends like an in-game trader.
3. **A hub** — an in-app browser for BDO community sites, plus reference data.

**The soul / north star:** *a jeweler’s display case for a dark-fantasy MMO.* Premium,
obsidian-and-gold, calm and luxurious, but **fast and glanceable** — a player checks it
between fights. It is data-dense yet must feel **uncluttered and serene**, never busy.
Modern and clean, with restrained, tasteful motion that makes it feel alive and high-end.

It is a **fan app** — evoke BDO’s premium dark-fantasy mood, but do **not** copy Pearl
Abyss’s logos/branding or impersonate the official game UI. Make something that stands on
its own.

**Current identity to build from:** dark, near-black backgrounds with a warm **gold** accent
(`BdoGold`, roughly `#C9A227`), subtle vertical gradients (`#0B0A08 → #14110C`). You may
refine/evolve this palette, but keep the **dark + premium gold-accented** soul as the
starting point. Propose any evolution explicitly with before/after reasoning.

---

## 2. Hard constraints (do not violate — they make it buildable)

- **Platform:** Native Android, **Jetpack Compose + Material 3 (M3)**, Kotlin. Everything you
  design must be expressible in Compose/M3 (or clearly flagged as a custom `Canvas`/
  `drawBehind` element).
- **Dark theme is primary.** The app is dark. A light theme is optional/secondary — focus on
  dark.
- **minSdk 26, targetSdk 35.** No features requiring newer-only APIs without a fallback.
- **Performance:** per-frame animation values must be cheap; specify animations that can run
  in the draw phase (`graphicsLayer`/`drawBehind`) rather than triggering 60fps recomposition.
  Avoid designs that demand heavy overdraw or huge bitmaps.
- **Glanceability first** on the Bosses tab — countdowns must be readable at arm’s length in
  under a second.
- **No heavy asset pipeline on the dev machine** (no ImageMagick/ffmpeg). Prefer vector/Compose-
  drawn visuals, gradients, and shapes over shipped raster art. Item icons load remotely.
- Keep it **accessible**: ≥48dp touch targets, sufficient contrast on dark, support large font
  scales gracefully.

---

## 3. Full screen & feature inventory (design ALL of this)

The app has a **5-tab bottom navigation**, a top app bar with a **⚙ settings** entry, and
several detail/overlay surfaces. Redesign every one, including their **loading / empty /
error** states.

### Global chrome
- **Top app bar:** app title “BDO INFO” + ⚙ settings (gear) on the right.
- **Bottom navigation (5 tabs):** Bosses · Market · Events · Profile · Hub.
- **Theme:** dark, gold-accented, premium.

### TAB 1 — Bosses (has 3 sub-tabs: Timers / Schedule / Alerts)
- **Live header** (top of the tab): a “LIVE” pulse indicator, **Daily reset** countdown,
  **Weekly reset** countdown, and **Pig Cave** status (OPEN/CLOSED). Should feel like a
  living status bar.
- **Timers sub-tab:** a vertical list of **upcoming boss spawns**. Each row/card shows:
  - one or more **boss portraits** (some spawns are *co-spawns* — 2+ bosses at once, e.g.
    “Karanda + Golden Pig King”),
  - boss name(s), the spawn day/time (e.g. “Sat 1:15 AM”),
  - a prominent **countdown** (e.g. “2h 40m”, or “0m 44s” when imminent),
  - a **NEXT** chip on the soonest spawn.
  - The imminent/next spawn should feel special (subtle emphasis/animation). 13 bosses exist
    (Kzarka, Kutum, Nouver, Karanda, Garmoth, Offin, Vell, Uturi, Sangoon, Bulgasal, Quint,
    Muraka, Golden Pig King).
- **Schedule sub-tab:** the fixed **weekly schedule grid** of boss spawn times (a week ×
  time-slots matrix). Highlight the current/next slot with a gentle “breathe” animation.
- **Alerts sub-tab:** notification settings — per-boss **lead times** (how early to alert),
  **quiet hours/rules**, and the Android **exact-alarm permission** grant flow. Make settings
  feel calm and clear, not like a form dump.
- **Boss detail sheet** (opens when a spawn is tapped — currently a bottom sheet): the boss’s
  portrait, lore/mechanics blurb, and its **drop table** — a list of item drops each with an
  **item icon**, name, rarity, a **drop-confidence** tag (`official` / `community-estimated` /
  `unknown`), and a **live market price chip**. Plus a link to the item’s codex page.

### TAB 2 — Market (the richest area — design it to feel like a premium trading terminal)
- **Market home:**
  - **Watchlist** section: the user’s saved items, each row showing a real **item icon**,
    name, live **price · stock · trade volume**, and an optional **🎯 buy-price target** with a
    “HIT” state when the live price drops to/below target. A **sort control** (Recent / Name /
    Price / Volume).
  - **Search** field: searches a bundled item index; results are rows with icon + name +
    rarity dot.
  - **Browse by Category** grid: the game’s **17 top categories** (Main Weapon, Sub Weapon,
    Awakening Weapon, Armor, Accessory, Material, Enhancement, Consumables, Life Tools, Alchemy
    Stone, Magic Crystal, Pearl Items, Dye, Mount, Ship, Wagon, Furniture).
- **Category drill-down:** tap a main category → **subcategory list** → **sortable item list**
  (rows: icon, name, stock, volume, price; sort by Price/Volume/Name).
- **Item detail screen:**
  - Header: item **icon**, name, ⭐ **watchlist** toggle.
  - **In-depth stats panel:** base price, in-stock count, **total trades (lifetime volume)**,
    **price floor–ceiling** range, last-sold price + “x ago”, and **last-vs-base spread %**
    (green up / red down).
  - **Enhancement price table:** rows per enhancement level (Base, PRI, DUO, TRI, TET, PEN, or
    +1…+20), each with price / stock / last-sold.
  - **Price history chart:** a 90-day line chart with **range chips (7D / 30D / 90D)**, an
    **enhancement-level selector**, min/max guide lines, a gradient fill, and a “now” marker.
    (90 days is a hard data cap — don’t imply more.) This chart is currently a custom `Canvas`;
    redesign it to be elegant and information-rich but still `Canvas`-drawable.
  - A “View on BDO Codex” link.
- **Item icons:** real game icons load remotely (may be slow/missing). Design a beautiful
  **fallback** for missing icons: currently a grade-colored **monogram tile** (first letter in
  a rarity-tinted rounded square). Rarity/grade colors: 0 white-gray, 1 green, 2 blue, 3 gold,
  4 orange-red, 5 crimson. Specify how icons + fallback + rarity reads as a system.

### TAB 3 — Events  *(currently a LOCKED placeholder)*
- Locked until the user enters a community API key in settings. Design both the **locked
  state** (an inviting “unlock with API key” screen, not a dead end) and a **forward-looking
  unlocked layout** for news/coupons/maintenance feeds (cards: title, date, body, tags).

### TAB 4 — Profile  *(currently a LOCKED placeholder)*
- Also locked behind the API key. Design the **locked state** and a plausible **unlocked
  player-profile** layout (family/character info, pinned items/pages) for later.

### TAB 5 — Hub (in-app browser)
- **Hub home:** **site tiles** for community sites (BDO Codex, BDO Alerts, Garmoth) and a list
  of ⭐ **bookmarks**. Make the tiles feel like a curated launcher.
- **In-app browser:** a WebView with a slim toolbar (back, page title, ⭐ bookmark, refresh,
  open-in-Chrome), a thin top **progress bar**, ad-blocking, and an **OAuth login popup
  overlay** (a full-screen “Sign in” sheet for “Log in with Discord” flows). Design the
  toolbar, progress, and the sign-in overlay.

### Settings (⚙ overlay)
- **API key** entry (masked field with show/hide, Save, Clear-with-confirm) — gates Events/Profile.
- **About** card: app version + build number, Privacy Policy + GitHub links.
- **Data Sources panel:** a list of every API/data source the app uses, each as a card with:
  the source name, **what it does** (one line), a **credit/attribution** line, and a **live
  status pill** — **Online** (green) / **Offline** (red) / **Bundled** (gold) — checked when
  the screen opens. (E.g. arsha.io = primary market data; BlackDesertMarket.com = fallback;
  BDO Codex = icons; BDO Alerts = live timers; plus bundled sources.) Make these cards feel
  trustworthy and clean. Tapping a card opens the source’s site.

### Notifications (system)
- Boss-spawn alarms post **system notifications**: small icon must be a **monochrome bell**;
  the boss portrait goes in the large-icon slot. Spec the notification style/content.

---

## 4. Current design system (your starting point — improve on it)

- **Color:** dark near-black backgrounds + warm gold accent (`#C9A227`-ish). Up = green
  (`#6FBF5A`), down = red (`#E0593F`). Rarity tints as listed above.
- **Type:** Material 3 default type scale (no custom font yet — you may propose one, but it
  must be a freely-licensed, bundle-able font; specify it).
- **Shape/components:** M3 Cards, `FilterChip`, `OutlinedTextField`, bottom nav, bottom
  sheets, custom `Canvas` charts, rounded monogram tiles, status pills.
- **Motion today:** minimal — some entrance animations (which currently, as a *bug*, replay on
  every tab switch), and a “breathe” highlight on the schedule. Treat motion as a major
  opportunity.

---

## 5. Your design goals (the brief in one place)

Make it **gorgeous, unique, simplistic/minimal, intuitive, modern, and alive**:
- **Minimal & calm:** generous spacing, clear hierarchy, restrained color, let the gold and
  data breathe. Remove visual noise.
- **Unique & premium:** a distinctive, ownable look — not a generic Material template. Find a
  signature motif (e.g. how the gold is used, a card treatment, a chart style, a status glow).
- **Intuitive:** obvious navigation and affordances; the most important info (next boss, an
  item’s price + trend) is the loudest thing on screen.
- **Modern motion:** a coherent **motion system** — meaningful, fast, tasteful transitions and
  micro-interactions (screen enters, list item reveals, chart draw-on, press/ripple feedback,
  the “LIVE”/countdown pulse, status-pill states, target-HIT celebration). Specify durations
  and easing. Motion should clarify, never delay.
- **Beautiful empty/loading/error states** — especially the **“arsha is down / temporarily
  unavailable”** market state, which happens often. Make degraded states feel intentional and
  calm (e.g. graceful skeletons, a tasteful offline treatment), not broken.

---

## 6. Preserve vs. reinvent

- **Preserve:** the **feature set**, the **5-tab information architecture**, the dark+premium+
  gold **soul**, and the **glanceability** of boss timers. Don’t delete features or data.
- **Free to reinvent:** the entire **visual execution** — palette refinement, typography,
  spacing, component styling, layouts within each screen, iconography, chart styling, and the
  whole **motion** layer. Surprise me, tastefully.

---

## 7. REQUIRED deliverable (structure your output exactly like this)

Produce **one cohesive Markdown design spec** with these sections, in this order. Use
**concrete values everywhere**. Where a visual is hard to convey in words, include a small
**ASCII wireframe** of the layout.

1. **Design language / north star** — 3–5 sentences naming the concept + the signature motif,
   and the 3–4 principles you’ll hold to.
2. **Design tokens** (the foundation — be exhaustive and literal):
   - **Color:** every semantic token with hex (backgrounds, surfaces/elevations, primary/gold,
     on-colors, success/up, danger/down/offline, the 6 rarity tints, status pill colors,
     dividers, scrims, gradients). Dark theme required; note light if you do one.
   - **Typography:** the type scale (display/title/body/label sizes in sp, weights, line
     heights, letter-spacing) and where each is used. Name the font (bundle-able/licensed).
   - **Spacing & sizing:** the spacing scale (dp), touch-target minimums, content widths.
   - **Shape & elevation:** corner radii per component, border/stroke usage, shadow/elevation
     or glow treatment on dark.
   - **Iconography:** style (line vs filled, weight), and the item-icon + monogram-fallback +
     rarity system.
3. **Motion system** — named transitions and micro-interactions, each with **duration (ms) +
   easing curve** (use standard cubic-bezier or M3 easings), and which property animates.
   Include: screen transitions, tab switches, list-item entrance, chart draw-on, press states,
   the LIVE/countdown pulse, status-pill change, target-HIT moment, sheet open/close.
4. **Component library** — spec each reusable component (with an ASCII sketch + token
   references): app bar, bottom nav (incl. selected state), sub-tab selector, **boss spawn
   card** (single + co-spawn + NEXT/imminent variants), **live header**, **schedule grid
   cell**, **watchlist row** (incl. target/HIT), **search row**, **category tile**, **listing
   row**, **stats panel**, **enhancement table row**, **price chart**, **item icon / monogram
   tile**, **filter/sort chip**, **status pill**, **data-source card**, **text field**,
   **buttons**, **bottom sheet**, **locked-tab state**, **skeleton/loading**, **empty state**,
   **error/offline state**.
5. **Screen-by-screen** — for **every screen/state in §3**, give: layout (ASCII wireframe),
   visual hierarchy, the tokens/components used, the states (default/loading/empty/error), and
   the key interactions/animations. Order them so an engineer can build top-to-bottom.
6. **States & edge cases** — the offline/“market unavailable” treatment, slow/missing item
   icons, very long item names, large font scale, co-spawns with 3+ bosses, empty watchlist.
7. **Accessibility & ergonomics** — contrast notes, touch targets, one-handed reach, reduced-
   motion behavior.
8. **Implementation notes for the Compose engineer** — call out what maps to M3 vs. custom
   `Canvas`/`drawBehind`, any custom theme work (a `BdoTheme` with these tokens), performance-
   sensitive animations (draw-phase only), and a **suggested build order** (which screens/
   components first). Flag anything that needs a new dependency or asset.

**Output rules:** one self-contained Markdown document; concrete values over adjectives; ASCII
wireframes for layouts; no hand-wavy “make it pop.” If you must assume something, state the
assumption and proceed. Keep it implementable — every spec should answer “what exact value
does the engineer type in?”

---

## 8. Quality bar

Aim for something that would feel at home next to the best modern finance/markets apps and
premium game companions: calm, confident, tactile, and unmistakably *itself*. When in doubt:
**simpler, more spacing, fewer colors, more intention.** Make the gold count.
