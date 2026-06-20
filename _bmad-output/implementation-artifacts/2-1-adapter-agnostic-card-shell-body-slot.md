# Story 2.1: Adapter-Agnostic Card Shell + Body Slot

Status: ready-for-dev

## Story

As a developer,
I want a squared card layout and a standalone `MessageCardBinder`/ViewHolder decoupled from any Activity or adapter,
so that both the current notification list and the future single feed reuse the identical card without rework.

## Acceptance Criteria

1. **Given** `fragment_detail_item.xml`, **when** a notification row is inflated, **then** its outer card container is fully squared in light and dark themes, uses `@color/surface`, a 1dp border using `@color/border`, resting `shadow_elev_1`, clipped/hidden overflow, and no compatibility padding or corner treatment that visually rounds or expands the shell.
2. **Given** keyboard, D-pad, switch-access, or screen-reader focus, **when** the card receives focus, **then** the whole card is one focusable/clickable target and displays the shared 2dp `@color/focus_ring` focus indicator without obscuring the 1dp border.
3. **Given** the shell layout, **when** later card stories inspect it, **then** it contains a stable `@+id/card_priority_accent` placeholder spanning the full card height at 4dp width and a stable `@+id/card_body` `ViewGroup` for all message-body content.
4. **Given** the existing notification row capabilities, **when** the shell is introduced, **then** current plain/Markdown message rendering, title/date, unread state, icon, attachment preview/file controls, action buttons, selection state, click, long-click, and link behavior continue to work; this story does not remove or redesign those behaviors.
5. **Given** a new standalone `MessageCardBinder`, **when** `DetailAdapter` binds a row, **then** the binder accepts a `Notification` plus nullable topic/display-name input and explicit interaction/dependency callbacks, and it has no field, constructor parameter, import, cast, or runtime lookup for `DetailActivity`, `Activity`, `DetailAdapter`, or a specific RecyclerView host.
6. **Given** the current `DetailAdapter`, **when** a holder is created and bound, **then** the adapter delegates row rendering to the standalone binder instead of retaining the existing monolithic binding implementation, while selection bookkeeping and adapter/list responsibilities remain in `DetailAdapter`.
7. **Given** future Epic 3 renderers, **when** they add structured bodies, **then** they mount their own `view_card_<type>.xml` content inside `@id/card_body`; they do not edit `fragment_detail_item.xml`. This story is the sole Epic 2/3 owner of the shell layout contract.
8. **Given** Story 2.1 scope, **when** it is complete, **then** the accent placeholder remains visually neutral, no priority badge/new header/delete/tag redesign is implemented, and no loading skeleton, arrival animation, deep-link highlight, swipe action, or Compose dependency is added.

## Tasks / Subtasks

- [ ] Establish the reusable shell contract in `fragment_detail_item.xml` (AC: 1–4, 7, 8)
  - [ ] Replace the rounded/compat-padded card treatment with a squared, clipped shell using token resources.
  - [ ] Add the full-height 4dp `card_priority_accent` placeholder without assigning priority-specific color or glow.
  - [ ] Add `card_body` as the single body-content host and move the current message/attachment/action subtree into or under that host so existing content still renders.
  - [ ] Preserve stable legacy child IDs where practical; document and update every binder reference for IDs that must change.
  - [ ] Apply the shared focus-indicator primitive from Story 1.4 and ensure click/focus semantics belong to the outer card only.
- [ ] Extract an adapter-agnostic binder (AC: 4–6)
  - [ ] Add `MessageCardBinder` under `io.heckel.ntfy.ui` (or a focused `ui.message` package if introduced consistently).
  - [ ] Move row-view lookup, bind/reset logic, Markdown/plain text handling, attachments, icons, action buttons, selection styling, and listener wiring out of `DetailAdapter.DetailViewHolder`.
  - [ ] Pass capabilities through narrow interfaces/callbacks rather than storing `Activity`, `DetailActivity`, `DetailAdapter`, `Repository`, or a lifecycle scope in the binder.
  - [ ] Use `itemView.context` for resource inflation/resolution and activity launching only where the preserved legacy behavior requires a `Context`.
  - [ ] Ensure recycled rows reset visibility, listeners, dynamic action children, movement method, image state, and selected/unselected styling before applying the next notification.
- [ ] Reduce `DetailAdapter` to adapter responsibilities (AC: 5, 6)
  - [ ] Keep `ListAdapter`, diffing, selected-ID bookkeeping, `get()`, and selection notifications in `DetailAdapter`.
  - [ ] Have the holder own or invoke `MessageCardBinder` and pass the current notification, nullable topic/display name, selected state, and callbacks.
  - [ ] Preserve the existing `DetailActivity` call site and current click/long-click behavior; do not implement Story 2.5 tap-to-mark-read semantics here.
