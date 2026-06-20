# Story 2.3b: X-Delete with Token-Styled Confirm Dialog

Status: ready-for-dev

## Story

As a user,
I want an X button that asks before deleting,
so that I can remove a notification safely without accidental loss.

## Acceptance Criteria

1. **Given** a bound notification card header, **when** it renders, **then** it exposes one trailing X delete button using the redesigned header contract; the control has a localized accessibility label, a practical minimum 48dp touch target, and no old per-card bell or card overflow action is introduced.
2. **Given** the X button, **when** it is hovered, focused, or pressed, **then** its destructive state is visibly sourced from `@color/priority_max` and its icon/foreground remains legible in light and dark themes; no raw color, new danger token, or Compose implementation is added.
3. **Given** the X button, **when** the user taps it, **then** a small Material delete-confirm dialog opens with localized message, Cancel, and Delete actions, styled through the Story 1.1/1.2 token-backed app theme so dark-default, surface, text, radius, and `shadow_elev_2` behavior resolve correctly.
4. **Given** the confirmation dialog, **when** the user chooses Delete, **then** the host invokes deletion for that notification ID exactly once through the existing repository deletion path, the deleted row disappears through the observed data source, and no Activity navigation or unrelated card action occurs.
5. **Given** the confirmation dialog, **when** the user chooses Cancel, dismisses with Back, taps outside when allowed, or the host is recreated before confirmation, **then** the notification remains untouched and no delete callback is invoked.
6. **Given** a card with whole-card click, long-click, links, attachment controls, and selection behavior, **when** the X is tapped, **then** the event is consumed by the X action and does not trigger card click/mark-read, link opening, clipboard copy, selection, or attachment actions.
7. **Given** a recycled holder, **when** another notification is bound, **then** the X listener and dialog request target the newly bound notification only; a stale notification ID cannot be deleted.
8. **Given** Story 4.5 later adds swipe-to-delete, **when** it requests confirmation, **then** it can reuse the same host-owned confirmation/deletion contract as the X button rather than duplicating dialog strings, token styling, or repository mutation.

## Tasks / Subtasks

- [ ] Add the X control to the reusable card header contract (AC: 1, 2, 6, 7)
  - [ ] Add or finalize a stable `card_delete_button` ID in the header area of `fragment_detail_item.xml`, preserving the shell/body ownership established by Story 2.1 and the badge/title/unread layout established by Story 2.3a.
  - [ ] Use an existing X/close vector where suitable, or add a token-tinted close vector; do not use the trash-can icon or retain the old `detail_item_menu_button`.
  - [ ] Provide a localized content description and a minimum 48dp interactive target without distorting the visual icon size.
  - [ ] Define token-backed selector/tint/scale behavior for default, focus, hover, and press states; destructive interaction uses `priority_max`.
  - [ ] Ensure the button is independently focusable/clickable and consumes its interaction before the card surface.
- [ ] Extend the adapter-agnostic binder action boundary (AC: 4, 6, 7)
  - [ ] Add a narrow `onDeleteRequested(notification)` or notification-ID callback to `MessageCardActions`; do not inject `Repository`, `Activity`, `FragmentManager`, or a coroutine scope into `MessageCardBinder`.
  - [ ] Replace the delete listener on every bind and clear/reset it as part of recycled-state initialization.
  - [ ] Keep the binder responsible only for emitting the request; it must not create/show a dialog or mutate Room.
- [ ] Add one reusable host-owned delete confirmation flow (AC: 3–5, 8)
  - [ ] Add a focused `NotificationDeleteConfirmation` presenter/helper or equivalent UI-layer contract that accepts a lifecycle-safe host context and a confirmation callback.
  - [ ] Build the dialog with `MaterialAlertDialogBuilder` and token-backed theme/style resources; use `priority_max` for the destructive Delete action instead of the legacy `DangerText`/Material error alias.
  - [ ] Use dedicated localizable strings for “Delete this notification?”, “Cancel”, “Delete”, and the X accessibility label; do not reuse subscription-delete or multi-delete copy.
  - [ ] Guard the confirmation callback so one dialog confirmation produces one deletion request.
  - [ ] Make the presenter reusable by Story 4.5 swipe-delete without depending on `DetailAdapter`.
- [ ] Wire current-host deletion without coupling the binder (AC: 4–6)
  - [ ] Have `DetailActivity` supply the delete-request callback when constructing the adapter/binder.
  - [ ] On confirmed Delete, launch `repository.markAsDeleted(notification.id)` on the existing lifecycle-owned IO path.
  - [ ] Do not add immediate swipe semantics, undo Snackbar behavior, tap-to-read behavior, navigation changes, or physical database removal in this story.
  - [ ] Preserve current attachment deletion, subscription deletion, clear-all, multi-select deletion, and action-mode behavior.
