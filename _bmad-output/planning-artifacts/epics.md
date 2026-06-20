---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
inputDocuments:
  - _bmad-output/specs/spec-ui-parity/SPEC.md
  - _bmad-output/specs/spec-ui-parity/brownfield.md
  - docs/ui-parity/design-tokens.md
  - docs/ui-parity/components.md
  - docs/ui-parity/message-format.md
  - docs/ui-parity/screens-layout.md
  - docs/ui-parity/CHANGELOG-redesign-2026-06.md
  - docs/ui-parity/screenshots/  # visual reference; companions/web app win where they disagree (some shots are stale post-redesign)
---

# ntfy-android - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the **ntfy Android UI Parity & Structured Message Rendering** initiative, decomposing the requirements from the SPEC kernel (`SPEC-ui-parity`) and its preservation-validated companions into implementable stories.

> **Input model note:** the canonical contract for this work is a **SPEC kernel + companions**, not a PRD/Architecture/UX trio. Mapping used here: SPEC **Capabilities (CAP-N)** → Functional Requirements; SPEC **Constraints** → Non-Functional Requirements; the **companion catalogs** (`design-tokens.md`, `components.md`, `message-format.md`, `screens-layout.md`) → UX Design Requirements; **`brownfield.md`** → Additional (architecture) Requirements. Where a companion disagrees with a screenshot, the companion wins; where a companion disagrees with the running ntfy-web app, the web app wins.

## Requirements Inventory

### Functional Requirements

FR1 (CAP-2): The notification card renders as the redesigned "product" surface — fully squared (no radius), `surface` background, `1px border`, resting `shadow_elev_1`, with a 4dp left priority accent bar colored for all five priorities (P1–2 gray `muted`, P3 white/`text`, P4 amber `priority_high`, P5 coral `priority_max`); dark mode adds `glow_priority_high` on P4 and `glow_priority_max` on P5.
FR2 (CAP-3): Every card header shows a priority badge (Min/Low/Normal/High/Urgent), the title (falling back to the message body string when titleless, single-line truncate), an unread dot only when `new == 1`, and an X delete button that opens a delete-confirm dialog; the old per-card bell and ⋯ overflow are absent.
FR3 (CAP-4): The card meta row renders a categorized tag set — topic (emerald pill, shown only on the All feed) · `service:` (slate-blue, prefix stripped) · general (hash-colored, max 2 + "+N more" expander) — and a right-aligned timestamp; the `card` marker tag and emoji-shortcode tags are excluded.
FR4 (CAP-5): A message tagged `card` whose body is valid JSON of type `kv`/`list`/`chart`/`sections` renders as a structured card via `parseCardSpec` (tag `card` AND parseable JSON AND known `type`); everything else falls back to the markdown/plain-text path; invalid payloads fall back without crashing.
FR5 (CAP-6): Structured rendering matches web's parsing details exactly — kv leading-icon map (exact → first word → `·`), status/meter thresholds (`<65`/`≥65`/`≥90`), chart constraints (hand-drawn, 120dp tall, ≤60 points, labels only when ≤12), and markdown security (linkify only http/https/mailto, drop unsafe-scheme images); a malformed payload shows raw text and never crashes.
FR6 (CAP-7): The card is the complete, final view of a message — tapping marks it read (clears the unread dot, no navigation); left swipe past threshold reveals a coral delete backing (opens confirm), right swipe past threshold reveals an emerald mark-read backing (unread only); backing layers mount only while swiping. There is no detail screen/route/Activity. _(Split across epics: **FR6a tap→mark-read** = Epic 2 card click; **FR6b swipe→delete/mark-read** = Epic 4 `ItemTouchHelper` on the feed RecyclerView.)_
FR7 (CAP-8): The app shell is a single feed surface (All / per-topic) plus a settings surface, switched via a left hamburger navigation drawer, with an emerald publish FAB always present on the feed; there is no bottom navigation bar.
FR8 (CAP-9): The feed lists full notification cards newest-first by `sequenceId` descending, 18dp card gap, 20-per-page infinite scroll; only genuinely newly-arrived cards play the 0.25s slide-in (reduced-motion respected); loading shows skeleton cards, empty shows the first-run/no-messages panel; the All feed shows each card's topic chip and the per-topic feed omits it; no sticky topic header.
FR9 (CAP-10): The navigation drawer lists All notifications, subscription rows, Subscribe, and Settings; each topic row shows an active bar, unread count (`99+` cap), a passive muted (bell-off) indicator, a message/chat left icon (not a bell), and a ⋯ menu offering Mute/Unmute · Rename · Clear · Unsubscribe; there is no standalone always-visible mute button.
FR10 (CAP-11): The publish surface is a bottom sheet collecting topic/title/message/priority/tags, with four priority chips (Low/Normal/High/Urgent, default Normal) each showing a selected tint, and a Close/Send footer.
FR11 (CAP-12): The app supports light and dark themes (dark default/hero), both rendered from tokens with no pure black/white canvas (dark `#0C0D0F`, light `#F3F4F6`), selectable via a Light/Dark/System segmented control in Appearance settings.

### NonFunctional Requirements

