---
baseline_commit: c216fdb4668cc8f069c7f15adc33b10986d579ed
---

# Story 3.3: `kv` Block Renderer

Status: in-progress

## Story

As a user,
I want key-value monitor cards with icons, status, and meters,
so that system and metric payloads render as a structured table.

## Acceptance Criteria

1. **Given** a parsed `kv` card spec  
   **When** its rows render in the card body  
   **Then** every row is presented left-to-right as `[leading icon] [key (muted)] [value] [meter?]`  
   **And** the renderer mounts its content only inside Story 2.1's `@id/card_body` host without changing the card shell, header, meta row, attachments, actions, or card interactions.

2. **Given** a row with an optional `icon` and required `key`  
   **When** the leading icon is resolved  
   **Then** the lookup input is `icon` when present, otherwise `key`, lowercased with locale-independent rules  
   **And** lookup order is exact value, then first whitespace-delimited word, then the fallback glyph `·`  
   **And** the glyph result matches the full canonical Story 3.0 golden corpus and `message-format.md` §4 map byte-for-byte.

3. **Given** a row whose `status` is `error`  
   **When** its value renders  
   **Then** the value uses `@color/priority_urgent`  
   **And** `ok`, `warn`, missing, or unknown status values leave the value at the normal `@color/text` color.

4. **Given** a row with `status` and no finite meter value  
   **When** it renders  
   **Then** a small filled status dot precedes the value: `ok` uses `@color/accent_ui`, `warn` uses `@color/priority_high`, and `error` uses `@color/priority_max`  
   **And** missing or unknown status renders no dot  
   **And** any finite meter suppresses the status dot, even when status is also present.

5. **Given** a row's `meter` field  
   **When** it is a finite JSON number  
   **Then** the shared Story 3.2 meter component renders after the value, using its clamp, thresholds, tokens, and accessibility semantics  
   **And** absent, null, non-number, NaN, or infinite values render no meter and do not crash  
   **And** this story does not duplicate meter threshold or drawing logic.

6. **Given** a `kv` spec with `columns: 2`  
   **When** the actual available width of the mounted body is at least the project-defined approximately-600dp breakpoint  
   **Then** rows render in two ordered columns, with each row—including its meter—contained within its own grid cell  
   **And** below that breakpoint, or for missing/unsupported `columns`, rows render in one full-width column  
   **And** the decision is based on available body width, not orientation, device category, or global screen width, and updates safely if that width changes.

7. **Given** any number of valid rows, including a long key/value and a long row set  
   **When** the block renders  
   **Then** every row and complete value remains present with no `maxLines`, ellipsize, compact mode, “show more,” or body-row truncation  
   **And** row order matches the payload order in both one- and two-column modes.

8. **Given** malformed or partially malformed row content  
   **When** the renderer is invoked through Story 3.1's safe dispatch boundary  
   **Then** the card never crashes, stale children from a recycled bind are removed, and the boundary can fall back to the raw message string as defined by Story 3.1  
   **And** rebinding a body host from `kv` to another body type or raw text leaves no old kv rows, meters, dots, listeners, or accessibility metadata.

## Tasks / Subtasks

- [ ] Consume the structured-card model and renderer contract from Stories 3.0–3.2 (AC: 1, 2, 5, 8)
  - [ ] Use the existing `KvSpec`/row model or JSON adapter produced by Story 3.1; do not parse the notification body again in the renderer.
  - [ ] Reuse the Story 3.0 icon-map fixture/source and Story 3.2 meter API rather than copying parity constants.
  - [ ] Keep `parseCardSpec`, top-level type dispatch, heuristic detection, and raw fallback owned by Story 3.1/3.8.
- [ ] Implement the reusable View/XML kv renderer (AC: 1–8)
  - [ ] Add a focused renderer class under `io.heckel.ntfy.ui.message` (or the structured-body package established by Story 3.1).
  - [ ] Add `view_card_kv.xml` and a reusable row layout only if it improves clarity; use token resources exclusively.
  - [ ] Define a narrow API that accepts a parsed kv spec and a body `ViewGroup`, returns/owns only its mounted body view, and has no Activity, adapter, repository, coroutine, or navigation dependency.
  - [ ] Clear or replace renderer-owned children deterministically before every render.
- [ ] Implement parity-exact icon resolution (AC: 2)
  - [ ] Normalize with `Locale.ROOT`.
  - [ ] Preserve the exact canonical glyphs and aliases.
  - [ ] Cover exact match, first-word match, explicit-icon override, casing, whitespace, unknown key, and fallback.
