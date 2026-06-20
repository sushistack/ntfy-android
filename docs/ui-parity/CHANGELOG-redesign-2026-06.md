# Redesign Delta — 2026-06

Everything that changed in the ntfy-web client during the 2026-06 UI/UX pass, relative to the
previous version of these parity docs. Each item notes the web file(s) so Android can cross-check.

## Removed (delete the Android equivalents)

- **Message detail view** — the detail pane (desktop right column) and the mobile full-screen
  detail, plus the `/:topic/:msgId` detail route. The **card is now the full presentation**;
  tapping a card only marks it read. (`App.jsx`, deleted `DetailPane.jsx`.)
- **Bottom navigation bar** (Subscriptions / All / Settings) — navigation is the hamburger drawer,
  which already has All / topics / Subscribe / Settings. (`App.jsx`, deleted `BottomNav.jsx`.)
- **Per-card mute bell** and the card **"⋯" overflow menu** — replaced by a single **X delete**
  button in the card header. (`NotificationCard.jsx`)
- **Sidebar standalone mute toggle button** — moved into the row's **⋯ menu** as Mute/Unmute.
  (`Sidebar.jsx`)
- **Card compact/preview mode** and **"Show more" / "+N more"** on card bodies — cards render full.
  (`CardBody.jsx`, `StructuredCard.jsx`)
- **Sticky per-topic feed header** (topic-name banner above the list). (`Feed.jsx`)
- **Rounded-right card corner** — cards are now fully squared. (`tokens.css` `rounded-card`)

## Added

- **Structured message format** — tag `card` + JSON body → key-value / list / chart / sections.
  Full spec in [message-format.md](message-format.md). (`StructuredCard.jsx`, `CardBody.jsx`)
  - `kv` rows: leading icon, status dot/colors, inline meter bar, optional `columns:2`
    (2-col on ≥sm, 1-col on mobile; meter stays inside its cell).
  - `chart`: dependency-free SVG bar/line, 120 tall, labels when ≤12 points, ≤60 points.
  - `sections`: ordered mix of markdown/kv/list/chart blocks.
  - Heuristic fallback: untagged `key: value` lines still render as a kv table.
- **Categorized tag row** (`CardTags.jsx`) — topic (emerald, fixed) · `service:` (slate-blue,
  fixed) · general (hash color, max 2 + "+N more").

## Changed

- **Priority accent bar shows for all priorities** with colors: min/low gray (`muted`), normal
  white (`text`), high amber (`priority_high`), max coral (`priority_max`); glow on high/max in
  dark. (Was P4/P5 only.) (`NotificationCard.jsx`)
- **Priority badge shows on every card**: `Min`/`Low`/`Normal`/`High`/`Urgent` (low=gray text,
  normal=white text, high=amber fill, urgent=coral fill). (Was High/Urgent only.)
  (`PriorityBadge.jsx`, new i18n keys `notification_card_badge_min|low|normal`.)
- **Publish dialog priority chips**: all four (Low/Normal/High/Urgent) now show a selected tint
  (Low gray, Normal white, High amber, Urgent coral). (Was: Low/Normal had no selected style.)
  (`PublishDialog.jsx`)
- **Timestamp format** → `YYYY-MM-DD HH:mm:ss` local, locale-independent; **always right-aligned**
  even on tag-less cards. (`utils.js formatShortDateTime`, `NotificationCard.jsx`)
- **Feed card gap** 12 → **18** (1.5×). (`Feed.jsx`)
- **New-arrival animation** now plays only on the **actually-arrived** card(s), not on whatever
  sits at list index 0. (`Feed.jsx` — driven by the arrived-ids set.)
- **Feed ordering** stays **sequenceId-descending** (newest first by server sequence, not
  wall-clock — robust to clock skew/reconnect). _(Briefly trialed time-sort, then reverted.)_
- **Sidebar topic left icon** is now a **message/chat icon** (was a bell); muted topics show a
  passive **bell-off indicator** next to the ⋯ menu, and Mute/Unmute is inside that menu.
  (`Sidebar.jsx`)
- **FAB mobile inset** dropped the 56px reservation that existed for the removed bottom nav — now
  `bottom-6` like desktop. (`PublishFab.jsx`)
- **Card swipe backing layers** only mount while swiping (no edge color-bleed at rest / desktop).
  (`NotificationCard.jsx`)
- **`rounded-card`** token: `0 16 16 0` → `0` (fully squared). (`tokens.css`)

## Token/value notes

- All color/type/spacing/radius **values are unchanged** except `rounded-card` (above).
- `width-detail-pane` token is now **unused** (detail removed).
- New literal (non-token) colors: the CardTags general-tag palette + service-tag color — see
  [design-tokens.md](design-tokens.md) → "Literal colors".