- [ ] Add regression and contract tests (AC: 1–8)
  - [ ] Add a layout/binder test proving `card_body` is a `ViewGroup`, `card_priority_accent` exists at 4dp/full height, card radius is zero, and required token resources are used.
  - [ ] Add binder tests for title present/absent, plain and Markdown text, unread state, selection state, icon absent/present, attachment absent/present, and action-list recycling.
  - [ ] Add an architecture guard test/static check that fails if `MessageCardBinder` references `Activity`, `DetailActivity`, `DetailAdapter`, or Compose.
  - [ ] Add a host-reuse test that inflates and binds the shell from a plain `ViewGroup`/test context without constructing `DetailActivity`.
  - [ ] Run focused tests plus Play and F-Droid debug resource processing/assembly.

## Dev Notes

### Current State

- `DetailAdapter` is a `ListAdapter<Notification, DetailViewHolder>`, but its nested holder currently owns nearly the entire row implementation: view lookup, text/Markdown rendering, links, priority icon, unread dot, selection color, attachments, icon preview, popup menus, dynamic actions, repository writes, coroutine work, and external intents.
- The holder constructor currently takes an `Activity`, `CoroutineScope`, `Repository`, `Markwon`, selected IDs, and click/long-click lambdas. This directly prevents reuse by the future Epic 4 feed.
- `fragment_detail_item.xml` is one large `CardView`/`ConstraintLayout` with a 3dp corner radius, compat padding, hard-coded margins/padding, and no body host or accent-bar placeholder.
- The existing body is not only `detail_item_message_text`: attachment image/file UI and action buttons are part of the message presentation and must remain functional during extraction.
- `DetailActivity` currently constructs `DetailAdapter(this, lifecycleScope, repository, onNotificationClick, onNotificationLongClick)` and submits the per-topic list. Keep this integration working; the navigation/feed replacement is Epic 4.

### Required Binder Boundary

`MessageCardBinder` is a reusable view binder, not another adapter. Its public API should make host dependencies explicit. A suitable shape is:

```kotlin
class MessageCardBinder(
    itemView: View,
    private val bodyRenderer: MessageBodyRenderer,
    private val actions: MessageCardActions,
) {
    fun bind(
        notification: Notification,
        topicName: String?,
        selected: Boolean,
    )
}
```

The exact names may follow project conventions, but preserve these boundaries:

- `Notification` is the persisted row model currently rendered by `DetailAdapter`; do not substitute the network `msg.Message`.
- `topicName` is nullable. The current per-topic list may pass `null`; Epic 4 All-feed mode will pass a display/topic name for the topic chip.
- Host actions and side effects are callback/interface dependencies. The binder must not discover its host with `context as Activity`, `findViewTreeLifecycleOwner`, or adapter references.
- Markdown setup may be injected as a renderer/function or constructed from `itemView.context`; do not require an Activity solely for Markwon.
- Repository mutation and coroutine ownership should remain outside the reusable view layer. If legacy attachment operations cannot be cleanly moved in this story, expose narrow callbacks from the host rather than retaining `Repository`/`CoroutineScope` fields.

### Shell and Body Ownership

- `fragment_detail_item.xml` is the permanent card shell owned by Story 2.1.
- Required stable IDs:
  - `detail_item_card` (outer card; retain for compatibility unless a compelling reason requires migration)
  - `card_priority_accent` (4dp, full height; neutral placeholder in 2.1)
  - `card_body` (`ViewGroup`; structured renderers mount here)
- Place all current message body content that must scroll/render as part of the notification inside the body region, including attachment and action surfaces. Header/meta restructuring belongs to Stories 2.3a/2.3b/2.4; avoid prematurely freezing their final child hierarchy.
- Epic 3 must add separate `view_card_kv.xml`, `view_card_list.xml`, `view_card_chart.xml`, `view_card_sections.xml`, and text/Markdown body layouts as needed and inflate them into `card_body`. It must never reopen the shell XML.
- Android Views clip children only when the relevant parent has `clipChildren`/`clipToPadding` enabled and the background itself is square. Verify the actual rendered hierarchy; setting `CardView` radius to zero alone is insufficient if compat padding remains.

### Architecture Compliance and Scope Guardrails

- Continue with Views/XML, AppCompat, Material, RecyclerView, ConstraintLayout, Markwon, Glide, and existing utilities. Do not introduce Jetpack Compose, a new card framework, or a rendering dependency.
- Consume Story 1.1 color tokens (`surface`, `border`, `focus_ring`) and Story 1.2/1.4 non-color/elevation/focus primitives. If those prerequisite resources are not merged, sequence the work; do not duplicate or hard-code their values.
- Treat the web/component documents as the visual contract, but this story implements only the shell seam. Priority colors/glows are Story 2.2; badge/header/delete are 2.3a/2.3b; tags/timestamp are 2.4; tap-to-read is 2.5; skeleton/animation/highlight is 2.6.
- Do not delete legacy attachment menus, action buttons, link interaction, long-press selection, or notification icons merely because they are absent from Story 2.1 ACs. Later product stories may explicitly replace them; until then they are regression requirements.
- No raw hex or ad-hoc px/dp values outside documented resources. The two shell-specific dimensions not already represented by canonical spacing tokens are the specified 1dp border and 4dp accent width; define named dimensions if Story 1.2 does not already provide suitable keys.
- Preserve min SDK 26 and both `play`/`fdroid` flavors.