- [ ] Add focused regression tests (AC: 1–8)
  - [ ] Test that X tap opens confirmation and does not invoke card click/long-click/link/selection actions.
  - [ ] Test Delete invokes the correct ID exactly once; Cancel, Back, outside-dismiss, and recreation invoke it zero times.
  - [ ] Test holder recycling by binding A then B and confirming that only B can be requested/deleted.
  - [ ] Test light/night resource resolution and assert the destructive state/action uses `priority_max`, with no raw hex or new error token.
  - [ ] Test accessibility label, focusability, and minimum touch-target size.
  - [ ] Add a contract test proving the shared confirmation flow can be called independently of `DetailAdapter`, ready for Story 4.5.
  - [ ] Run focused tests, `check`, and Play/F-Droid debug resource processing/assembly.

## Dev Notes

### Current State

- `fragment_detail_item.xml` currently contains `detail_item_menu_button`, an overflow button used only for attachment/click-link actions. Story 2.3a removes the redesigned card-header overflow; Story 2.3b replaces the header affordance with a single X. Do not accidentally remove attachment/file controls that still live elsewhere in the card body.
- `DetailAdapter.DetailViewHolder` currently binds whole-card click/long-click behavior and recreates listeners per notification. Story 2.1 is expected to extract this into `MessageCardBinder`; implement against that binder boundary if present rather than adding fresh Activity coupling to the legacy holder.
- `DetailActivity` already owns lifecycle, repository access, and several Material confirmation dialogs. Its current single-row swipe immediately calls `repository.markAsDeleted(notification.id)` and offers Undo; that behavior is legacy and Story 4.5 owns its replacement. Do not route the X through the existing immediate-swipe callback.
- `Repository.markAsDeleted(notificationId)` delegates to the Room DAO's soft-delete update. This is the correct existing mutation path. Do not physically delete the row, attachment, icon, or subscription.
- Existing `dangerButton()` applies `R.style.DangerText`, which resolves through the Material error attribute. The parity contract explicitly reuses `priority_max`; add a focused token-backed destructive action style/helper or tint this dialog's positive action from `R.color.priority_max`.

### Required Interaction Boundary

The reusable card must emit intent; the host owns confirmation and side effects. A suitable shape is:

```kotlin
interface MessageCardActions {
    fun onDeleteRequested(notification: Notification)
    // Existing card/body actions remain here or in adjacent narrow callbacks.
}
```

Host flow:

```text
X click → binder emits delete request
        → host opens shared confirmation
        → Delete: lifecycleScope(IO) → repository.markAsDeleted(id)
        → Cancel/dismiss: no mutation
```

- Capture the currently bound immutable `notification.id` when assigning the listener. Never look up a holder's adapter position after the dialog opens; list updates can invalidate it.
- The binder must not hold an `Activity`, `Dialog`, `Repository`, `FragmentManager`, or lifecycle scope.
- The dialog helper must not know about `DetailAdapter`; Story 4.5 will invoke the same confirmation from an `ItemTouchHelper` host.
- Do not optimistically hide the row before confirmation. The Room-observed list removes it after the soft-delete update.

### Dialog and Token Contract

- Use `MaterialAlertDialogBuilder` so the dialog follows `AppTheme` and night-qualified resources.
- Dialog surface/radius/elevation come from the token-backed dialog theme: `surface`, `text`/`muted`, `radius_md`, and `shadow_elev_2`. If Stories 1.1/1.2 have not landed, sequence the work; do not duplicate literals.
- Destructive action and X interactive state use `priority_max`; `priority_urgent` remains available for other error semantics but is not the specified X hover/press token.
- Add dedicated English source strings in `values/strings.xml` for the Weblate pipeline. Do not manually fan out edits to every translated file; preserve normal localization workflow.
- Android touch UI has no persistent desktop hover requirement, but `state_hovered`, `state_focused`, and `state_pressed` should all receive an explicit token-backed state for mouse/trackpad, keyboard/D-pad, and touch parity. Keep any scale effect subtle and disabled or harmless for accessibility; color feedback is the required behavior.

### Files to Update

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current state: legacy header has an overflow button; Story 2.1/2.3a may have already restructured the shell/header.
  - Change: add/finalize stable X delete control and remove the old header overflow affordance.
  - Preserve: `card_body`, priority accent, badge/title/unread content, attachment/body controls, whole-card semantics outside the X target.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Current state: created by Story 2.1; adapter-agnostic binding boundary.
  - Change: bind the X to a narrow delete-request callback and reset it safely on reuse.
  - Preserve: no Activity/adapter/repository ownership and all existing body interactions.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state: legacy adapter currently owns binding; after Story 2.1 it delegates to the binder.
  - Change: thread the host-provided delete action into the binder only as required.
  - Preserve: diffing, selection bookkeeping, current list responsibilities.
- `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt`
  - Current state: host owns repository/lifecycle and existing clear/subscription/multi-delete dialogs.
  - Change: handle per-notification delete requests, show shared confirmation, and soft-delete on confirmed action.
  - Preserve: current list observation, selection/action mode, attachment behavior, and unrelated dialogs.
- `app/src/main/res/values/strings.xml`
  - Change: add dedicated localizable notification-delete dialog/action/accessibility strings.
- Token/theme/style/drawable resource files introduced by Stories 1.1/1.2, as required for the X selector and dialog style.

### Files to Add

- A small reusable UI-layer confirmation presenter/helper, suggested location:
  - `app/src/main/java/io/heckel/ntfy/ui/NotificationDeleteConfirmation.kt`