- [ ] Implement row status presentation (AC: 3, 4)
  - [ ] Reset value color and dot visibility/color on every bind or row reuse.
  - [ ] Ensure finite meter presence suppresses the dot while `status:"error"` still colors the value coral.
  - [ ] Keep dots decorative for accessibility unless the established renderer contract supplies a meaningful combined row description.
- [ ] Integrate the shared meter component (AC: 5)
  - [ ] Pass only finite numeric values to Story 3.2.
  - [ ] Let the meter own clamping, threshold colors, dimensions, and meter semantics.
  - [ ] Ensure meter layout consumes remaining cell width without forcing the key/value off-screen.
- [ ] Implement responsive one/two-column layout (AC: 6, 7)
  - [ ] Use the measured `card_body`/kv container width converted against display density and a named breakpoint resource.
  - [ ] Preserve payload order with deterministic row-major placement.
  - [ ] Recompute without duplicating children or losing state when width crosses the breakpoint.
  - [ ] Keep each meter inside its row's grid cell; never span both columns.
- [ ] Add focused automated tests (AC: 1–8)
  - [ ] Run Story 3.0 golden vectors against icon resolution.
  - [ ] Assert status/value/dot behavior for `ok`, `warn`, `error`, missing, and unknown values, with and without meters.
  - [ ] Assert finite-only meter delegation and no duplicated threshold implementation.
  - [ ] Assert widths below/at/above the breakpoint, row order, per-cell meter containment, and width-change relayout.
  - [ ] Assert long values/all rows render without clamp or “show more.”
  - [ ] Assert host reuse sequences (`kv`→`kv`, `kv`→raw, `kv`→other structured type) leave no stale views.
  - [ ] Assert malformed row input reaches safe fallback/no-crash behavior through the Story 3.1 integration test.

## Dev Notes

### Dependency and Ownership Gates

- Story 3.3 is the first user-visible structured payload renderer and ships with enablers 3.0–3.2. Implement or merge those stories first:
  - 3.0 owns the shared golden corpus and canonical icon-map vectors.
  - 3.1 owns tag+JSON+known-type detection, parsed card models, renderer dispatch, try/catch, and raw fallback.
  - 3.2 owns the inline meter component, finite/clamp handling, threshold colors, height/radius, and accessibility semantics.
- At story-creation time, Stories 3.0–3.2 have no story artifacts or production implementation in the working tree. Do not silently invent incompatible parallel APIs; create/consume them in sequence and adapt this story to their final package and contracts.
- Epic 2's generated story artifacts define the target `MessageCardBinder` and `@id/card_body`, but their production code is also not yet present. Merge Epic 2 prerequisites before integration.
- Story 3.3 owns kv body presentation only. It does not own list/chart/markdown/sections rendering, heuristic-kv parsing, shell styling, tags, title, delete, mark-read, attachments, or actions.

### Canonical Data Contract

The wire payload is a JSON object:

```json
{
  "type": "kv",
  "columns": 2,
  "rows": [
    {"key": "CPU", "value": "4.86%", "meter": 4.86},
    {"key": "Load Avg", "value": "0.11 0.12 0.18", "status": "ok"},
    {"key": "Agent", "value": "0.18.7", "icon": "agent"},
    {"key": "Disk", "value": "95%", "status": "error", "meter": 95}
  ]
}
```

- `key` and `value` are display strings in the canonical explicit schema.
- `status` recognizes `ok`, `warn`, and `error`; treat other values as no semantic status.
- `meter` renders only for a finite numeric value. JSON itself cannot encode NaN/Infinity, but the parsed model/renderer API should still defend against non-finite programmatic values.
- `columns` defaults to one. Only integer value `2` requests the responsive two-column mode.
- Rendering must preserve row order and full content.

### Icon Resolution

Use one shared resolver and one canonical map:

| Keys | Glyph | Keys | Glyph |
|---|---:|---|---:|
| `cpu` | `⚙` | `version` | `#` |
| `disk` | `💾` | `exit` | `⏎` |
| `memory`, `mem`, `ram` | `🧠` | `net`, `network` | `⇅` |
| `load` | `📈` | `services`, `service` | `❏` |
| `uptime` | `⏱` | `agent` | `▶` |
| `status`, `name` | `●` | `host` | `🖥` |
| `error` | `✕` | `ping` | `◎` |
| `warning` | `⚠` | `speed` | `▶` |
| `temp`, `temperature` | `🌡` | fallback | `·` |

Resolution algorithm:

1. Choose non-null `icon`, otherwise `key`.
2. Lowercase with `Locale.ROOT`.
3. Try the complete normalized string.
4. If absent, try its first whitespace-delimited word.
5. Return `·`.

