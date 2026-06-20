# Component Specs — ntfy-web → Android

> Exact anatomy of every shared UI component, after the 2026-06 redesign. Build against the
> tokens in [design-tokens.md](design-tokens.md); never hard-code hex/px except the few literal
> tag-palette colors called out below (they have no token).
>
> Web sources: `src/components/message/*`, `src/components/Sidebar.jsx`, `AppBar.jsx`,
> `PublishDialog.jsx`, `PublishFab.jsx`, `src/components/ui/*`.

---

## 1. Notification Card  ★ the product

Web: `message/NotificationCard.jsx` (shell) + `message/CardBody.jsx` (body).

### Container
- Shape: **squared corners, no radius** (`rounded-card` = `border-radius:0`). _(Changed in this
  redesign — it used to be squared-left/rounded-right.)_
- `surface` background, `1px border` (`border`), `shadow-elev-1` at rest.
  Desktop hover: `shadow-elev-2` + lift (`translateY(-2px)`). No hover effect on touch.
- `overflow: hidden`, whole card is a button (tap target). Focus ring = `focus_ring`, 2px.
- Selected state (deep-link only): `surface-active` background.

### Left priority accent bar
A 4-wide (`w-1`) full-height bar on the **left edge**, colored by priority — **shown for ALL
priorities** (changed; used to be P4/P5 only):

| priority | bar color | token |
|----------|-----------|-------|
| 1 min, 2 low | gray | `muted` |
| 3 normal | white/primary | `text` |
| 4 high | amber | `priority_high` |
| 5 max/urgent | coral | `priority_max` |

Dark mode only: P4 bar gets `glow_priority_high`, P5 bar gets `glow_priority_max` (outer glow).
P1–3 have no glow.

### Header band  (`px-4 pt-3 pb-2 pl-5`, row, `gap-2`, vertically centered)
1. **Priority badge** (see §2) — always present.
2. **Title** — `body` size, semibold, primary text, single-line truncate. If the message has no
   title, the **message body string** is used as the title.
3. **Unread dot** — only when `new == 1`: 8×8 (`w-2 h-2`) circle, `accent_ui` fill, `glow_accent_dot` (dark).
4. **X (delete) button** — `CloseIcon` (an X). Hover → `priority_max` (coral) + subtle scale.
   Tapping opens the **delete-confirm dialog** (§1.4). _(Replaced the old bell + "⋯" overflow.
   There is no per-card mute button anymore — mute lives in the sidebar ⋯ menu.)_

### Body slot  (`px-4`)
Renders `CardBody` (see [message-format.md](message-format.md)) — markdown / kv / list / chart /
sections / heuristic-kv. Plus optional `pending`/`error` slots (used by the optimistic publish queue).
**Always full content — no clamp, no "show more".**

### Meta row  (`px-4 py-2`, row, `gap-2`, vertically centered)
- **CardTags** (§3) on the left.
- **Timestamp** on the right, **always right-aligned** (`ml-auto`), `caption` size, muted.
  Format: **`YYYY-MM-DD HH:mm:ss`** (local time, locale-independent) — e.g. `2026-06-20 23:11:01`.
  _(Changed from the locale short format.)_

### Interaction
- **Tap** (anywhere not a button) → **mark the notification read** (clears the unread dot).
  There is **no detail view** to navigate to.
- **Swipe (touch only):**
  - Left swipe past threshold → reveals a **coral** (`priority_max`) "delete" backing → opens delete-confirm.
  - Right swipe past threshold → reveals an **emerald** (`accent_text`) "mark read" backing
    (only when unread).
  - Reveal width 96, snap threshold 72. The colored backing layers are **only mounted while
    swiping** (so on desktop / at rest they never bleed at the edges).
- **Delete-confirm dialog**: a small dialog ("delete this notification?") with Cancel / Delete.
  Used by both the X button and the swipe-delete.

> The optimistic-send card variant (publish queue) reuses the same shell with `pending`/`error`
> slots and no tap action.

---

## 2. PriorityBadge

Web: `message/PriorityBadge.jsx`. Chip, `variant="priority"`: **uppercase, extra-bold,
`caption` size, `radius_badge` (6) corners, `px-2 py-0.5`**.

