# Story 2.5: Tap to Mark Read

Status: ready-for-dev

## Story

As a user,
I want tapping a card to mark it read,
so that I clear new items without navigating anywhere (there is no detail view).

## Acceptance Criteria

1. **Given** an unread persisted notification (`notificationId != 0`, the Android equivalent of web `new == 1`)  
   **When** the user taps the non-interactive card surface  
   **Then** exactly one ID-scoped mark-read request is issued for that notification  
   **And** the persisted row is updated to `notificationId = 0`  
   **And** the observed list rebinds the card with the unread dot hidden.

2. **Given** a read notification (`notificationId == 0`)  
   **When** the user taps the non-interactive card surface  
   **Then** no repository/DAO write is issued.

3. **Given** any card tap  
   **When** tap-to-read handling runs  
   **Then** it does not open `notification.click`, copy the message, start an Activity, navigate to a route/detail screen, or otherwise perform the legacy `DetailActivity.onNotificationClick` behavior.

4. **Given** the card contains an interactive child  
   **When** the user taps the X-delete button, a tag or `+N more` control, a link, attachment surface, or message action button  
   **Then** that child performs only its own action  
   **And** the card does not mark the notification read from the same gesture.

5. **Given** selection/action mode is active  
   **When** the user taps a card  
   **Then** existing selection behavior remains available and the tap does not mark the notification read.

6. **Given** a recycled holder or a rapid repeated tap before the observable DB update returns  
   **When** binding/tap handling occurs  
   **Then** listeners reference only the currently bound notification  
   **And** the same unread notification cannot enqueue duplicate mark-read requests while one is in flight.

## Tasks / Subtasks

- [ ] Add an ID-scoped persistence operation (AC: 1–2)
  - [ ] Add `NotificationDao.markAsRead(notificationId: String)` using `UPDATE notification SET notificationId = 0 WHERE id = :notificationId AND notificationId != 0`.
  - [ ] Expose the operation through `Repository.markAsRead(notificationId: String)`.
  - [ ] Do not use `markAllAsRead()` or `markAsReadBySequenceId()` for card taps.
- [ ] Make tap-to-read an explicit binder action (AC: 1–6)
  - [ ] Extend Story 2.1's `MessageCardActions`/callback boundary with an ID- or notification-scoped `onMarkRead` action.
  - [ ] Keep repository and coroutine ownership in the host; `MessageCardBinder` must remain adapter/Activity agnostic.
  - [ ] Bind the outer card/non-interactive surface to invoke the callback only when the currently bound notification is unread.
  - [ ] Guard duplicate dispatch until the row is rebound read; clear holder-local pending state when a different ID is bound.
- [ ] Replace legacy primary-card tap behavior (AC: 3, 5)
  - [ ] In the current `DetailActivity` host, preserve action-mode selection as the first branch.
  - [ ] Outside action mode, route the card tap only to the mark-read callback.
  - [ ] Remove card-tap URL opening and clipboard-copy behavior; links and explicit action buttons remain independently interactive.
- [ ] Isolate child interactions (AC: 4)
  - [ ] Ensure X-delete and Story 2.4 tag/expander listeners consume their gesture without invoking the outer card listener.
  - [ ] Preserve link, attachment, and action-button handlers as child-owned actions with no tap-to-read fallthrough.
  - [ ] Do not make the whole `card_body` independently clickable; use the outer card listener plus normal Android child event dispatch.
- [ ] Add focused automated tests (AC: 1–6)
  - [ ] DAO test: unread ID becomes read; a different row, including one sharing a sequence ID, is unchanged.
  - [ ] Binder/host tests: unread tap dispatches once, read tap dispatches zero times, rapid double tap dispatches once, rebind targets the new ID.
  - [ ] Interaction tests: X, tag/expander, link, attachment, and action button do not dispatch mark-read.
  - [ ] Host test: normal card tap neither starts an Activity nor copies content; action-mode tap selects without marking read.

## Dev Notes

### Dependency Gate

- This story assumes Stories 2.1–2.4 and 2.3b have established the final shell, `MessageCardBinder`, unread dot, X-delete, and tag controls. They are not implemented in the current working tree; merge or implement them first and adapt to their final APIs.
- Reuse Story 2.1's callback/interface seam. Do not move `Repository`, `CoroutineScope`, `Activity`, or navigation knowledge into the binder.
- Epic 2 remains release-gated until Stories 2.1–2.4 (including 2.3b) ship together.

### Developer Context

- The persisted `Notification` model has no Boolean `new` field. Existing Android code treats `notificationId != 0` as unread and `notificationId == 0` as read; Story 2.3a's unread dot must use the same source of truth.
- `NotificationDao` currently supports `markAllAsRead(subscriptionId)` and `markAsReadBySequenceId(subscriptionId, sequenceId)`, but neither is the correct contract for `markAsRead(<id>)`. Add the narrow ID update to prevent another row with the same sequence ID from being changed.
- `Repository.getNotificationsLiveData()` observes a Room `Flow`; the DB update should naturally submit a new list through `ListAdapter`, whose content comparison sees the changed `notificationId`, causing the unread dot to clear. Do not manually hide the dot as the sole state update.
- Current `DetailActivity.onNotificationClick()` opens `notification.click` or copies message text. This story intentionally replaces that whole-card behavior. Explicit links/actions continue to work through their own child listeners.
- Database writes must run off the main thread using the host's existing lifecycle-aware coroutine scope. Do not add `GlobalScope`.

### Architecture Compliance

