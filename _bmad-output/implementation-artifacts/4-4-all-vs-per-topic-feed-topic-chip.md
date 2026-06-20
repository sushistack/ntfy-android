# Story 4.4: All vs Per-Topic Feed — Topic Chip Visibility

Status: ready-for-dev

## Story

As a user,
I want to see which topic a card belongs to only when it's ambiguous,
so that the All feed is informative and per-topic feeds aren't redundant.

## Acceptance Criteria

1. **Given** the All-notifications feed  
   **When** cards render  
   **Then** each card is bound with a non-null `topicName` argument so the topic chip is displayed in the tag row (FR3, FR8 — "the All feed shows each card's topic chip").

2. **Given** a per-topic feed  
   **When** cards render  
   **Then** each card is bound with a `null` `topicName` argument so no topic chip appears (FR3, FR8 — "the per-topic feed omits it").

3. **Given** the All feed and a per-topic feed rendered side-by-side in tests  
   **When** the binder receives a non-null vs null `topicName`  
   **Then** a binder/unit test asserts:  
   - Non-null topic → topic chip view is `VISIBLE` with the correct text  
   - Null topic → topic chip view is `GONE`  
   This is the sole testable delta for this story.

4. **Given** either feed mode  
   **When** the feed renders  
   **Then** there is **no** sticky per-topic section header or grouping header anywhere in the RecyclerView (Non-goal guard per epics).

5. **Given** the `MessageCardBinder.bind()` call site in any host  
   **When** it wires the `topicName` argument  
   **Then** the host (DetailAdapter or the future Epic 4 feed adapter) is the sole decider of which value to pass; the binder contains zero mode-detection logic and no subscription/topic lookup.

## Tasks / Subtasks

- [ ] Confirm `MessageCardBinder.bind(notification, topicName, selected)` signature is established by Story 2.1 (AC: 1–3, 5)
  - [ ] Read the merged `MessageCardBinder` to confirm it accepts `topicName: String?` and that the Story 2.4 tag row already uses it to conditionally show/hide the topic chip.
  - [ ] If the tag-row topic chip was not yet wired in Story 2.4 (possible if 2.4 landed without the Epic 4 caller), add the `VISIBLE`/`GONE` guard in the binder — one conditional on `topicName != null`.
  - [ ] Do not touch `fragment_detail_item.xml` for layout structural changes; only binder logic.
- [ ] Update `DetailAdapter` to always pass `null` for `topicName` (AC: 2, 5)
  - [ ] In `DetailAdapter.DetailViewHolder.bind()` (or its binder delegation), confirm `topicName = null` is already the default or explicitly set it.
  - [ ] This preserves current per-topic behavior in `DetailActivity` without any change in rendered output.
- [ ] Wire the All-feed caller to pass `topicName` (AC: 1, 5)
  - [ ] Identify the Epic 4 Story 4.1 feed adapter (expected: a new adapter/binder host for the single-feed `RecyclerView`).
  - [ ] In All-feed mode: pass the subscription/display name for each notification as `topicName` (look up from `notification.subscriptionId` or equivalent subscription join/display field).
  - [ ] In per-topic mode: pass `null`.
  - [ ] If the Epic 4 feed adapter does not yet exist (Stories 4.1–4.3 not merged), document the expected call-site contract in a code comment and add a `// TODO(4.4)` marker so the 4.1 adapter wires it on merge.
- [ ] Guard: no sticky header (AC: 4)
  - [ ] Confirm no `StickyHeaderDecoration`, section-header `itemViewType`, or grouped header is added to the RecyclerView in this story.
  - [ ] If any exists from prior Epic 4 stories, remove it as part of this story.
- [ ] Add focused tests (AC: 3)
  - [ ] Add a binder unit test (or extend an existing one from Story 2.4): bind a `Notification` with `topicName = "alerts"` → assert topic chip is `VISIBLE` and its text equals `"alerts"`; bind same with `topicName = null` → assert chip is `GONE`.
  - [ ] Add a host test: in `DetailAdapter` the binder is always called with `null` topic name (regression guard).

## Dev Notes

### Dependency Gate