**Shown for every priority** (changed; was P4/P5 only). `priority ?? 3`.

| priority | label (en) | background | text |
|----------|-----------|------------|------|
| 1 | `Min` | `surface_2` | `muted` |
| 2 | `Low` | `surface_2` | `muted` |
| 3 | `Normal` | `surface_2` | `text` |
| 4 | `High` | `priority_high` | `priority_high_on_surface` |
| 5 | `Urgent` | `priority_max` | `priority_max_on_surface` |

i18n keys: `notification_card_badge_min|low|normal|high|max`. (low=gray, normal=white is the
intended visual; high=amber, urgent=coral are filled.)

---

## 3. CardTags — categorized tag row

Web: `message/CardTags.jsx`. Renders inside the card meta row (left of timestamp). Three
categories, **left → right**:

1. **Topic tag** — the subscription/topic name. Fixed emerald pill (`TopicChip`:
   `topic_chip_bg` / `topic_chip_text`, `radius_full`, `px-3 py-1`, semibold). Shown when a topic
   name is provided (the All-notifications feed passes it; per-topic feed omits it).
2. **Service tags** — any tag with the **`service:` prefix**; the prefix is stripped and the
   remainder shown. **Fixed color** (slate-blue): `bg #2a3142` / `text #9db4d8`, pill, medium.
3. **General tags** — every other text tag. **Color is the name's hash** into a fixed 6-color
   palette (so the same tag is always the same color). Pill, no border.
   - Show **at most 2**; if there are 3+, append a **`+N more`** text button that expands to show all.

Excluded from the row: the **`card`** marker tag and any **emoji-shortcode tags** (those map to
emojis elsewhere). Order within a category preserves message order.

**General-tag palette** (literal — no tokens; pick index = `hash(name) % 6`):

| # | bg | text |
|---|------|------|
| 0 | `#332b52` | `#c4b5fd` (violet) |
| 1 | `#143a34` | `#7fe0cb` (teal) |
| 2 | `#3a2f14` | `#f5c97a` (amber) |
| 3 | `#3a1f22` | `#f5a3a5` (rose) |
| 4 | `#14303a` | `#7fc8e0` (sky) |
| 5 | `#283a14` | `#b7e07f` (lime) |

Hash (parity-critical — Android must match for color stability):
```
var h = 0
for (c in name) h = (h * 31 + c.code) ushr 0   // 32-bit unsigned
index = h % 6
```

---

## 4. Meter (inline bar)

Web: `ui/Meter.jsx`. Horizontal bar: track `meter_track`, `radius_full`, **7 tall**, fill width =
clamped value %. Fill color by threshold: `<65` → `meter_ok` (emerald), `≥65` → `meter_warning`
(amber), `≥90` → `meter_critical` (coral). Optional trailing label (`body_sm`, tabular-nums;
critical value uses `meter_critical` text, else muted). `role="meter"` for a11y.

---

## 5. Chips (`ui/Chip.jsx`)

Base: inline-flex, centered, `caption` size, focus ring. Variants:
- `priority` — `radius_badge`, uppercase, extra-bold, `px-2 py-0.5`; **bg/text supplied by caller**.
- `topic` — `radius_full`, `topic_chip_bg`/`topic_chip_text`, semibold, `px-3 py-1`.
- `tag` — `radius_full`, transparent bg, `control_border` border, muted, `px-3 py-1` (CardTags
  overrides bg/text per category).

---

## 6. Sidebar / Navigation drawer

Web: `Sidebar.jsx` (`SidebarContent` is shared by the desktop sidebar and the mobile drawer sheet).

Top → bottom:
- **All notifications** row — grid icon + label, navigates to `/`.
- **Subscription rows** (per topic) — see below.
- **Subscribe to topic** — plus icon + label, opens subscribe dialog.
- **Settings** (pinned lower) — gear icon + label, navigates to `/settings`.