NFR1: All new UI is built against the token resource keys in `design-tokens.md`; never hard-code hex/px except the documented literal general-tag palette and service-tag color (used as-is in both themes). A grep of new UI code finds no raw hex/px outside those literals.
NFR2 (parity-critical): The structured-card parser matches web byte-for-byte where parity is observable — the 32-bit unsigned tag-hash, the kv icon map, the meter thresholds (`<65`/`≥65`/`≥90`), and the dual gate for card detection (tag `card` AND valid JSON AND known `type`).
NFR3: No external chart library — the chart is a thin hand-drawn Canvas/Compose-Canvas bar/line in emerald accent.
NFR4 (security/fault-tolerance): Only `http`/`https`/`mailto` links are live; unsafe-scheme images are dropped; no `javascript:`/data URLs; any malformed payload degrades to the raw message string inside a try/catch and must never crash the card.
NFR5: The card renders **full content** — no compact/preview mode, no "show more", no "+N more" on card bodies.
NFR6: Phone-first — only the mobile column (top bar + full-width feed + drawer + FAB) is required; no tablet/desktop multi-pane or detail-pane layout.
NFR7: Timestamps are `YYYY-MM-DD HH:mm:ss` in local time, locale-independent, always right-aligned.
NFR8: Feed ordering is `sequenceId` descending (server sequence), never wall-clock time.
NFR9: The `card`-tagged JSON path is mandatory for parity; the untagged `key: value` heuristic-kv fallback is **in scope for the first release** (decided 2026-06-21).

### Additional Requirements

_(Architecture / brownfield context from `brownfield.md` — the existing `io.heckel.ntfy` app this rework restructures.)_

- **Stack today:** View/XML + AppCompat (`androidx.appcompat:appcompat:1.7.1`); **no Jetpack Compose** in the build. The structured-card renderer (Canvas charts, markdown, dynamic kv layout) is the piece most likely to motivate introducing Compose.
- **Rendering-stack decision — DECIDED (2026-06-21): minimal-change path.** Incrementally re-skin the existing Views/XML and `DetailAdapter`; **do NOT introduce Jetpack Compose.** The structured-card renderer (kv/list/chart/sections, markdown, hand-drawn Canvas chart) is built in the existing View system. This caps story sizing toward re-skin/extend rather than rewrite.
- **Card extraction — DECIDED (2026-06-21, party-mode review): the redesigned card is an adapter-agnostic binder.** The card = `fragment_detail_item.xml` (layout, with a named `@+id/card_body` container) + a standalone `MessageCardBinder`/`ViewHolder` that does **not** reference `DetailActivity`/adapter context directly. Both the current list and the future single-feed `RecyclerView` reuse the same layout + binder, so the Epic 4 shell swap incurs **zero card rework**. Avoids the `fragment_detail_item.xml` / `DetailAdapter.onBindViewHolder` file-collision hotspot.
- **Navigation restructure (biggest structural delta):** `MainActivity` (subscriptions list) + `DetailActivity` (per-topic message list) → a single **feed** destination (All + per-topic) + drawer; `DetailSettingsActivity` folds into the drawer row ⋯ menu; `PublishFragment` → publish FAB + bottom sheet; `AddFragment` → drawer "Subscribe to topic". The current per-message row (`DetailAdapter` + `fragment_detail_item.xml`) is the natural place the redesigned card replaces.
- **To remove:** the two-level subscriptions→detail drill-down as primary nav, any per-message detail/expansion screen, per-card mute/overflow controls; confirm there is no bottom nav to remove.
- **Carries over (delivery/storage unchanged — this is a UI rework):** `db/` (Room) message/subscription model, `msg/`/`service/`/`work/` (notification delivery, WorkManager, connection), `up/` (UnifiedPush).
- **Data model:** the `sequenceId`-descending feed ordering needs a server-sequence field on the message model — verify whether the current Room schema already stores it or whether it must be added (with migration).
- **Localization:** keep the existing Weblate pipeline; add new strings as localizable resources (`notification_card_badge_min|low|normal|high|max`, empty-state Korean voice copy).

### UX Design Requirements

UX-DR1 (CAP-1 — design tokens): Expose the full ntfy-web design-token set as Android platform resources using the `android-key` snake_case names in `design-tokens.md` — colors (`@color/...`), font families/sizes/line-heights, spacing scale (`@dimen/spacing_1..7`), radii (`radius_sm/md/full/badge`; card = fully squared/no radius), and elevation/shadow (`shadow_flat/elev_1/elev_2`) — with correct **light + dark** values, so all UI is built against token keys.
UX-DR2 (literal palettes): Replicate verbatim, as literal hex (no tokens), the **6-color general-tag palette** (index = `hash(name) % 6`) and the fixed **service-tag color** (`#2a3142` / `#9db4d8`); these dark-theme values are used as-is in both themes. Dark-only glow effects (`glow_priority_high/max`, `glow_accent_dot`) apply in dark mode only.
UX-DR3 (component primitives): Build the shared component anatomy from `components.md` — **PriorityBadge** (uppercase extra-bold chip, `radius_badge`, per-priority bg/text), **Chip** (priority/topic/tag variants), **Meter** (inline bar, 7-tall, `radius_full`, threshold fills), and the structured-card block renderers (**kv** row `[icon][key][value][meter?]` with status dots/columns:2, **list** ordered/bulleted, **chart** bar/line, **sections** ordered mix), plus **state surfaces** (skeleton loader ~5 cards, first-run/no-messages EmptyState panels with Korean voice copy, optimistic-send card).
UX-DR4 (accessibility): Focus ring = `focus_ring`, 2px; respect reduced-motion for the slide-in animation; fire an a11y live-region announcement ("new notifications") on arrival; meter exposes `role="meter"`; the muted indicator is `role=img` / aria-label "Notifications muted" (an indicator, not a button); light-theme color values meet their WCAG AA contrast targets.
UX-DR5 (responsive / phone-first): Only the mobile column is required (AppBar 56dp top bar + full-width feed + left slide-in drawer `width_nav_drawer` 280 + FAB). kv `columns:2` uses a 2-column grid only when available width ≥ ~600dp, else 1 column. A tablet/foldable icon-rail is **out of scope** (phone-only confirmed 2026-06-21).

### FR Coverage Map