Do not substitute Android drawable icons or emoji aliases. These exact monospace glyphs are parity-critical.

### View and Layout Guidance

- Stay in Views/XML + AppCompat; do not introduce Compose, RecyclerView-within-card, or a new rendering/layout dependency.
- A small renderer class plus `GridLayout`/custom body `ViewGroup` is sufficient. Android's platform `GridLayout` supports a mutable column count on the project's min SDK; use the simplest implementation that preserves equal usable cells and row order.
- Base responsive switching on the body's actual measured width. A foldable, split window, drawer/inset, or future feed constraint can make screen width differ from usable card width.
- Avoid a permanent layout listener that accumulates across recycled binds. If a listener is needed before first measurement, remove or replace it deterministically and only rebuild when the effective column count changes.
- Two-column placement is row-major: payload rows 0/1 share the first visual row, 2/3 the next. Each row's content and meter remain in that cell.
- Values and keys may wrap. Do not use `singleLine`, `maxLines`, or ellipsize in the body.
- Use `@dimen/text_mono`/`@font/font_mono` for glyphs where available, `@dimen/text_body_sm` for row text, `@color/muted` for keys, and `@color/text` for default values. Use named spacing/dimension resources from Story 1.2; no raw hex or px.

### Integration with `MessageCardBinder`

- Expected Story 2.1/3.1 path:
  1. Binder resets body presentation.
  2. Story 3.1 parses once and dispatches a parsed `KvSpec`.
  3. Kv renderer mounts `view_card_kv.xml` under `card_body`.
  4. Renderer creates/binds all rows and delegates finite meters to Story 3.2.
- The renderer must not edit `fragment_detail_item.xml`; Story 2.1 is the sole owner of the stable shell/body-slot contract.
- Structured-body rendering must not remove or relocate attachment and action surfaces. Follow the final Story 2.1 hierarchy: replace only the normal message-body presentation owned by the dispatch seam, while preserving non-body content and callbacks.
- RecyclerView safety is non-negotiable: reset container children, value colors, dot state, meter state, and pending measurement listeners on every bind.

### Existing Files and Preservation Requirements

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current state: legacy monolithic rounded row with `detail_item_message_text`; no `card_body`.
  - Expected before 3.3: Epic 2's squared shell and stable `@id/card_body`.
  - Preserve: shell/header/meta/accent, click and long-click behavior, attachment/icon/action surfaces, selection, and accessibility state.
  - Story 3.3 change: none. Do not modify this shell file.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state: nested Activity-coupled holder performs all plain/Markdown and side-effect binding.
  - Expected before 3.3: delegates reusable card binding to `MessageCardBinder`.
  - Preserve: list/diff/selection responsibilities and host callbacks.
  - Story 3.3 change: normally none beyond minimal integration if the final Story 3.1 seam requires it.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Expected from Story 2.1 and updated by Story 3.1.
  - Change: dispatch/invoke the kv renderer through the existing parsed-body seam; do not place kv row-building logic directly in the binder.
  - Preserve: adapter independence and complete reset behavior.
- Expected NEW production files, following the package convention established by Story 3.1:
  - `app/src/main/java/io/heckel/ntfy/ui/message/KvBlockRenderer.kt`
  - `app/src/main/res/layout/view_card_kv.xml`
  - optionally `app/src/main/res/layout/view_card_kv_row.xml`
  - focused tests and only genuinely missing named dimensions.
- Expected consumed files from prerequisite stories:
  - parsed `CardSpec.Kv`/`KvRow` model and dispatch contract from 3.1,
  - icon golden corpus/shared fixture from 3.0,
  - meter view/binder from 3.2.

### Architecture Compliance

- Production UI remains under `io.heckel.ntfy.ui`; use a focused `ui.message` subpackage consistently if Story 3.1 establishes it.
- The renderer is a pure presentation component. It must not reference `Activity`, `DetailActivity`, `DetailAdapter`, `Repository`, Room, coroutines, navigation, notification services, or network code.
- Continue using the project's existing stack: Kotlin/JVM 17, min SDK 26, AppCompat 1.7.1, ConstraintLayout 2.2.1, Material 1.13.0, RecyclerView 1.4.0, and Gson already pinned by the project.
- Do not add Compose or any third-party grid, table, meter, or structured-card library.
- Do not upgrade Gson as part of this story. The checked-in build currently uses Gson 2.13.2; parsing architecture belongs to Story 3.1, and this renderer should receive typed data.

### Testing Requirements