This story assumes:
- **Story 2.1** introduced `MessageCardBinder` with a `topicName: String?` parameter in `bind()`.
- **Story 2.4** placed the topic chip view in the tag row, conditionally shown when `topicName != null`.
- **Stories 4.1–4.3** introduced the single-feed `RecyclerView` and its adapter/host.

If any of these are not yet merged, implement this story's changes against the expected API contracts (see Story 2.1 dev notes for binder boundary) and leave marked `TODO(4.x)` comments at call sites. Do not re-implement binder seams that belong to other stories.

### Minimal-Scope Contract

Story 4.4 is intentionally the **thinnest** Epic 4 story. It delivers exactly one behavioral variable: whether `topicName` is null or not at the `bind()` call site. There is no layout work, no new token, no animation, no state machine. Any deviation from this scope is a defect.

Files likely touched:
- `ui/MessageCardBinder.kt` — possibly add/confirm the topic-chip guard (one conditional)
- `ui/DetailAdapter.kt` — confirm `null` topic name passthrough (likely already correct from Story 2.1)
- The Epic 4 single-feed adapter (name TBD by Story 4.1, likely `FeedAdapter.kt` or similar) — pass non-null topic name for All feed, null for per-topic

Files that must **not** be modified:
- `fragment_detail_item.xml` — shell is Story 2.1-owned; no structural changes here
- Any delivery/service/`msg/`/`db/` layer file — this is view binding only

### Resolving the Topic Display Name

The `Notification` row stores `subscriptionId: Long` (foreign key to `Subscription`). The display topic name for a notification in the All feed must come from a joined or pre-fetched subscription name:

- Option A (preferred): the Epic 4 feed ViewModel or repository query already joins and provides `Notification` + subscription display name together (e.g., via a data class or wrapper). Pass the display name directly.
- Option B (fallback): look up in an in-memory map `subscriptionId → displayName` in the adapter, populated from the ViewModel's subscription list `LiveData`.
- Do **not** perform a DAO lookup per-bind. Keep all IO off the main thread; bind only pre-fetched data.

The `Subscription.displayName` field (or `topic` if no display name is set) is the correct source per existing app conventions. Check `MainAdapter` (subscription list) for the precedent of how subscriptions resolve display names.

### Topic Chip Visual Spec (from Story 2.4)

From `components.md §3`, the topic chip is:
- Emerald pill, labeled with the topic/subscription name
- Background: `@color/accent_ui` (emerald, the `topic_chip` variant)
- Text: `@color/accent_on_surface`
- Shown first in the tag-row ordering (before `service:` and general tags)
- **Only** shown when a topic name is passed (`topicName != null`)

This story does not change the chip's visual style — only whether it appears.

### No-Header Invariant

The epics file explicitly states: "no sticky topic header" in both All and per-topic modes (FR8 and Story 4.1 AC). Verify:
- No `ConcatAdapter` with a header adapter is present
- No `addItemDecoration` with a sticky-header decorator is called on the feed RecyclerView
- No `viewType` branching for header rows in the feed adapter

### Architecture Compliance

- Stay in the existing View/XML + AppCompat/RecyclerView stack; no Compose, no navigation destination changes.
- The binder receives `topicName: String?` from the host; it never reads a `ViewModel`, a `Repository`, or a subscription table. Feed-mode logic lives in the host/adapter only.
- No raw hex, no new color resources — chip styling is established by Story 2.4 using tokens from Story 1.1.

### Existing Files and Preservation Requirements

- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state: passes `null` (or no) topic name to the binder (per-topic feed only, always hides topic chip).
  - Change: confirm/explicitly pass `null` — no functional change from user perspective.
  - Preserve: selection, click/long-click, action-mode, all existing card behaviors.

- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt` (created by Story 2.1, topic chip added by Story 2.4)
  - Current state: accepts `topicName: String?`; Story 2.4 conditions the topic chip `VISIBLE`/`GONE` on it.
  - Change: none if 2.4 already wired the conditional. If not, add the one-line guard.
  - Preserve: all other rendering logic, selection, child event isolation, reduced-motion hooks.

- Epic 4 feed adapter (Story 4.1, name TBD)
  - Change: pass `subscription.displayName ?: subscription.topic` when in All-feed mode; `null` when in per-topic mode.
  - Preserve: pagination, sequenceId ordering, arrival animation wiring, all existing behaviors.

### Testing Requirements

- A binder test (Robolectric or AndroidX Test in `app/src/test/` or `app/src/androidTest/`) covering:
  - `topicName = "server-alerts"` → chip is `VISIBLE`, chip text is `"server-alerts"`
  - `topicName = null` → chip is `GONE`
- A `DetailAdapter` regression test: confirm that binding via `DetailAdapter` always supplies `null` topic name (guards against accidental All-feed logic leaking into the per-topic host).
- Reuse the test infrastructure established by Stories 2.1/2.4; do not add new test dependencies.
- No manual smoke check is sufficient alone — the conditional must be covered by automated assertions.

### Project Structure Notes

- No new files are strictly required; this story's implementation may be a one-line conditional in `MessageCardBinder` + a caller update.
- Tests extend existing test packages under `app/src/test/java/io/heckel/ntfy/` or `app/src/androidTest/java/io/heckel/ntfy/`.
- No `strings.xml` changes, no migration, no new layouts, no token additions.

### Previous Story Intelligence

- Story 2.1 established the `topicName: String?` binder parameter and the rule that the host is the sole decider of what to pass (binder contains no mode-detection). This story enforces that contract at the call sites.
- Story 2.4 established the topic chip visual contract (emerald pill, first in tag-row order, `GONE` when no topic name). This story's only behavioral contribution is ensuring the right callers pass non-null vs null.
- Stories 4.1–4.3 built the single-feed surface, pagination, and container states. 4.4 adds the All-vs-per-topic binder argument wiring on top of that completed surface.
- Story 2.2 guidance: always reset binder state on `bind()`; ensure the chip is explicitly set `VISIBLE` or `GONE` on every bind to prevent RecyclerView reuse leakage.

### Git Intelligence

- No Epic 4 implementation has landed yet. This story may be the first to exercise the All-feed code path. If Stories 4.1–4.3 are not yet merged, add call-site comments and complete the binder guard defensively.
- The working tree contains the sprint-status YAML and story markdown artifacts — no production code has changed yet. All story files are reference context only.
- Recent commits are documentation-only (specs, UI parity reference docs). Confirm that no source code dependencies have been introduced before implementing.

### Latest Technical Information

No web research needed. This story uses only stable, already-pinned Android View/RecyclerView APIs and the project-local binder boundary established by Story 2.1. Do not upgrade libraries.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 4, Story 4.4 AC + FR3/FR8 no-sticky-header rule]
- [Source: `_bmad-output/planning-artifacts/epics.md` — FR Coverage Map (FR8 "All feed shows topic chip; per-topic omits it")]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md` — `MessageCardBinder` signature, topicName boundary, AC 5 host-reuse contract]
- [Source: `_bmad-output/implementation-artifacts/2-4-categorized-tag-row-timestamp.md` — topic chip visual spec, VISIBLE/GONE guard, tag-row ordering]
- [Source: `docs/ui-parity/components.md` §3 — tag row: topic chip emerald pill, shown only on All feed]
- [Source: `docs/ui-parity/screens-layout.md` — All vs per-topic feed differences, no sticky header]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — current per-topic host, null topic name passthrough]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/MainAdapter.kt` — subscription display name resolution precedent]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Python 3.11 resolver unavailable; customization resolved manually from `customize.toml` (no team/user overrides found).
- Loaded: `epics.md`, Stories 2.1, 2.4, 2.5, `DetailAdapter.kt`, `fragment_detail_item.xml`, sprint-status.yaml.
- No PRD/architecture/UX standalone files; project uses SPEC + companion model (epics.md documents this).
- No `project-context.md` found at `{project-root}/**`.
- No Epic 4 implementation has landed; story is written to handle either the 4.1–4.3 code being present or not.

### Completion Notes List

- Ultimate context engine analysis completed — comprehensive developer guide created.
- Story scope is intentionally minimal (single binder argument, one conditional); all guardrails focus on preventing scope creep and RecyclerView bind-reset regressions.
- Call-site wiring for All-feed mode (subscription display name resolution) is described with two options; Option A is preferred.

### File List

- `_bmad-output/implementation-artifacts/4-4-all-vs-per-topic-feed-topic-chip.md`