- Stay in the existing View/XML + AppCompat/RecyclerView/Room stack; do not introduce Compose or a navigation/detail destination.
- The binder emits intent; the host performs persistence. Keep `MessageCardBinder` free of `Activity`, `DetailActivity`, `DetailAdapter`, `Repository`, and coroutine fields.
- Use notification `id` for a card-tap write. `sequenceId` is an update-group/server concept and is not guaranteed to identify exactly one persisted card.
- Keep the operation idempotent at both layers: SQL includes `notificationId != 0`, and UI dispatch suppresses duplicate in-flight taps.
- Preserve long-press selection and action-mode semantics. Selection mode supersedes mark-read.
- Pending/optimistic cards introduced by Story 4.9 have no tap-to-read action; design the callback as nullable/disabled so that future host can opt out without binder changes.

### Existing Files and Preservation Requirements

- `app/src/main/java/io/heckel/ntfy/db/Database.kt`
  - Current state: `Notification.notificationId` stores unread/read state; DAO has only all-by-subscription and by-sequence read updates.
  - Change: add a conditional, ID-scoped single-row mark-read query.
  - Preserve: entity schema and database version; this story requires no migration because it adds only a DAO method.
- `app/src/main/java/io/heckel/ntfy/db/Repository.kt`
  - Current state: exposes `markAllAsRead` and `markAsReadBySequenceId`.
  - Change: expose the new ID-scoped operation without changing existing receive/user-action behavior.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Expected from Story 2.1; primary interaction update.
  - Change: emit mark-read only for unread, non-interactive card taps; reset bound ID/pending state on every bind.
  - Preserve: child controls, body rendering, long-click, selection, attachments, actions, and adapter independence.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state before 2.1: forwards outer-card and message-text clicks to a generic Activity callback.
  - Expected after 2.1: holder delegates to the binder and passes explicit actions.
  - Change: pass mark-read/selection actions; do not reintroduce persistence into the holder.
- `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt`
  - Current state: `onNotificationClick()` selects in action mode, otherwise opens `click` URL or copies message text.
  - Change: selection remains; normal card taps launch an IO mark-read write only when unread.
  - Preserve: explicit link/action/attachment behaviors and long-press action-mode entry.
- `app/src/main/res/layout/fragment_detail_item.xml`
  - Expected final shell from Stories 2.1–2.4.
  - Normally no structural change is needed. Verify nested interactive children are clickable/focusable and do not delegate to the card.

### Testing Requirements

- Add the project's first focused Room/interaction tests if no suitable test source set exists; use existing Android test dependencies before adding new libraries.
- The exact-once assertion must cover rapid taps, not only SQL idempotency: two DAO calls that happen to produce one final state still violate the AC.
- Test RecyclerView reuse by binding unread A, rebinding unread B, then tapping and asserting only B is dispatched.
- Test an unread-to-read Room emission or equivalent adapter content update so the unread dot clearing is state-driven.
- Include a regression check that an ID-scoped update does not mark another notification read even if subscription/sequence values overlap.
- Manual smoke check: tap unread/read card surfaces, each child control, links/actions/attachments, and cards while selection mode is active; confirm no card tap opens a URL, copies text, or navigates.

### Project Structure Notes

- Primary production updates: `db/Database.kt`, `db/Repository.kt`, `ui/MessageCardBinder.kt`, and the current host wiring in `ui/DetailAdapter.kt` / `ui/DetailActivity.kt`.
- Place tests beside existing conventions under `app/src/test/java/io/heckel/ntfy/` or `app/src/androidTest/java/io/heckel/ntfy/`; create only the minimum package structure needed.
- No resource, schema migration, delivery/service, notification-action, dependency, or navigation work belongs in this story.

### Previous Story Intelligence

- Story 2.2 establishes that all card styling and interaction state must reset on every bind to avoid RecyclerView leakage; apply the same discipline to bound notification IDs and pending mark-read state.
- Stories 2.1/2.2 require the binder to remain reusable and host-independent. Persistence callbacks belong at the boundary, not inside the view binder.
- Prerequisite stories are context documents only in the present working tree; do not assume their proposed APIs landed unchanged.

### Git Intelligence

- Recent commits add the UI-parity specification and reference catalogs; no Epic 2 implementation has landed.
- The working tree already contains generated story artifacts and sprint-status edits. Preserve all unrelated changes.
- No existing test source tree was found during story analysis, so test scaffolding may be necessary but should remain narrowly scoped.

### Latest Technical Information

- No web research is required for this story: it uses stable, already-pinned Android/Room/View APIs and project-local contracts. Follow the repository's resolved dependency versions rather than upgrading libraries in this behavioral change.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 2, Story 2.5, FR6a, release gate]
- [Source: `docs/ui-parity/components.md` §1 — Notification Card interaction]
- [Source: `docs/ui-parity/message-format.md` §7 — Card vs. detail]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` CAP-7 and Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — current card/navigation architecture]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md` — binder boundary and preserved behaviors]
- [Source: `_bmad-output/implementation-artifacts/2-2-priority-accent-bar-all-five-priorities.md` — bind/reset and prerequisite guidance]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` — Notification model and DAO read operations]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — repository boundary]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — current card/child click handling]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` — legacy whole-card click behavior]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- The customization resolver required Python 3.11; customization was manually resolved from `customize.toml` with no team/user overrides.
- Loaded one epics document, the SPEC/brownfield companions, relevant UI-parity catalogs, Story 2.1, Story 2.2, current DAO/repository, adapter, Activity click handler, and card layout.
- No standalone PRD, architecture, UX, or `project-context.md` file was found; the project uses the SPEC + companion model documented in `epics.md`.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Added Android-specific unread-state mapping, exact-ID persistence contract, legacy-click removal, child-event isolation, selection-mode preservation, and duplicate-tap guardrails.

### File List

- `_bmad-output/implementation-artifacts/2-5-tap-to-mark-read.md`