- A state-list tint/background/animator resource for the X control if existing Material state APIs cannot express the required token states cleanly.
- Focused unit/Robolectric/instrumentation tests under the repository's chosen test source sets.

### Scope Guardrails

- Views/XML + AppCompat + Material only; no Compose, new dialog framework, or dependency upgrade.
- This story does not implement Story 2.3a badge/title/unread content, Story 2.5 tap-to-read, Story 4.5 swipe backings/thresholds, or Epic 4 navigation changes.
- Do not reuse subscription deletion strings such as `detail_delete_dialog_*`; those describe unsubscribe + all notifications and are semantically dangerous here.
- Do not use the multi-delete strings (`detail_action_mode_delete_dialog_*`) for a single-card action.
- Do not silently keep the old card overflow in the redesigned header. If body attachment actions still need a menu, retain/rehome that body-specific affordance without presenting it as the removed card-level ⋯ control.
- Preserve min SDK 26, target/compile SDK 36, and both `play`/`fdroid` flavors.

### Testing Requirements

- Prefer behavioral assertions over screenshots: callback counts/IDs, dialog visibility/actions, listener consumption, recycled binding, and Room-observed removal.
- Verify all negative paths: Cancel, Back, outside dismissal, configuration recreation, double-tap/rapid confirmation, and stale holder binding.
- Verify X accessibility independently from the whole-card semantics: localized content description, focusable control, and minimum target.
- Verify the dialog and control in light and night resource configurations.
- Verify no raw hex/px, new danger token, direct DAO access, GlobalScope, or Activity reference is introduced into the binder/helper boundary.
- Existing legacy swipe behavior may remain until Story 4.5, but X tests must prove it does not share the immediate-delete/Undo path.

### Previous Story Intelligence

- Story 2.1 defines `fragment_detail_item.xml` as the permanent reusable shell and `MessageCardBinder` as the adapter-agnostic boundary. Keep repository mutation and lifecycle ownership outside the binder.
- Story 2.1 requires explicit recycled-state reset and preservation of attachment/action/link/selection behavior; the X listener is part of that reset contract.
- Stories 1.1 and 1.2 define `priority_max`, token-backed dimensions/elevation, and the no-raw-color verification gate. Consume them; do not create a parallel visual system.
- Story 2.3a owns badge/title/unread content and removal of the old header bell/overflow. This story owns only the X behavior and confirmation path, while integrating into the same header.

### Git Intelligence

- Recent commits add the preservation-validated SPEC, epic breakdown, and UI-parity companions; no card implementation or delete-confirm abstraction has landed in git.
- Existing code establishes `MaterialAlertDialogBuilder`, lifecycle-owned repository mutation, and `dangerButton()` as patterns, but the new parity contract overrides the destructive color choice from Material error to `priority_max`.
- The worktree contains user-owned uncommitted story artifacts. Do not overwrite or reformat unrelated files.

### Latest Technical Information

- No external web research is required for this story. It adds no library or platform API, and the checked-in parity contract plus pinned project stack are authoritative.
- Reuse the currently pinned AppCompat 1.7.1, Material 1.13.0, RecyclerView 1.4.0, and lifecycle/coroutine stack. Do not upgrade dependencies as part of the delete interaction.

### Project Structure Notes

- Keep reusable card behavior under `app/src/main/java/io/heckel/ntfy/ui/` alongside `MessageCardBinder`.
- Keep the confirmation presenter host-agnostic enough for both the current `DetailActivity` and future Epic 4 feed host.
- Keep localizable source copy in `app/src/main/res/values/strings.xml`; translations flow through Weblate.
- If prerequisite stories rename or split the header layout, preserve the stable semantic control ID and binder/action contracts rather than reopening shell/body ownership.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 2, Story 2.3b, merge gating]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-3, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack, current card surface, controls to remove]
- [Source: `docs/ui-parity/components.md` — §1 Notification Card: Header, Interaction, Delete-confirm dialog]
- [Source: `docs/ui-parity/design-tokens.md` — Color Tokens, Radius Tokens, Shadow/Elevation Tokens]
- [Source: `docs/ui-parity/CHANGELOG-redesign-2026-06.md` — Removed per-card bell/overflow]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `_bmad-output/implementation-artifacts/1-1-color-token-resources-light-dark.md`]
- [Source: `_bmad-output/implementation-artifacts/1-2-non-color-token-resources-literal-tag-palettes.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` — `loadView()`, existing Material confirmation dialogs]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — `markAsDeleted()`]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Util.kt` — `dangerButton()`]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/src/main/res/values/strings.xml`]
- [Source: `app/build.gradle`]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; base/team/user customization was resolved manually.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- The story preserves the adapter-agnostic binder boundary and makes confirmation reusable by future swipe-delete.
- Deletion remains the existing Room soft-delete operation and occurs only after explicit confirmation.
- Implementation is ready subject to the Story 1.1/1.2 token resources and Story 2.1/2.3a header/binder prerequisites.

### File List

- `_bmad-output/implementation-artifacts/2-3b-x-delete-with-token-styled-confirm-dialog.md`