### Subscription row
`[active bar] [left icon] [name] [unread count] [muted indicator?] [⋯ menu]`
- **Active bar:** 4-wide, 16-tall (`w-1 h-4`) pill on the far left; `accent_ui` + `glow_accent_dot`
  when this topic is active, transparent otherwise.
- **Left icon = message icon** (a chat bubble) — `accent_text` when active, `muted` otherwise.
  _(Changed from a bell.)_ In the collapsed icon-rail, a muted topic shows the **bell-off** icon
  instead so mute state stays visible.
- **Name:** `body_sm`, primary text, truncate.
- **Unread count:** when `new > 0`, right-aligned `caption`, `accent_text`, `99+` cap.
- **Muted indicator:** when muted, a **passive bell-off icon** (`muted`) sits just left of the ⋯
  button. **It is an indicator, not a button** (role=img, aria-label "Notifications muted").
- **⋯ context menu:** opens a menu with, in order:
  **Mute/Unmute** (toggles; label flips on muted state) · _separator_ · **Rename** · **Clear** ·
  _separator_ · **Unsubscribe**. _(Mute/Unmute moved here from a standalone always-visible button.)_

Mobile: this content is inside a **left slide-in sheet** (`width_nav_drawer` = 280), opened by the
app-bar hamburger.

---

## 7. Mobile App Bar

Web: `AppBar.jsx`. Only on mobile (`md:hidden`), 56 tall (`h-14`), `surface` bg, bottom border.
- **Hamburger** (left) → opens the drawer. `muted`, hover `accent_text`.
- **Title** (center, flex-1, truncate): the active topic name, or "All notifications".
- Right: a reserved connection-dot slot (placeholder).

> **There is no bottom navigation bar.** _(Removed — navigation is the hamburger drawer, which
> already contains All / topics / Subscribe / Settings.)_ Delete any `BottomNav` equivalent.

---

## 8. Publish FAB

Web: `PublishFab.jsx`. Fixed bottom-right (`bottom-6 right-4`, desktop `right-6`), 56×56
(`w-14 h-14`), `radius_sm`, **`accent_ui` fill / `accent_on_surface` icon** (a "+"/compose),
`shadow-elev-2`, hover lift+scale+brighten. Opens the publish sheet/dialog.

---

## 9. Publish sheet / dialog

Web: `PublishDialog.jsx`. **Mobile = bottom sheet, desktop = centered dialog.** Title
"Publish a message". Fields, top → bottom:
- **Topic name** (text input)
- **Title** (text input)
- **Message** (textarea, 4 rows)
- **Priority** — a row of 4 equal-width chips: **Low · Normal · High · Urgent** (values 2/3/4/5;
  default selected = Normal/3). The **selected** chip is tinted; unselected = `control_border`
  outline + muted. Selected styles (all four now tint — changed; Low/Normal used to look unselected):
  - Low → border+text `muted`, bg `muted/10`
  - Normal → border+text `text`, bg `text/10`
  - High → border+text `priority_high`, bg `priority_high/10`
  - Urgent → border+text `priority_max`, bg `priority_max/10`
- **Tags** (text input, comma-separated)
- Footer: **Close** (ghost) · **Send** (primary white-fill button).

Inputs: `surface_2` bg, `control_border` border, `radius_sm`, focus ring.

---

## 10. State surfaces

- **Loading:** skeleton cards (web `DataBoundary` shows ~5 skeletons).
- **Empty:** first-run / no-messages panels (`message/EmptyStates.jsx`) — icon tile + Korean voice
  copy. Per-topic vs all-feed have distinct copy.
- **Optimistic send:** a card with a "sending…" indicator; on failure a retry bar.

---

## Removed in this redesign (delete on Android)
- ❌ Detail view / detail pane / `/:topic/:msgId` detail route — **the card is the full view.**
- ❌ Bottom navigation bar.
- ❌ Per-card mute **bell** button and the card "⋯" overflow menu (now X-delete only).
- ❌ Sidebar standalone mute toggle button (now in the ⋯ menu).
- ❌ Card compact/preview mode + "Show more" / "+N more" on card bodies.
- ❌ Sticky per-topic feed header (the topic-name banner above the feed).
- ❌ Rounded-right card corner (cards are fully squared now).