- FR1 (priority accent bar): **Epic 2** — card left bar, all 5 priorities + dark glow.
- FR2 (header): **Epic 2** — badge, title fallback, unread dot, X-delete + confirm.
- FR3 (tags + timestamp): **Epic 2** — categorized tag row, right-aligned timestamp.
- FR4 (card JSON path): **Epic 3** — `parseCardSpec` dual gate, fallback path.
- FR5 (parsing details): **Epic 3** — icon map, thresholds, Canvas chart, markdown security.
- FR6a (tap → mark read): **Epic 2** — card click listener, no navigation.
- FR6b (swipe → delete / mark read): **Epic 4** — `ItemTouchHelper` on the feed RecyclerView.
- FR7 (feed + drawer + FAB shell): **Epic 4** — single-feed shell, no bottom nav.
- FR8 (feed list / order / states): **Epic 4** — `sequenceId` desc, pagination, container states.
- FR9 (drawer rows): **Epic 4** — topic rows, active bar, unread, muted indicator, ⋯ menu.
- FR10 (publish sheet): **Epic 4** — publish FAB + bottom sheet, priority chips.
- FR11 (themes): **Epic 1** — light/dark tokens + Light/Dark/System switch.
- _Enabler (no FR; brownfield/NFR8):_ **Epic 0** — `sequenceId` Room column + migration + receive-path fill + DAO ordering (prerequisite for FR8 ordering).

## Epic List

### Epic 0: Data foundation — server sequence ordering
Add the server `sequenceId` to the message data layer so the feed can order by server sequence (NFR8) instead of wall-clock. Pure data/delivery work, no UI — separated so a Room migration bug can never pollute a UI story's "done", and merged before Epic 4 needs ordering. Can run in parallel with Epic 1.
**FRs covered:** (enabler for FR8) · **Touches:** `db/` entities, `Database.kt` version + `Migration`, DAO `ORDER BY sequenceId DESC`, receive path (`msg/`/`service/`/`work/`) populating `sequenceId`; legacy/null handling for messages with no server sequence.

### Epic 1: Themeable visual foundation (tokens + light/dark + a11y primitives)
Expose the ntfy-web design-token set as Android resources (color/type/spacing/radius/elevation, light + dark) and ship the Light/Dark/System theme switch; own the shared accessibility/visual primitives (focus-ring token, minimum-contrast values, reduced-motion detection utility) so later epics inherit them. Standalone value: dark-default redesigned look + theme control.
**FRs covered:** FR11 · **Supports:** UX-DR1, UX-DR2, UX-DR4 (contrast + reduced-motion/focus primitives), NFR1, NFR6

### Epic 2: Redesigned notification card (adapter-agnostic binder)
Build the squared card as a reusable `fragment_detail_item.xml` (with a named `@+id/card_body` slot) + standalone `MessageCardBinder`/`ViewHolder` (no `DetailActivity` coupling): priority accent bar (all 5 + dark glow), header (badge / title fallback / unread dot / X-delete + confirm), categorized tag row + `YYYY-MM-DD HH:mm:ss` timestamp, **tap → mark read** (FR6a), plus the card's own **skeleton/loading state and new-arrival slide-in animation** (reduced-motion respected). A11y inline in ACs: focus ring, muted indicator `role=img`, live-region announce on arrival.
**FRs covered:** FR1, FR2, FR3, FR6a · **Supports:** UX-DR3 (badge/chip + skeleton/arrival), UX-DR4 (focus ring, live region, reduced-motion), NFR5, NFR7

### Epic 3: Structured message rendering (card body)
Render the `@+id/card_body` slot: parse `card`-tagged JSON (kv/list/chart/sections) + markdown + heuristic-kv into real components — byte-for-byte parser parity with web (32-bit tag hash, kv icon map, meter thresholds `<65`/`≥65`/`≥90`, dual gate), hand-drawn Canvas chart (≤60 pts, 120dp, labels ≤12), security/fault-tolerance (link-scheme whitelist, malformed → raw, never crash). Fills the body slot Epic 2 defined; does not touch the card shell.
**FRs covered:** FR4, FR5 · **Supports:** UX-DR3 (kv/list/chart/sections, meter), NFR2, NFR3, NFR4, NFR5, NFR9

### Epic 4: Single-feed app shell & navigation
Restructure `MainActivity`+`DetailActivity` drill-down into a single feed (All / per-topic) + hamburger drawer + publish FAB/bottom-sheet, reusing the Epic 2 card binder so there is no card rework. Feed orders by `sequenceId` desc (consumes Epic 0), 18dp gap, 20-per-page infinite scroll, feed-container states (loading / first-run / no-messages empty panel with Korean voice copy), live-region on arrival; **swipe → delete / mark-read** (FR6b) via `ItemTouchHelper`; drawer topic rows (active bar / unread / muted indicator / ⋯ menu); publish sheet with priority chips.
**FRs covered:** FR7, FR8, FR9, FR10, FR6b · **Supports:** UX-DR3 (empty/loading container states), UX-DR4 (reduced-motion), UX-DR5 (phone-first responsive), NFR6, NFR8

**Dependency flow:** (Epic 0 ∥ Epic 1) → Epic 2 → Epic 3 · Epic 4. Epic 0 must merge before Epic 4's ordering; Epic 2's card binder is consumed by both Epic 3 (body slot) and Epic 4 (feed), with no forward rework.

---

## Epic 0: Data foundation — server sequence ordering

Add the server `sequenceId` to the message data layer so the feed can order by server sequence (NFR8). Pure data/delivery work; no UI. Merges before Epic 4 needs ordering; runs in parallel with Epic 1.

### Story 0.1: Add `sequenceId` to the message schema with migration

As a developer,
I want the message Room entity to store the server-assigned sequence id with a safe migration,
So that the feed can later order strictly by server sequence instead of wall-clock time.

**Acceptance Criteria:**

**Given** the current Room database at its existing version
**When** a `sequenceId` (nullable Long) column is added to the message entity and the DB version is incremented with a `Migration`
**Then** an existing populated database upgrades with **no data loss** and the new column defaults to null for legacy rows
**And** a migration instrumentation test (old schema → new schema) passes green
**And** no UI or query behavior changes yet (column is added, not consumed).

