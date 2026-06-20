---
id: SPEC-ui-parity
companions:
  - brownfield.md
  - ../../../docs/ui-parity/design-tokens.md
  - ../../../docs/ui-parity/components.md
  - ../../../docs/ui-parity/message-format.md
  - ../../../docs/ui-parity/screens-layout.md
  - ../../../docs/ui-parity/CHANGELOG-redesign-2026-06.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. The `docs/ui-parity/*` companions are the authoritative reference catalogs (tokens, component anatomy, message protocol, screen layout); this SPEC carries the kernel that points into them. Where any companion disagrees with a screenshot, the companion wins; where the companion disagrees with the running ntfy-web app, the web app wins.

# ntfy Android — UI Parity & Structured Message Rendering

## Why

The ntfy **web** client went through a 2026-06 redesign (squared notification cards, all-priority accent bars/badges, categorized tag rows, a single-feed navigation model with no detail screen, and a new **structured-message protocol** that smuggles JSON into the message body to render key-value tables / lists / charts / mixed sections). The ntfy **Android** app — a brownfield View/XML app (`io.heckel.ntfy`) whose current shell predates the redesign (subscription-list `MainActivity` + per-topic `DetailActivity`, plain-text message rows) — is now out of parity. The goal is **1:1 visual and behavioral parity** with the redesigned web client so the same message looks and behaves identically on both platforms, and so rich monitoring/CI-style payloads render as real components instead of raw text. This is a vision-to-realize (a unified cross-platform product surface) plus a capability-to-capture (structured cards). It matters now because the web redesign has shipped and Android is the lagging surface.

## Capabilities

- id: CAP-1
  intent: Android exposes the full ntfy-web design-token set (color, type, spacing, radius, elevation) as platform resources so all UI is built against token keys, not literals.
  success: Every token in `design-tokens.md` exists as the snake_case Android resource named there (e.g. `@color/accent_text`, `@dimen/spacing_4`), with correct light + dark values; a grep of new UI code finds no raw hex/px except the documented literal tag-palette/service colors.

- id: CAP-2
  intent: The notification card renders as the redesigned "product" surface — squared, with a 4dp left priority accent bar colored for all five priorities (gray/white/amber/coral, glow on high/max in dark).
  success: A card of each priority 1–5 matches the bar color/glow table in `components.md §1`; corners are fully squared (no radius); resting elevation is `shadow_elev_1`.

- id: CAP-3
  intent: Every card header shows a priority badge (Min/Low/Normal/High/Urgent), the title (falling back to the body string when titleless), an unread dot when new, and an X delete button that opens a delete-confirm dialog.
  success: Badge appears on all priorities with the colors/labels in `components.md §2`; X-delete triggers the confirm dialog; the old per-card bell and ⋯ overflow are absent; unread dot shows only when `new == 1`.

- id: CAP-4
  intent: The card meta row renders a categorized tag set (topic emerald · `service:` slate-blue · general hash-colored, max 2 + "+N more") and a right-aligned timestamp.
  success: Tag colors match the fixed/hash rules in `components.md §3`; the 32-bit hash produces the same palette index as the web pseudocode for a given name; the `card` marker and emoji-shortcode tags are excluded; timestamp reads `YYYY-MM-DD HH:mm:ss` local and is always right-aligned.

- id: CAP-5
  intent: A message tagged `card` whose body is valid JSON of type `kv`/`list`/`chart`/`sections` renders as a structured card; everything else falls back to the markdown/plain-text path.
  success: `parseCardSpec` accepts only (tag `card` AND parseable JSON AND known `type`) per `message-format.md §1`; invalid payloads fall back without crashing; the four block types render per §2 (kv icons/status/meter/columns:2, list ordered/bulleted, chart bar/line, sections ordered mix); a card-tagged message renders identically to web.

- id: CAP-6
  intent: Structured rendering matches web's parsing details exactly — icon map, status/meter thresholds, chart constraints, and markdown security rules.
  success: kv leading icons resolve via the §4 map (exact → first word → `·`); meter thresholds are `<65`/`≥65`/`≥90`; charts are hand-drawn (no chart lib), 120dp tall, ≤60 points, labels only when ≤12; markdown linkifies only http/https/mailto and drops unsafe image schemes; a malformed payload shows raw text, never crashes.

- id: CAP-7
  intent: The card is the complete, final view of a message — tapping marks it read (no navigation), and touch swipe reveals delete (coral) / mark-read (emerald) backings.
  success: There is no detail screen/route/Activity; tap clears the unread dot only; left/right swipe past threshold reveals the correct colored backing and action per `components.md §1`; backing layers mount only while swiping.

- id: CAP-8
  intent: The app shell is a single feed surface plus a settings surface, switched via a hamburger navigation drawer, with a publish FAB always present.
  success: Navigation is feed (All / per-topic) + settings + left drawer only; no bottom navigation bar exists; the emerald FAB is present on the feed and opens the publish sheet per `screens-layout.md`.

