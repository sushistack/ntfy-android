# ntfy Android — UI Parity Reference

This folder is the **spec + design-token reference** for building the ntfy **Android** app to
match the redesigned **ntfy-web** client. Goal: **1:1 UI/UX parity**. Use it as BMAD context when
planning and implementing UI stories.

> **Web source of truth:** the `ntfy-web` repo — `src/styles/tokens.css` and `src/components/`.
> When in doubt, the running web app + that source win over any prose here.

## ⚠️ Updated 2026-06 redesign

The app was significantly reworked. Read **[CHANGELOG-redesign-2026-06.md](CHANGELOG-redesign-2026-06.md)**
first if you built against the older version of these docs — several things were **removed**
(detail view, bottom nav, per-card mute bell) and the **structured-message format** was added.
Some `screenshots/` are **pre-redesign and partially stale** (see notes below).

## Documents (read in this order)

1. **[design-tokens.md](design-tokens.md)** — every color / type / spacing / radius / elevation
   token with its Android resource key, plus the literal tag-palette colors. Build against these,
   never raw hex/px.
2. **[components.md](components.md)** — exact anatomy of every shared component (card, priority
   badge, tags, meter, sidebar, app bar, FAB, publish sheet) and a list of what was **removed**.
3. **[message-format.md](message-format.md)** — ★ the **structured-card protocol**: how a ntfy
   message carries key-value tables / lists / charts / mixed sections (tag `card` + JSON body),
   with schemas, rendering rules, the icon map, and the fallback/heuristic path. **Android must
   parse and render this identically.**
4. **[screens-layout.md](screens-layout.md)** — app shell, navigation model (drawer, no
   detail, no bottom-nav), feed behavior (ordering, gaps, animation), settings, themes.
5. **[CHANGELOG-redesign-2026-06.md](CHANGELOG-redesign-2026-06.md)** — delta from the previous design.

---

## Design language (the gist)

- **Dark is the hero theme**, light is supported. Build both; design dark-first. Canvas is never
  pure black/white (dark `#0C0D0F`, light `#F3F4F6`).
- **The notification card is the product, and it is the *only* view of a message** — there is no
  detail screen. The card renders the **full** content (markdown / key-value / list / chart /
  mixed). Tapping a card just marks it read.
- **Card shape: fully squared** (no corner radius). A **4dp priority accent bar** runs down the
  left edge, colored for **every** priority: min/low **gray**, normal **white**, high **amber**,
  max **coral** (amber/coral get a dark-mode glow).
- **Accent = emerald green** (`#42D392` dark / `#1A9E5F` light), brand-shared with web.
- **Type:** Plus Jakarta Sans (UI), JetBrains Mono (mono/values). 4px spacing scale.
- **Priority badge** on every card (`Min`/`Low`/`Normal`/`High`/`Urgent`), squared 6dp corners.
- **Timestamps** are `YYYY-MM-DD HH:mm:ss`, always bottom-right.
- **Tags** split into topic (emerald, fixed) · service `service:` (slate-blue, fixed) · general
  (hash color, max 2 + "+N more").
- **Navigation:** hamburger → drawer (All / topics / Subscribe / Settings). **No bottom nav.**
  FAB (emerald) publishes.

---

## Screenshots (`screenshots/`)

**Captured fresh on 2026-06-21 against the post-redesign web app** (Playwright, real data, both
themes, mobile 412×915 + desktop 1366×900). These now reflect the current UI.

| file | screen |
|------|--------|
| `mobile-01-subscriptions-home.png` | All-notifications home (no bottom nav) |
| `mobile-02-feed.png` | Per-topic feed (squared cards, X-delete header, priority badges, structured cards at top) |
| `mobile-10-structured-cards.png` | Feed top showing **kv / chart / sections** structured cards + categorized tags |
| `mobile-04-drawer.png` | Navigation drawer (message icons, mute in ⋯) |
| `mobile-05-publish.png` | Publish bottom sheet (all 4 priority chips tint) |
| `mobile-06/07/08-settings-*.png` | Settings: general / appearance / server |
| `mobile-09-feed-dark.png` | Feed, dark hero theme |
| `desktop-01-shell.png`, `desktop-04-shell-dark.png` | Desktop shell (sidebar + feed, no detail pane) |
| `desktop-03-settings.png` | Desktop settings |

> The detail-view shots (`mobile-03-detail`, `desktop-02-detail-pane`) were **removed** — there is
> no detail view. To regenerate all: with the web dev server on `:3002`, run
> `SHOT_DIR=$(pwd)/../ntfy-android/docs/ui-parity/screenshots npx playwright test screenshots`
> from the ntfy-web repo.

---

## Parity checklist for Android stories

- [ ] Colors/type/spacing/radius from `design-tokens.md` resource keys, not literals (except the
      documented tag-palette/service hex).
- [ ] Notification card: **fully squared**, 4dp left priority bar colored for **all** priorities.
- [ ] Priority badge on **every** card (Min/Low/Normal/High/Urgent), 6dp corners.
- [ ] Card header: priority badge + title + unread dot + **X delete** (no bell, no ⋯).
- [ ] Card body renders **structured messages** per `message-format.md` (kv/list/chart/sections)
      + markdown + heuristic kv. **Full content, no compact/"show more".**
- [ ] Tag row: topic + `service:` (fixed colors) + general (hash color, max 2 + more).
- [ ] Timestamp `YYYY-MM-DD HH:mm:ss`, always right.
- [ ] Feed: newest-first by sequenceId, 18dp card gap, per-arrival slide-in, no sticky topic header.
- [ ] **No detail view. No bottom nav.** Tap card = mark read. Mute lives in the drawer ⋯ menu.
- [ ] Drawer: All / topics (message icon, active bar, unread count, muted indicator, ⋯ menu) /
      Subscribe / Settings. FAB publishes.
- [ ] Publish sheet: Topic/Title/Message/Priority(4 chips, all tint)/Tags; Close/Send.
- [ ] Both light + dark themes; dark is default/hero.