### Story 0.2: Populate `sequenceId` on receive and order the DAO query

As a developer,
I want incoming messages to record their server `sequenceId` and the message DAO to expose a `sequenceId`-descending query,
So that downstream feeds get correct server-sequence ordering with stable fallback for legacy/null rows.

**Acceptance Criteria:**

**Given** a message arriving via the receive path (`msg/`/`service/`/`work/`)
**When** the server payload carries a sequence value
**Then** that value is persisted to `sequenceId`
**And** the message DAO provides a query ordered by `sequenceId DESC` with a deterministic tiebreaker (e.g. existing id/timestamp) for rows where `sequenceId` is null
**And** a DAO test confirms ordering is `sequenceId` desc, never wall-clock, and that null-sequence legacy rows sort deterministically.

---

## Epic 1: Themeable visual foundation (tokens + light/dark + a11y primitives)

Expose the ntfy-web design tokens as Android resources (light + dark), ship the theme switch, and own the shared accessibility/motion primitives. **Constraint (NFR / re-skin decision):** all of Epic 1 is delivered in the existing View/XML resource system — **no Jetpack Compose** is introduced. **No new design tokens** beyond `design-tokens.md` (Non-goal): destructive/error UI reuses the existing `priority_max`/`priority_urgent` coral tokens; glows reuse the existing `glow_*` tokens.

### Story 1.1: Color token resources (light + dark)

As a developer,
I want every color token in `design-tokens.md` as a snake_case Android color resource with correct light and dark values,
So that all UI is built against `@color/...` keys rather than raw hex.

**Acceptance Criteria:**

**Given** the color table in `design-tokens.md`
**When** color resources are defined in `values/` (light) and `values-night/` (dark)
**Then** every canonical color exists under its exact `android-key` name (e.g. `@color/accent_text`, `@color/priority_high`, `@color/focus_ring`) with the documented light + dark hex
**And** the accent sub-tokens (`accent_text`/`accent_ui`/`accent_on_surface`) are all present per the accent decision table
**And** destructive/error UI is defined to reference the existing `priority_max`/`priority_urgent` tokens (no new error token is introduced — Non-goal)
**And** a grep of these resources finds no value diverging from the companion's hex
**And** a static check (lint/grep) over new UI code is added to CI asserting **no raw hex literal** appears outside the documented literal tag/service palette (NFR1; this is the testable home for "grep finds no raw hex").

### Story 1.2: Non-color token resources + literal tag palettes

As a developer,
I want typography, spacing, radius, and elevation tokens plus the literal tag palettes as Android resources,
So that sizing/shape/elevation and tag colors are all referenced by key.

**Acceptance Criteria:**

**Given** the radius/elevation/typography/spacing and literal-color tables in `design-tokens.md`
**When** dimens/text-appearances/shadow resources and the literal palette arrays are defined
**Then** `spacing_1..7`, `radius_sm/md/full/badge` (card = squared / no radius), font sizes/line-heights, and `shadow_flat/elev_1/elev_2` exist under their exact `android-key` names
**And** the 6-color general-tag palette and the fixed service-tag color (`#2a3142`/`#9db4d8`) are defined verbatim as literals (no token), used as-is in both themes
**And** the dark-only glow effects (`glow_priority_high/max`, `glow_accent_dot`) are defined for dark only and not applied in light, and a single reusable glow application rule (blur radius / opacity sourced from these tokens) is documented so every glowing surface — priority bar, unread dot, deep-link highlight, chart — derives its glow from the same tokens rather than per-component values.

### Story 1.3: Light / Dark / System theme switch

As a user,
I want a Light / Dark / System theme control with Dark as the default,
So that I can choose my appearance and get the dark-default hero look.

**Acceptance Criteria:**

**Given** the Appearance section of Settings
**When** I open it on a fresh install
**Then** a segmented Light / Dark / System control is shown with **Dark selected by default**
**And** selecting each option applies that theme app-wide, rendered entirely from tokens
**And** neither theme uses a pure black/white canvas (dark `#0C0D0F`, light `#F3F4F6`)
**And** the choice persists across app restarts.

### Story 1.4: Reduced-motion & accessibility primitives

As a developer,
I want shared helpers for reduced-motion detection and a standard focus indicator,
So that later epics inherit consistent, owned accessibility behavior instead of reinventing it.

**Acceptance Criteria:**

**Given** the system "remove animations" / reduced-motion setting
**When** a reduced-motion helper is queried
**Then** it reports the current setting so callers can skip animations
**And** a reusable focus-indicator style backed by `@color/focus_ring` at 2px is available for focusable controls
**And** light-theme token values are confirmed against their WCAG AA contrast targets (documented check).

---

## Epic 2: Redesigned notification card (adapter-agnostic binder)

Build the squared card as a reusable layout + standalone binder with a body slot, plus the card's own loading/arrival states. Consumed unchanged by Epic 3 (body) and Epic 4 (feed).

### Story 2.1: Adapter-agnostic card shell + body slot

As a developer,
I want a squared card layout and a standalone `MessageCardBinder`/ViewHolder decoupled from any Activity/adapter,
So that both the current list and the future single feed reuse the identical card with no rework.

**Acceptance Criteria:**

**Given** `fragment_detail_item.xml` and a new `MessageCardBinder`
**When** the card is rendered
**Then** the container is fully squared (no radius), `@color/surface` background, 1px `@color/border`, resting `shadow_elev_1`, `overflow:hidden`, whole-card tap target with the 2px focus ring
**And** the layout exposes a named `@+id/card_body` container for body content **and a placeholder accent-bar view** (so Story 2.2 only colors it, not re-edits the XML)
**And** the binder takes a message + a topic-name argument and holds **no reference** to `DetailActivity` or a specific adapter (verified by it compiling against a plain `ViewGroup`/context)
**And** this story owns `fragment_detail_item.xml`; **Epic 3 stories MUST NOT edit `fragment_detail_item.xml`** — they render into `@+id/card_body` via the binder using their own `view_card_<type>.xml` layouts (file-collision guard)
**And** 2.1 ships the shell only (no loading/skeleton state — that is Story 2.6); corners show no rounding in either theme.