- id: CAP-9
  intent: The feed lists full notification cards newest-first by server sequence, with correct spacing, per-arrival animation, pagination, and feed states.
  success: Order is `sequenceId` descending; card gap is 18dp; only genuinely newly-arrived cards play the 0.25s slide-in (reduced-motion respected); 20-per-page infinite scroll; loading shows skeletons, empty shows the first-run/no-messages panel; the All feed shows each card's topic chip and per-topic feed omits it; no sticky topic header.

- id: CAP-10
  intent: The navigation drawer lists All notifications, subscription rows, Subscribe, and Settings, with each topic row showing active bar, unread count, muted indicator, and a ⋯ menu.
  success: Drawer content/order matches `components.md §6`; topic row left icon is a message/chat icon (not a bell); muted topics show a passive bell-off indicator; the ⋯ menu offers Mute/Unmute · Rename · Clear · Unsubscribe; there is no standalone always-visible mute button.

- id: CAP-11
  intent: The publish sheet collects topic/title/message/priority/tags, with all four priority chips showing a selected tint.
  success: Mobile presents a bottom sheet with the fields in `components.md §9`; the four priority chips (Low/Normal/High/Urgent, default Normal) each tint when selected; Close/Send footer behaves as specified.

- id: CAP-12
  intent: The app supports light and dark themes with dark as the default/hero, selectable in settings.
  success: Both themes render from tokens with no pure black/white canvas (dark `#0C0D0F`, light `#F3F4F6`); Appearance settings offer a Light/Dark/System segmented control defaulting to dark.

## Constraints

- Build against the token resource keys in `design-tokens.md`; never hard-code hex/px except the documented literal general-tag palette and service-tag color (used as-is in both themes).
- The ntfy-web repo (`src/styles/tokens.css`, `src/components/`) and the running web app are the ultimate source of truth; the `docs/ui-parity/*` companions win over the `screenshots/` (which are partially stale post-redesign).
- The structured-card parser must match web byte-for-byte where parity is observable: the 32-bit unsigned tag-hash, the kv icon map, meter thresholds (`<65`/`≥65`/`≥90`), and the dual gate for card detection (tag `card` AND valid JSON AND known `type`).
- No external chart library — the chart is a thin hand-drawn Canvas/Compose-Canvas bar/line in emerald accent.
- Markdown/structured rendering is security-hardened and fault-tolerant: only `http`/`https`/`mailto` links are live, unsafe-scheme images are dropped, no `javascript:`/data URLs, and any malformed payload degrades to the raw message string inside a try/catch — it must never crash the card.
- The card renders **full content** — no compact/preview mode, no "show more", no "+N more" on card bodies.
- Phone-first: only the mobile column (top bar + full-width feed + drawer + FAB) is required.
- Timestamps are `YYYY-MM-DD HH:mm:ss` in local time, locale-independent, always right-aligned.
- Feed ordering is `sequenceId` descending (server sequence), never wall-clock time.
- The `card`-tagged JSON path is mandatory for parity; the untagged `key: value` heuristic-kv fallback is optional for the first release.

## Non-goals

- No message detail view/pane, `/:topic/:msgId` detail route, or detail Activity/Fragment — the card is the full view.
- No bottom navigation bar.
- No per-card mute bell or card "⋯" overflow menu (header is X-delete only).
- No card compact/preview mode or "show more" affordance.
- No tablet/desktop multi-pane or detail-pane layout; a tablet/foldable icon-rail is optional, not required.
- No redesign of Settings functionality beyond applying the shared tokens/shell (settings behavior is unchanged from the current app).
- No new design tokens beyond those in `design-tokens.md`; no light-theme variant of the literal tag colors yet.
- No recursion into nested `sections` (a nested `sections` block is ignored).

## Success signal

The same ntfy message — whether plain text, untagged `key: value` lines, or a `card`-tagged JSON payload (kv/list/chart/sections) — placed side by side on Android and the redesigned ntfy-web client is visually and behaviorally indistinguishable: identical squared card with the correct priority bar and badge, hash-stable tag colors, structured blocks (meters, status dots, charts, mixed sections), `YYYY-MM-DD HH:mm:ss` timestamp, and tap-to-mark-read with no detail screen anywhere in the app.

## Assumptions

- This is a brownfield rework of the existing `io.heckel.ntfy` Android app (View/XML, AppCompat — no Compose today); the parity work restructures that app rather than starting a new one. See `brownfield.md`.
- The app keeps its existing localization pipeline (Weblate); new strings (e.g. `notification_card_badge_min|low|normal|high|max`, empty-state Korean voice copy) are added as localizable resources.
- "Identical to web" is judged against the current running ntfy-web app and the `docs/ui-parity/*` companions, not the stale screenshots.

## Open Questions

- Heuristic-kv fallback (untagged `key: value` → kv table): ship in the first release or defer? (`message-format.md §6` says explicit-path-only is acceptable to start.)
- Is a tablet/foldable icon-rail layout wanted at all, or is phone-only the committed scope?
- Rendering stack for the new card surface — incrementally re-skin the existing Views/`DetailAdapter`, or introduce Compose for the card/structured renderer? (HOW decision deferred to architecture, but it gates story sizing.)