### Files to Update

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current state: monolithic rounded `CardView` row with no reusable body slot.
  - Change: squared token-backed shell, full-height neutral accent placeholder, named body host, existing body behavior retained.
  - Preserve: inflation by `DetailAdapter`, legacy content IDs/behaviors needed during staged migration, attachment/action surfaces, whole-card interaction.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state: nested holder contains all row rendering and side effects.
  - Change: delegate rendering to `MessageCardBinder`; keep list/diff/selection/holder orchestration.
  - Preserve: `TopicDiffCallback`, selection API, current Activity call site, click/long-click outcomes.
- `app/src/main/res/values/dimens.xml` and token/style/drawable resources as required
  - Current state: only `fab_margin` is defined in `dimens.xml`; existing card shape is hard-coded in XML.
  - Change: add named shell dimensions/styles/drawables only where prerequisite token stories do not already own them.
  - Preserve: existing resources and Material theme aliases.

### Files to Add

- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
- Narrow binder dependency interfaces/helpers if needed, kept in the same focused UI package.
- Focused JVM/Robolectric or instrumentation tests under the existing Android test source sets. The repository currently has no discovered `app/src/test` or `app/src/androidTest` suite, so add only the minimum test dependencies needed by the chosen approach.

### Testing Requirements

- Test recycling explicitly. The current row has many conditional views and dynamic children; stale content is the highest-risk regression after extraction.
- Assert resource IDs, dimensions, radius/elevation, and token references programmatically where possible; screenshots alone are insufficient.
- Verify light and night resource resolution for `surface`, `border`, and focus state.
- Verify the binder compiles and binds from a generic parent/context with fake callbacks and without any Activity instance.
- Verify the current `DetailActivity` list still renders and selection/click/long-click/link behavior remains intact.
- No loading-state or animation test belongs in this story.

### Git Intelligence

- The latest commits add the preservation-validated SPEC, Epic breakdown, and UI parity companions; they do not establish a prior card-refactor implementation pattern.
- Older history for `DetailAdapter`/`fragment_detail_item.xml` shows incremental attachment, action, link, and image-preview behavior. This reinforces extraction-with-preservation rather than replacing the row wholesale.
- The worktree already contains user-owned planning/story changes. Do not overwrite or reformat unrelated artifacts.

### Latest Technical Information

- No external web research is required for implementation choices: the project pins the relevant View stack in `app/build.gradle`, and this story adds no library or API. The checked-in SPEC and companions are the authoritative product contract.
- Current project versions relevant to this story include AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0, Material 1.13.0, Markwon 4.6.2, and Glide 5.0.5. Reuse them; do not upgrade dependencies inside this extraction story.

### Project Structure Notes

- Keep production UI code under `app/src/main/java/io/heckel/ntfy/ui/`.
- Keep the shared shell at `app/src/main/res/layout/fragment_detail_item.xml` so current and future adapters inflate the same resource.
- Future body layouts use `app/src/main/res/layout/view_card_<type>.xml`; do not place structured renderer XML inside the shell.
- There is no project-level `project-context.md`; the canonical context is the SPEC kernel plus companions listed below.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 2, Story 2.1 and dependency flow]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-2, CAP-7, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack, Navigation gap, Carries over]
- [Source: `docs/ui-parity/components.md` — §1 Notification Card]
- [Source: `docs/ui-parity/design-tokens.md` — Color Tokens, Shadow/Elevation Tokens, Spacing Scale]
- [Source: `docs/ui-parity/screens-layout.md` — Navigation model, Feed]
- [Source: `docs/ui-parity/CHANGELOG-redesign-2026-06.md` — Removed, Changed]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` — `loadView()` adapter integration]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` — `Notification` entity]
- [Source: `app/build.gradle`]
- [Source: `_bmad-output/implementation-artifacts/1-1-color-token-resources-light-dark.md`]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; customization was resolved manually from base/team/user TOML.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story 2.1 is the first story in Epic 2; previous-story intelligence is not applicable.
- The shell extraction explicitly preserves current row behavior and creates the stable body-slot seam required by Epics 3 and 4.
- Implementation is ready subject to the Story 1.1/1.2/1.4 token, elevation, and focus-resource prerequisites.

### File List

- `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`