### Story 2.2: Priority accent bar (all five priorities)

As a user,
I want a colored left bar on each card matching its priority,
So that I can gauge urgency at a glance, identically to web.

**Acceptance Criteria:**

**Given** a card of priority 1–5
**When** it renders
**Then** a 4dp full-height left bar shows the `components.md §1` color — P1–2 `muted`, P3 `text`, P4 `priority_high`, P5 `priority_max` (asserted as token equality, not by eye)
**And** in dark mode only, P4 gets the `glow_priority_high` token and P5 gets the `glow_priority_max` token, applied via the shared glow rule (Story 1.2); P1–3 have no glow — asserted as a measurable output (the bar's shadow/outline color equals the glow token in dark, and is absent in light)
**And** light mode applies no glow.

### Story 2.3a: Card header static content (badge, title, unread dot)

As a user,
I want each card header to show priority, title, and unread state,
So that I can identify a notification at a glance without a detail screen.

**Acceptance Criteria:**

**Given** a card header
**When** it renders
**Then** a priority badge appears for **every** priority with the label/colors in `components.md §2` (Min/Low/Normal/High/Urgent; uppercase, extra-bold, `radius_badge`), using i18n keys `notification_card_badge_min|low|normal|high|max`
**And** the title shows the message title, falling back to the message body string when titleless, single-line truncated
**And** an unread dot (`accent_ui`, `glow_accent_dot` in dark) appears only when `new == 1`
**And** the old per-card bell and ⋯ overflow are absent.

### Story 2.3b: X-delete with token-styled confirm dialog

As a user,
I want an X button that asks before deleting,
So that I can remove a notification safely without an accidental loss.

**Acceptance Criteria:**

**Given** a card header
**When** I tap the X (delete) button
**Then** a delete-confirm dialog (Cancel / Delete) opens, styled from the design tokens (Story 1.1/1.2) so it honors dark-default
**And** the notification is deleted only on Delete and untouched on Cancel
**And** the X hover/press state uses `priority_max` (coral) per `components.md §1`.

### Story 2.4: Categorized tag row + timestamp

As a user,
I want the card meta row to show categorized tag chips and a right-aligned timestamp,
So that tags are color-stable and identical to web.

**Acceptance Criteria:**

**Given** a message with tags
**When** the meta row renders
**Then** tags render in order topic (emerald `topic_chip` — shown only when a topic name is passed) · `service:` (slate-blue literal, prefix stripped) · general (hash-colored), per `components.md §3`
**And** general tags show **at most 2**; if 3+, a `+N more` text button is appended that, when tapped, expands to reveal all remaining general tags (full content — no permanent truncation); the `card` marker and emoji-shortcode tags are excluded
**And** the general-tag color index = `hash(name) % 6` using the 32-bit unsigned hash `h = (h*31 + c.code) ushr 0`, matching the web pseudocode for the same name (parity-critical, NFR2); this story owns its hash golden test (tag-name → index → hex vectors), self-contained within Epic 2
**And** the timestamp is right-aligned (`ml-auto`), format `YYYY-MM-DD HH:mm:ss` in local time, locale-independent (absolute format per NFR7 — **not** relative "n minutes ago").

### Story 2.5: Tap to mark read (FR6a)

As a user,
I want tapping a card to mark it read,
So that I clear new items without navigating anywhere (there is no detail view).

**Acceptance Criteria:**

**Given** an unread card (`new == 1`)
**When** I tap anywhere on it that is not a button
**Then** `markAsRead(<id>)` is invoked exactly once and the unread dot clears
**And** **no** navigation/Activity/route/detail screen is triggered (Non-goal guard)
**And** tapping the X button or a tag/`+N more` button does not trigger mark-read.

### Story 2.6: Card loading skeleton, new-arrival animation & deep-link highlight

As a user,
I want cards to show a loading placeholder, newly-arrived cards to slide in, and a deep-linked card to be highlighted,
So that the feed feels alive and I can find the message I tapped a notification for — while respecting reduced-motion.

**Acceptance Criteria:**

**Given** the card component
**When** it is in a loading state
**Then** it renders a skeleton matching the card's shape (corners, bar, header, tag-row heights)
**And** when a card is flagged as genuinely newly-arrived, **only that card** plays the 0.25s slide-in-from-top
**And** when a card is the deep-link target, it shows a transient highlight pulse (border glow fading out, sourced from the Story 1.2 glow rule); the feed scroll-to-target is owned by Story 4.1
**And** when reduced-motion is on (Story 1.4 helper), animations are skipped — asserted measurably: with `Settings.Global.ANIMATOR_DURATION_SCALE == 0` the slide-in/pulse duration is 0 and the deep-link target falls back to a static (non-animated) emphasis
**And** an accessibility live-region announcement is emitted on arrival.

> **Epic 2 merge gating:** the redesigned card is exposed to users only when Stories 2.1–2.4 (+2.3b) ship together as one release bundle — never a half-card (e.g. no tags/timestamp). Stories may merge to the branch incrementally but are feature-gated until the bundle is complete.

---

## Epic 3: Structured message rendering (card body)

Render the `@+id/card_body` slot from the message body. Parser parity is byte-exact; everything degrades to raw text, never crashes. **Slicing note:** each renderer story produces a user-visible payload type (kv table / list / chart / mixed); Stories 3.0 (golden), 3.1 (dispatch) and 3.2 (meter) are enablers that ship **with** the first payload renderer (3.3 kv), not as standalone user value. **Ordering note:** `chart` (3.5) and `sections` (3.7) are sequenced last so they form a clean fast-follow slice if schedule slips — but they are **in scope for v1** (SPEC makes all four block types mandatory for parity).

### Story 3.0: Parser parity golden corpus

As a developer,
I want a single fixture set of golden vectors for every parity-critical parser rule,
So that hash colors, icon glyphs, meter thresholds, and card detection stay byte-for-byte aligned with web and regressions are caught automatically.

**Acceptance Criteria:**

**Given** the parity-critical rules in `components.md` / `message-format.md`
**When** a golden-corpus test fixture is created
**Then** it contains: the **full** kv key→glyph icon map (incl. exact / first-word / `·` fallback cases); meter boundary cases (`64`/`65`/`89`/`90`/`91` → ok/warn/critical); markdown safe/unsafe link-and-image scheme cases; and a dual-gate pass/fail set (tag+valid+known → render; missing tag, invalid JSON, unknown type → raw fallback)
**And** these fixtures are referenced by Stories 3.2, 3.3, 3.6b, 3.8 rather than each re-encoding values (the tag-hash golden vectors live in Story 2.4, where the hash is first used)
**And** a test runs the corpus and fails on any divergence.

### Story 3.1: Card detection, dispatch & safe fallback

As a user,
I want monitoring/CI payloads to render as structured cards and everything else as text,
So that rich messages look right and malformed ones never break the card.

**Acceptance Criteria:**

**Given** a notification
**When** the body is dispatched for rendering
**Then** `parseCardSpec` returns a spec **only** when the tags contain `card` AND the body parses as JSON AND `type` ∈ {kv,list,chart,sections} (`message-format.md §1`)
**And** the negative cases are explicitly asserted (dual-gate, per Story 3.0): missing `card` tag, unparseable JSON, and a known-`card`-tag-but-unknown-`type` each fall back to the text path — not just the positive case
**And** the dispatch order is defined and tested: structured spec → heuristic-kv (§6) → paragraph/raw
**And** any parsing/rendering error is caught (try/catch), including a malformed/fuzz payload, and the raw message string is shown instead — the card never crashes
**And** the raw fallback is itself token-styled (clean, not "unfinished-looking")
**And** the `card` marker tag is never displayed as a chip.

### Story 3.2: Inline meter component

As a user,
I want a horizontal meter bar with threshold colors,
So that numeric values read at a glance, identically to web.

**Acceptance Criteria:**

**Given** a value 0–100
**When** a meter renders
**Then** it is a `radius_full` bar on `meter_track`, 7 tall, fill width = clamped value %
**And** fill color is `<65` `meter_ok`, `≥65` `meter_warning`, `≥90` `meter_critical` (NFR2 thresholds), with boundary cases `64`→ok, `65`→warning, `89`→warning, `90`/`91`→critical asserted against the Story 3.0 corpus
**And** the value is clamped to 0–100 (e.g. `-5`→0, `130`→100)
**And** it exposes a meter accessibility role.

### Story 3.3: `kv` block renderer

As a user,
I want key-value "monitor" cards with icons, status, and meters,
So that system/metric payloads render as a structured table.

**Acceptance Criteria:**

**Given** a `kv` spec
**When** it renders each row as `[leading icon] [key (muted)] [value] [meter?]`
**Then** the leading icon resolves via the §4 map: lowercased `icon ?? key` exact → first word → fallback `·` (parity-critical)
**And** `status:"error"` colors the value coral (`priority_urgent`); `ok`/`warn` do not recolor it
**And** when `status` is set and there is **no** meter, a status dot precedes the value (`ok`→`accent_ui`, `warn`→`priority_high`, `error`→`priority_max`)
**And** a meter bar (Story 3.2) renders only when `meter` is a finite number
**And** `columns:2` uses a 2-column grid only when available width ≥ ~600dp, else 1 column (mobile collapses).

### Story 3.4: `list` block renderer

As a user,
I want ordered and bulleted lists,
So that step/result payloads render as real lists.

**Acceptance Criteria:**

**Given** a `list` spec
**When** it renders
**Then** `ordered:true` produces numbered `1.` items and otherwise bulleted `•` items
**And** all items render with no truncation, coerced to strings, styled `body_sm` muted.

### Story 3.5: `chart` block renderer (hand-drawn Canvas)

As a user,
I want compact bar/line charts without a chart library,
So that time-series payloads render in parity and stay dependency-free.

**Acceptance Criteria:**

**Given** a `chart` spec
**When** it renders on a Canvas (no external chart lib, NFR3)
**Then** it is a single emerald (`accent_text`/`accent_ui`) chart, 120dp tall, full width; `kind:"line"` draws a 2px polyline else vertical bars
**And** non-finite points are dropped; when more than 60 valid points are supplied, the data is capped at the first 60 (no crash); the y-axis auto-scales to `[min(0,…), max]`
**And** axis labels show only when ≤12 points (else omitted entirely — e.g. 13 points → no labels), each label = `label` or `value+unit`
**And** empty/all-invalid data renders nothing.

### Story 3.6a: Markdown renderer (styles)

As a user,
I want formatted markdown text,
So that rich message bodies render with correct typography.

**Acceptance Criteria:**

**Given** a markdown string
**When** it renders per `message-format.md §5`
**Then** paragraph/heading/bold/italic/inline-code/code-block/blockquote/list styles match the documented intent (using tokens, no display-size headings inside cards)
**And** a malformed payload falls back to raw text without crashing.

### Story 3.6b: Markdown link/image security

As a user,
I want unsafe links and images neutralized,
So that a malicious payload can't execute script or leak data via the card.

**Acceptance Criteria:**

**Given** markdown containing links and images
**When** it renders
**Then** only `http`/`https`/`mailto` links are live; any other scheme (notably `javascript:` and `data:` URLs) renders as inert accent-colored text (NFR4)
**And** images render only for safe-scheme `src`, otherwise the image is dropped
**And** these rules are covered by the Story 3.0 corpus (safe vs unsafe scheme cases), asserted as a security test.

### Story 3.7: `sections` block renderer (mixed, ordered)

As a user,
I want mixed cards combining text, tables, lists, and charts,
So that full incident/CI reports render as one structured card.

**Acceptance Criteria:**

**Given** a `sections` spec
**When** it renders
**Then** each block renders in order, vertically, with a 12dp gap, dispatching to markdown/kv/list/chart (Stories 3.3–3.6)
**And** a `markdown` block is valid only inside `sections`
**And** a nested `sections` block is ignored — asserted with an input case `{sections:[{sections:[…]}]}` whose inner block renders nothing while the outer siblings still render (no recursion, Non-goal)
**And** an unknown block `type` renders nothing while siblings still render.

> **NFR5 (full content — no clamp / no show-more):** Stories 3.3 (kv), 3.4 (list), 3.6a (markdown) and 3.7 (sections) each carry a negative AC — rendered card-body text uses **no** `maxLines`/`ellipsize` and exposes **no** "show more"/compact affordance; a long-body test asserts every row/item/block is fully present. This is the testable home for NFR5 and the Non-goal "no compact/preview".

### Story 3.8: Heuristic-kv fallback (untagged `key: value`)

As a user,
I want untagged `key: value` messages to still render as a kv table,
So that plain monitoring text gets structure without the `card` tag.

**Acceptance Criteria:**

**Given** a message with no `card` tag whose every non-empty line matches `^[^:]+:\s*.*$`
**When** it renders via the fallback path (§6)
**Then** it reuses the kv renderer (Story 3.3): a trailing percent/number becomes a `meter`, and a key matching `/error|fail|err/i` becomes `status:"error"`
**And** the kv-vs-paragraph decision is covered by the Story 3.0 dual-gate/shape corpus (pass: all-lines-`key: value`; fail cases → paragraph)
**And** a single line or empty body renders as a paragraph instead
**And** any other shape renders as a paragraph.

---

## Epic 4: Single-feed app shell & navigation

Replace the subscriptions→detail drill-down with one feed + drawer + publish FAB/sheet, reusing the Epic 2 card binder.

### Story 4.1: Single feed surface (ordered, reusing the card binder)

As a user,
I want one feed of full notification cards in server order,
So that I see all my notifications in a single scrollable surface, like web.

**Acceptance Criteria:**

**Given** the former `DetailActivity` per-topic list
**When** it is reworked into a single feed RecyclerView
**Then** the feed renders full cards via the **Epic 2 binder** (no card rework) ordered by `sequenceId DESC` (Epic 0), with an 18dp gap between cards, showing the **first page** (20)
**And** there is no separate detail Activity/route to enter, and no sticky topic header
**And** a deep-link target id (from a tapped system notification) scrolls the feed to that card and triggers the Story 2.6 highlight
**And** the feed supports both an All mode and a per-topic mode (topic as an argument).

> **Feed RecyclerView serialization:** Stories 4.1 → 4.2 → 4.5 touch the same feed RV/adapter and must land in order. 4.5's `ItemTouchHelper` attaches to the RV without modifying the adapter.

### Story 4.2: Feed pagination & arrival animation wiring

As a user,
I want the feed to page in as I scroll and animate genuinely new cards,
So that long histories stay performant and new arrivals are noticeable.

**Acceptance Criteria:**

**Given** more than 20 messages (first page rendered by Story 4.1)
**When** I scroll to the bottom
**Then** the next 20 are appended (client-side infinite scroll)
**And** a page-load failure surfaces a retry affordance (not a silent empty), per Story 4.3 error states
**And** when a genuinely new message arrives, only that card plays the slide-in (Story 2.6), driven by newly-arrived ids (not row index), respecting reduced-motion
**And** an a11y live-region "new notifications" announcement fires on arrival.

### Story 4.3: Feed states (loading, empty, disconnected)

As a user,
I want clear loading, empty, and connection states,
So that the feed never looks broken and I can tell "nothing yet" from "something's wrong".

**Acceptance Criteria:**

**Given** the feed is loading
**When** no data is ready
**Then** ~5 skeleton cards (Story 2.6) are shown
**And** when the feed is empty, the first-run / no-messages panel renders (icon tile + Korean voice copy), with distinct copy for the all-feed vs a per-topic feed
**And** when the connection is lost / a page load fails, a distinct disconnected/error state is shown (calm empty ≠ anxious error) with a reconnecting message and, on page-load failure, a retry action
**And** the copy is **fixed string resources (no placeholders)** added to the Weblate-localized resources — initial Korean (해요체):
  - `empty_feed_all_title` = "아직 받은 알림이 없어요", `empty_feed_all_body` = "주제를 구독하면 첫 알림이 여기에 나타나요"
  - `empty_feed_topic` = "이 주제에는 아직 알림이 없어요"
  - `feed_state_disconnected` = "연결이 끊겼어요. 다시 연결하는 중…"

### Story 4.4: All vs per-topic feed topic chip

As a user,
I want to see which topic a card belongs to only when it's ambiguous,
So that the All feed is clear and per-topic feeds aren't redundant.

**Acceptance Criteria:**

**Given** the All-notifications feed
**When** cards render
**Then** each card shows its topic chip (binder passed a topic name)
**And** in a per-topic feed the topic chip is omitted (no topic name passed)
**And** no sticky per-topic header is shown in either mode.

### Story 4.5: Swipe to delete / mark read (FR6b)

As a user,
I want to swipe a card to delete or mark it read,
So that I can triage quickly with touch gestures.

**Acceptance Criteria:**

**Given** a card in the feed
**When** I swipe left past threshold
**Then** a coral (`priority_max`) delete backing is revealed and releasing opens the delete-confirm dialog
**And** swiping right past threshold (unread only) reveals an emerald (`accent_text`) mark-read backing that marks it read
**And** reveal width is 96 / snap threshold 72, implemented with `ItemTouchHelper`, and the colored backing layers are mounted only while swiping.

### Story 4.6: Navigation drawer & app bar (no bottom nav)

As a user,
I want a hamburger drawer to switch between All, topics, Subscribe, and Settings,
So that navigation is a single drawer with no bottom bar.

**Acceptance Criteria:**

**Given** the app shell
**When** it renders on a phone
**Then** a 56dp top app bar shows a hamburger (opens the drawer) and a center title (active topic name or "All notifications")
**And** the drawer lists, in order, All notifications · subscription rows · Subscribe to topic · Settings (`components.md §6`)
**And** there is **no** bottom navigation bar anywhere
**And** a publish FAB (`accent_ui` fill, `accent_on_surface` "+" icon, `shadow_elev_2`) is always present on the feed.

### Story 4.7: Drawer subscription rows & context menu

As a user,
I want each topic row to show its state and a context menu,
So that I can see unread/muted status and manage the subscription.

**Acceptance Criteria:**

**Given** a subscription row
**When** it renders
**Then** it shows `[active bar] [message/chat icon] [name] [unread count] [muted indicator?] [⋯]` per `components.md §6` (left icon is a chat bubble, **not** a bell)
**And** the active topic shows the `accent_ui` active bar + active icon color; unread count shows when `new > 0`, capped `99+`
**And** a muted topic shows a passive bell-off indicator (`role=img`, "Notifications muted") — an indicator, not a button; there is no standalone always-visible mute button
**And** the ⋯ menu offers, in order, Mute/Unmute · Rename · Clear · Unsubscribe
**And** selecting a topic row navigates to that topic's feed only — it never opens a per-message detail screen (Non-goal guard).

> **Drawer serialization:** Story 4.6 (drawer/app-bar scaffold) lands before Story 4.7 (topic rows + ⋯ menu); both touch the drawer layout.

### Story 4.8: Publish FAB bottom sheet

As a user,
I want a publish bottom sheet with priority chips,
So that I can send a message with the right fields, like web.

**Acceptance Criteria:**

**Given** the feed
**When** I tap the publish FAB
**Then** a bottom sheet "Publish a message" opens with fields top→bottom: Topic name, Title, Message (4-row), Priority, Tags (`components.md §9`)
**And** the Priority row shows four chips Low/Normal/High/Urgent (values 2/3/4/5, default Normal) where the **selected** chip is tinted per its priority and unselected chips show a `control_border` outline + muted
**And** a Close (ghost) and Send (primary fill) footer behaves as specified; inputs use `surface_2` bg + `control_border` + `radius_sm` + focus ring.

### Story 4.9: Optimistic send — pending / failed / retry card

As a user,
I want to see my just-sent message appear immediately and know if it failed,
So that I'm never left wondering whether my message actually went out.

**Acceptance Criteria:**

**Given** I tap Send in the publish sheet (Story 4.8)
**When** the publish request is in flight
**Then** an optimistic card appears at the top of the feed in a **pending** state (distinct sending indicator, no tap-to-read action), reusing the Epic 2 card shell
**And** on success the card resolves to a normal card in place
**And** on failure the card shows an **error** state (using the `priority_max`/`priority_urgent` token — no new token) with an inline **retry** action that re-sends
**And** the pending/failed variants render correctly in both light and dark themes.

---

## Coverage Check

_Updated after party-mode review #2 (2026-06-21): added Story 3.0 (golden corpus) and 4.9 (optimistic send); split 2.3→2.3a/2.3b and 3.6→3.6a/3.6b; pinned NFR5 / grep-gate / no-Compose to explicit ACs._

- **FRs:** FR1→2.1/2.2, FR2→2.3a/2.3b, FR3→2.4, FR4→3.1, FR5→3.2–3.7, FR6a→2.5, FR6b→4.5, FR7→4.1/4.6, FR8→4.1/4.2/4.3/4.4, FR9→4.6/4.7, FR10→4.8, FR11→1.3. ✅ all covered. (Optimistic send 4.9 is additional UX beyond the FR set.)
- **UX-DRs:** UX-DR1→1.1, UX-DR2→1.2, UX-DR3→2.x/3.x/4.3, UX-DR4→1.4/2.3a/2.6/4.7, UX-DR5→3.3/4.6. ✅ all covered.
- **NFRs:** NFR1→1.1/1.2 (+ CI grep gate in 1.1), NFR2→2.4/3.0/3.2/3.3, NFR3→3.5, NFR4→3.1/3.6b, **NFR5→3.3/3.4/3.6a/3.7 explicit no-clamp ACs**, NFR6→4.x, NFR7→2.4, NFR8→0.1/0.2/4.1, NFR9→3.8. ✅ all covered.
- **Parser parity (NFR2):** tag-hash golden vectors owned by Story 2.4 (Epic 2, self-contained); the icon-map/meter/dual-gate/markdown-scheme corpus owned by Story 3.0 (Epic 3), referenced by 3.2/3.3/3.6b/3.8. No cross-epic story dependency.
- **Non-goals protected:** no detail view (2.5 + 4.7 guards), no bottom nav (4.6), no per-card mute/overflow (2.3a), no compact/show-more (NFR5 ACs), no nested-sections recursion (3.7), no new tokens (1.1/1.2 + CI gate), re-skin/no-Compose (Epic 1 constraint).
- **Open Questions:** heuristic-kv in v1 ✅ (3.8); tablet rail out ✅ (phone-only, NFR6); rendering stack = re-skin/no-Compose ✅ (Epic 1 constraint AC).

**Story count: 32** across 5 epics (Epic 0: 2 · Epic 1: 4 · Epic 2: 7 · Epic 3: 10 · Epic 4: 9).