- There is currently no checked-in `app/src/test` or `app/src/androidTest` suite. Follow the test structure introduced by Stories 3.0–3.2 rather than creating a competing harness.
- Prefer pure JVM tests for:
  - icon normalization/resolution,
  - status presentation decisions,
  - finite-meter delegation decisions,
  - requested column count and deterministic row placement.
- Use Robolectric or instrumentation only where measured width, inflation, resource resolution, GridLayout cells, and view recycling must be asserted.
- Required boundary examples:
  - exact icons: `CPU`, `memory`, `TEMP`;
  - first-word icons: `Load Avg`, `Network RX`;
  - explicit override: key `Whatever`, icon `agent`;
  - fallback: unknown/blank lookup → `·`;
  - `error` with no meter: coral value + coral dot;
  - `error` with meter: coral value + meter, no dot;
  - `warn` with no meter: normal value + amber dot;
  - finite meter values including out-of-range values delegate to the shared meter;
  - below, exactly at, and above the breakpoint;
  - odd row count in two columns;
  - long/multiline values and a large row list;
  - repeated rebind and width-crossing sequences with no duplicate/stale children.
- Manual smoke test in light and dark themes on a phone-width host and a width ≥600dp host, including TalkBack inspection of row reading order and the shared meter semantics.

### Previous Story Intelligence

- Story 2.1 establishes that structured renderers mount `view_card_<type>.xml` inside `card_body` and never edit the shared shell.
- Stories 2.2 and 2.6 emphasize complete reset on every RecyclerView bind; apply the same rule to dynamically added kv rows, colors, dots, meters, and measurement listeners.
- Epic 2 keeps repository/lifecycle/host behavior outside `MessageCardBinder`; this renderer must be even narrower and presentation-only.
- Story 2.4 filters the `card` marker from chips. Do not repeat tag filtering in this renderer.
- The current worktree contains user-owned generated artifacts and a modified sprint status file. Preserve all unrelated content and formatting.

### Git Intelligence

- Recent commits (`5e3972d6`, `a4d9b073`) add the planning artifacts and canonical web-parity references; no structured-card production code has landed.
- The checked-in production row remains the legacy `DetailAdapter`/`fragment_detail_item.xml` implementation. The generated Epic 2 stories describe future prerequisite seams, so implementation order matters more than current file shape.
- No library addition is indicated by recent history or the canonical architecture decision.

### Latest Technical Information

- Official Android APIs provide `GridLayout.setColumnCount(...)` and measured-width lifecycle hooks on all supported project API levels, so no layout dependency is needed.
- Gson 2.14.0 exists upstream as of story creation, while the project pins 2.13.2. This story must not opportunistically upgrade it; no renderer requirement depends on the newer release.
- Keep external technical facts subordinate to the checked-in canonical parity contract, which is the source of truth for glyphs, statuses, responsive behavior, and layout.

### Project Structure Notes

- The architecture's intended naming is `view_card_<type>.xml`; use `view_card_kv.xml`.
- If Story 3.1 chooses a different focused package/model naming scheme, follow it consistently rather than introducing parallel `structured`, `card`, and `message` packages.
- Add only dimensions that are absent after Stories 1.1/1.2/3.2. The approximately-600dp responsive breakpoint should be one named resource/constant shared by renderer and tests.
- Scope excludes edits to database, delivery, notification service, manifest, navigation, feed pagination, shell layout, or localization beyond any accessibility text genuinely required by the established renderer contract.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Stories 3.0–3.3, NFR2/NFR5, UX-DR3/UX-DR5]
- [Source: `docs/ui-parity/message-format.md` §1, §2.1, §4, §6, §7]
- [Source: `docs/ui-parity/components.md` §1 and §4]
- [Source: `docs/ui-parity/design-tokens.md` — color, typography, spacing, and radius tokens]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `_bmad-output/implementation-artifacts/2-2-priority-accent-bar-all-five-priorities.md`]
- [Source: `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/build.gradle`]
- [Source: Android Developers `GridLayout` and `View` API references]
- [Source: Google Gson releases]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- The customization resolver required Python 3.11; customization was manually resolved from the base TOML with no team/user overrides.
- Loaded the complete consolidated epics artifact, canonical message/component/token companions, prior Epic 2 story context, relevant current code, build dependencies, recent git history, and current sprint status.
- Parallel artifact, codebase, and prior-story investigations were used to challenge file ownership and dependency assumptions.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Parser/meter ownership, parity-exact icon resolution, actual-width responsive layout, full-content behavior, and RecyclerView cleanup are explicit.

### File List

- `_bmad-output/implementation-artifacts/3-3-kv-block-renderer.md`
