# Screens & Layout — ntfy-web → Android

> The app shell, navigation model, and per-screen layout after the 2026-06 redesign.
> Web source: `src/components/App.jsx`, `Feed.jsx`, `Sidebar.jsx`, `SettingsPage.jsx`.

## Navigation model (important — simplified)

The app is now **two surfaces**: the **feed** (the single content view) and **settings**, plus a
**drawer** for switching topics. **There is no message detail screen.**

```
┌ App shell ───────────────────────────────────────────────┐
│ [mobile] AppBar (hamburger + title)                       │
│ [desktop] Sidebar | Feed                                  │
│                                                           │
│  Routes:                                                  │
│   /                → Feed (All notifications)             │
│   /:topic          → Feed (one topic)                     │
│   /:topic/:msgId   → Feed (deep-link; highlights, NO detail) │
│   /settings        → Settings                            │
│                                                           │
│  Drawer (mobile) / Sidebar (desktop):                    │
│   All notifications · topics · Subscribe · Settings      │
│  FAB (publish) bottom-right, always.                     │
└───────────────────────────────────────────────────────────┘
```

- **No bottom navigation.** Mobile navigation = hamburger → drawer.
- **No detail pane / detail route.** `/:topic/:msgId` exists only as a deep-link target and just
  renders the topic feed (optionally highlighting that card). Tapping a card marks it read.
- **FAB** is always present (mobile + desktop), opens the publish sheet/dialog.

> Android mapping: a single "feed" destination (with topic as an argument) + a "settings"
> destination + a navigation drawer. No bottom-nav, no detail Activity/Fragment.

## Responsive behavior (web → Android guidance)

| width | web layout | Android |
|-------|-----------|---------|
| mobile (`< md`, <768) | AppBar on top, feed full-width, drawer is a slide-in sheet | phone layout: top bar + drawer |
| tablet (`md`–`lg`) | collapsed icon-rail sidebar + feed | optional: rail on large/foldable |
| desktop (`≥ lg`, ≥1024) | full sidebar + feed (feed max width `container_feed` = 720) | n/a (phone-first) |

Phone-first Android only needs the **mobile** column: top bar, full-width feed, drawer, FAB.

---

## Feed

Web: `Feed.jsx`, rendered inside a centered container (`max-w container_feed` = 720, padding
`px-4 py-6`).

- **List of full notification cards** (see [components.md §1](components.md)).
- **Card gap:** **18 (`gap-[1.125rem]`)** between cards. _(1.5× the old 12.)_
- **Order:** newest first — by **`sequenceId` descending** (server-assigned monotonic sequence;
  this is the original ordering, intentionally **not** wall-clock time, to survive clock skew /
  reconnect reordering).
- **No sticky topic-name header.** _(Removed — the active topic shows in the app-bar title / drawer.)_
- **Pagination:** client-side, 20 per page, infinite-scroll appends.
- **New-arrival animation:** when a genuinely new message arrives, **only that card** plays a
  slide-in-from-top (`animate_slide_in_top`, 0.25s). Not the whole list, not "whatever is on top".
  (Drive this off the set of newly-arrived ids, not the row index.) Respect reduced-motion.
- **Live region:** an a11y announcement ("new notifications") fires on arrival.
- **All-notifications feed** additionally shows each card's **topic chip** (per-topic feed omits it,
  since every card is that topic).
- **Optimistic sends** appear at the top with a sending indicator / retry-on-failure.

States: loading → skeleton cards; empty → first-run / no-messages panel (Korean voice).

---

## Drawer / Sidebar

See [components.md §6](components.md). Same content on mobile drawer and desktop sidebar:
All notifications · subscription rows (message icon, active bar, unread count, muted indicator,
⋯ menu) · Subscribe to topic · Settings.

---

## Settings

Web: `SettingsPage.jsx`. Sectioned form (left section nav + right content on wide; stacked on
mobile). Sections: **General · Server & Auth · Appearance · Notifications · Retention**. Each =
heading + hint + divider, then label/control rows (select / switch / segmented tabs).
- **Appearance → theme:** segmented **Light · Dark · System** (dark is the hero default).
- **General:** language select.
- **Server & Auth:** service URL + username/token (conditional on login config).

(Settings was not changed in the 2026-06 redesign beyond the shared token/shell updates.)

---

## Themes

Two themes, **dark is the hero/default**. All colors come from tokens
([design-tokens.md](design-tokens.md)); dark values are in `.dark`, light in `:root`/`@theme`.
Build both; design dark-first. Backgrounds are never pure black/white
(dark `#0C0D0F`, light `#F3F4F6`).
