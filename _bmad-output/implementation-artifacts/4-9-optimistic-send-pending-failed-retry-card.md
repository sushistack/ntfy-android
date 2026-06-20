# Story 4.9: Optimistic Send — Pending / Failed / Retry Card

Status: ready-for-dev

## Story

As a user,
I want to see my just-sent message appear immediately and know if it failed,
so that I'm never left wondering whether my message actually went out.

## Acceptance Criteria

1. **Given** I tap Send in the publish sheet (Story 4.8)
   **When** the publish HTTP request is in flight
   **Then** an optimistic card appears at the top of the feed in a **pending** state — showing a distinct "sending…" indicator, suppressing the tap-to-mark-read action, and reusing the Epic 2 card shell (`fragment_detail_item.xml` + `MessageCardBinder`)
   **And** the optimistic card is positioned at index 0 of the feed (above all server-received cards) for the duration of the request.

2. **Given** the publish request completes successfully
   **When** the server returns HTTP 200 and the normal message is received through the existing subscriber service
   **Then** the optimistic card is removed from the top of the feed
   **And** the real server card resolves into the normal feed position via the standard adapter diff
   **And** no duplicate card is visible (pending + normal) at any point.

3. **Given** the publish request fails (network error, non-2xx HTTP, cancellation, timeout)
   **When** the error is caught
   **Then** the optimistic card transitions to an **error state** — surfacing a non-dismissible inline retry bar with a localized "Send failed" message and a **Retry** action button
   **And** the error state uses `@color/priority_max` / `@color/priority_urgent` for the error text/icon (existing tokens — no new token is introduced)
   **And** the X-delete button on the card is enabled in the error state, allowing the user to discard the failed outbox item.

4. **Given** the error card is visible
   **When** the user taps **Retry**
   **Then** the card transitions back to the pending state and the publish HTTP request is re-issued with the same payload
   **And** the success and failure paths of AC-2 / AC-3 apply again.

5. **Given** the error card is visible
   **When** the user taps the X button on the card
   **Then** a delete-confirm dialog is shown using the existing shared `NotificationDeleteConfirmation` presenter (Story 2.3b)
   **And** on confirmation the optimistic card is removed from the feed; no Room write occurs (the card was never persisted).

6. **Given** a pending or error card in the feed
   **When** it renders in both light and dark themes
   **Then** the pending indicator and the error retry bar are styled from existing tokens (no raw hex, no new token)
   **And** the card shell is otherwise identical to a normal card (squared, `@color/surface`, 1px `@color/border`, `shadow_elev_1`).

7. **Given** a pending card
   **When** the user taps anywhere on it
   **Then** the tap-to-mark-read action is **not** triggered (the card is non-interactive as a read target; the X button and, when in error state, the Retry button remain tappable).

## Tasks / Subtasks

- [ ] Define `OptimisticMessage` presentation model (AC: 1–6)
  - [ ] Create an in-memory value class `OptimisticMessage(localId: String, title: String, message: String, priority: Int, tags: List<String>, sendState: SendState)` where `SendState` is a sealed class: `Pending`, `Error(cause: String)`.
  - [ ] Store the in-flight list in the feed's `ViewModel` (or a dedicated `OutboxViewModel`) as `MutableStateFlow<List<OptimisticMessage>>`; never write to Room.
  - [ ] Assign a stable `localId` (e.g. `"local_${UUID}"`) so the adapter can identity-diff the list; never collide with server-assigned message IDs.

- [ ] Extend `MessageCardBinder` to accept optimistic state (AC: 1, 3, 6, 7)
  - [ ] Add a sealed input type or nullable overload that accepts `OptimisticMessage` alongside `Notification`.
  - [ ] When binding in `Pending` state: show a "Sending…" indeterminate `CircularProgressIndicator` (or `LinearProgressIndicator`) in/below the header, disable the whole-card tap-to-read listener, show the X button (which will call the discard callback).
  - [ ] When binding in `Error` state: replace the progress indicator with an inline error bar — `@color/priority_max` text "Send failed", `@color/priority_urgent` icon, and a "Retry" `Button` styled as a text/outlined button.
  - [ ] Header still renders title, message (as body), priority badge, and tags (from `OptimisticMessage` fields) so the user recognizes their message.
  - [ ] Reset all transient state on every bind (follow the Story 2.6 reset contract).

- [ ] Wire `PublishFragment` (the existing Story 4.8 bottom sheet) to the outbox (AC: 1–4)
  - [ ] On Send tap: create an `OptimisticMessage(state=Pending)`, emit it to the feed's `outbox` flow, then proceed with the existing `lifecycleScope.launch(Dispatchers.IO)` HTTP call.
  - [ ] On success (`api.publish()` returns without throwing): remove the `OptimisticMessage` from the outbox flow (the real server card arrives via the subscriber service as normal).
  - [ ] On failure: update the `OptimisticMessage` in the outbox flow to `state=Error(cause=errorMessage)`.
  - [ ] Do **not** change the existing `ApiService.publish()` signature; all state transitions happen in the caller.

- [ ] Wire Retry from the card binder callback (AC: 4)
  - [ ] Add a narrow `onRetryRequested(localId: String)` callback to `MessageCardActions` (or equivalent binder callback interface).
  - [ ] On retry: update `OptimisticMessage` back to `Pending`, re-issue the same `api.publish()` call with the stored payload.
  - [ ] Store the original publish payload inside `OptimisticMessage` so retry has everything it needs without re-querying any UI fields (the bottom sheet is already dismissed).

- [ ] Wire X-discard (AC: 5)
  - [ ] Reuse the existing `onDeleteRequested` callback path from Story 2.3b (`NotificationDeleteConfirmation`).
  - [ ] On confirmed delete: remove the `OptimisticMessage` from the outbox flow; no `repository.markAsDeleted()` call.
  - [ ] On pending discard: also cancel the in-flight HTTP job (invoke `cancelFn` / coroutine `Job.cancel()`).

- [ ] Feed adapter integration (AC: 1–2, 6)
  - [ ] In the feed `RecyclerView` adapter (Story 4.1's adapter), combine the outbox flow with the server notifications list such that optimistic cards always appear at position 0.
  - [ ] Use `DiffUtil` with `localId` as the stable ID so pending→error transitions animate in-place; the card does not jump.
  - [ ] On success, the optimistic card is removed and the real card is inserted via the normal `DiffUtil` callback; no special "replace" logic is needed.

- [ ] Add string resources (AC: 3, 7)
  - [ ] `optimistic_send_pending` = "Sending…" (already localized via Weblate)
  - [ ] `optimistic_send_failed` = "Send failed"
  - [ ] `optimistic_send_retry` = "Retry"
  - [ ] Add all three to `app/src/main/res/values/strings.xml` as localizable resources.

- [ ] Add focused tests (AC: 1–7)
  - [ ] Unit test: `OptimisticMessage` state machine — Pending → Error → Pending (retry) → removed (success).
  - [ ] Unit test: discard cancels the job and removes the item without a Room write.
  - [ ] Binder test: in Pending state the card-click listener is absent; X-button listener is present.
  - [ ] Binder test: in Error state the retry button listener fires `onRetryRequested`; card-click listener absent.
  - [ ] Binder test: recycled holder resets all transient state (progress indicator hidden, no stale listener).
  - [ ] Adapter test: optimistic card appears at index 0; is removed on outbox clear; DiffUtil does not crash on the mixed list.

## Dev Notes

### Architecture Constraints (Non-Negotiable)

- **No Compose, no new libraries.** Use `CircularProgressIndicator` or `LinearProgressIndicator` (already in Material Components 1.13.0); use `Button`/`TextButton` for Retry. No Lottie, no Shimmer.
- **No Room write for optimistic items.** The outbox is purely in-memory (`MutableStateFlow` or `MutableLiveData` in the ViewModel). Only real server notifications touch Room.
- **Reuse `fragment_detail_item.xml` and `MessageCardBinder`.** Do not create a separate layout file for the optimistic card; extend the binder's state contract instead.
- **Reuse `NotificationDeleteConfirmation`** (Story 2.3b) for the X-discard confirm dialog. Do not duplicate dialog strings or `MaterialAlertDialogBuilder` setup.
- **Stay in `io.heckel.ntfy.ui`.** No new package; the outbox ViewModel lives alongside existing ViewModels.

### Key Existing Code to Understand Before Implementing

- **`PublishFragment.kt`** (`app/src/main/java/io/heckel/ntfy/ui/PublishFragment.kt`):
  - Already has a `Job?` and `cancelFn: (() -> Unit)?` for the in-flight HTTP call.
  - `onSendClick()` launches on `Dispatchers.IO`, calls `api.publish()`, handles success with `publishListener?.onPublished()` + `dismiss()`, and handles failure with an `errorText` update.
  - Story 4.9 adds: emit `OptimisticMessage` to outbox **before** launching the coroutine; update/remove on success/failure inside the same `try/catch` block. The outbox update must happen on `Dispatchers.Main` (already wrapped in `withContext(Dispatchers.Main)`).
  - `PublishListener` interface is currently implemented by `DetailActivity`. Epic 4's host Activity/Fragment implements it instead; same pattern.
  - Store the original payload as `data class PublishPayload(...)` inside `PublishFragment` or `OptimisticMessage` so retry has everything.

- **`ApiService.publish()`** (`app/src/main/java/io/heckel/ntfy/msg/ApiService.kt`):
  - Signature: `suspend fun publish(baseUrl, topic, user?, message, title, priority, tags, delay, body?, filename, click, attach, email, call, markdown, onCancelAvailable?)`.
  - Do **not** change this signature. The publish call in `PublishFragment.onSendClick()` already uses it. Retry just calls it again with the same arguments.

- **`Notification` entity** (`app/src/main/java/io/heckel/ntfy/db/Database.kt` line ~145):
  - `@Entity(primaryKeys = ["id", "subscriptionId"])` — Room entity; never used for optimistic items.
  - The `id` is a `String` (server-assigned). `OptimisticMessage.localId` must use a clearly distinct prefix (e.g. `"local_"`) so it never conflicts.
  - The `new` field on `Notification` drives the unread dot. Optimistic cards have no `new` concept; the unread dot must be suppressed in the binder.

- **`MessageCardBinder` (expected from Story 2.1)** — not yet written, but the binder's contract is:
  - Takes a message + topic-name argument, holds **no reference** to `DetailActivity` or adapter.
  - Exposes a `MessageCardActions` callback interface.
  - This story extends that interface with `onRetryRequested(localId: String)` and a `onDiscardRequested(localId: String)` variant of `onDeleteRequested`.

- **`DetailAdapter` / feed adapter** — Story 4.1 creates the new single-feed adapter. This story's outbox integration should be wired into that adapter, not the legacy `DetailAdapter`.

### State Machine

```
[not sent]
    │  user taps Send
    ▼
[Pending] ─── HTTP success ──▶ [removed from outbox]
    │
    └── HTTP failure ──▶ [Error(cause)]
                              │  user taps Retry ──▶ [Pending] (loop)
                              │  user taps X + confirms ──▶ [removed from outbox]
```

### Pending Card UI Contract

- Progress indicator: `CircularProgressIndicator` (size: small, ~20dp, `accent_ui` tint) placed to the right of the title **or** a `LinearProgressIndicator` (indeterminate) spanning the bottom of the card header.
- Whole-card click: disabled (`setOnClickListener(null)`).
- X button: enabled; routes to `onDiscardRequested`.
- No unread dot, no tap-to-read action.
- Priority badge: rendered normally from `OptimisticMessage.priority`.
- Tags/timestamp: rendered normally from `OptimisticMessage` fields; timestamp = time of send attempt.

### Error Card UI Contract

- Replace progress indicator with an inline error row:
  - Left: error icon tinted `@color/priority_max`.
  - Center: `"Send failed"` text (`body_sm`, `@color/priority_urgent`).
  - Right: `"Retry"` `TextButton` (accent tint).
- X button: enabled; routes to `onDiscardRequested` (which also cancels any re-in-flight retry job).
- Whole-card click: still disabled.

### Outbox Flow Design

```kotlin
// In ViewModel (Epic 4's feed ViewModel)
data class OptimisticMessage(
    val localId: String,
    val title: String,
    val message: String,
    val priority: Int,
    val tags: List<String>,
    val timestamp: Long,
    val sendState: SendState,
    val payload: PublishPayload  // stored for retry
)

sealed class SendState {
    object Pending : SendState()
    data class Error(val cause: String) : SendState()
}

val outbox = MutableStateFlow<List<OptimisticMessage>>(emptyList())
```

Combine `outbox` with the `Notification` LiveData in the adapter so the RecyclerView list = optimistic items (at top, ordered by localId/timestamp) + server items (sequenceId desc).

### File Ownership

**New files:**
- `app/src/main/java/io/heckel/ntfy/ui/OptimisticMessage.kt` — data model + `SendState` sealed class.
- (Optional) `app/src/main/res/layout/view_message_card_pending.xml` — if the pending/error indicator needs its own inflatable layout; otherwise inline in `fragment_detail_item.xml` via `GONE/VISIBLE` toggling.

**Updated files:**
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt` — extend to accept `OptimisticMessage`; add `onRetryRequested` + `onDiscardRequested` to `MessageCardActions`.
- `app/src/main/java/io/heckel/ntfy/ui/PublishFragment.kt` — emit/remove from outbox in `onSendClick()` success/error paths; store `PublishPayload` for retry.
- Epic 4 feed adapter (Story 4.1 output) — combine outbox flow with server list.
- `app/src/main/res/values/strings.xml` — add three string resources.

**Must NOT touch:**
- `app/src/main/java/io/heckel/ntfy/msg/ApiService.kt` — no signature change.
- `app/src/main/java/io/heckel/ntfy/db/Database.kt` — no schema change.
- `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — no new method needed.
- `fragment_detail_item.xml` — owned by Story 2.1; only extend via binder state, not XML changes.

### Dependencies on Previous Stories

| Story | What 4.9 consumes |
|-------|-------------------|
| 2.1 | `fragment_detail_item.xml` shell + `MessageCardBinder` + `MessageCardActions` interface |
| 2.3b | `NotificationDeleteConfirmation` for X-discard confirm dialog |
| 4.1 | Feed RecyclerView adapter — outbox items prepended at position 0 |
| 4.8 | Publish bottom sheet + `api.publish()` call site — outbox emit wired here |

### Token Compliance (NFR1)

Error state tokens (from `design-tokens.md`):
- `@color/priority_max` (coral): error icon tint + text accent
- `@color/priority_urgent` (slightly darker coral): error text body
- No new token. The epics spec explicitly states: "using the `priority_max`/`priority_urgent` token — no new token".

Pending state token:
- `@color/accent_ui` (emerald): progress indicator tint.

### Testing Requirements

- **Unit tests** (`app/src/test/...`): state machine transitions, outbox add/update/remove, retry payload preservation.
- **Robolectric/View tests** (`app/src/androidTest/...`): binder Pending binding (progress visible, click null), Error binding (retry button fires callback), holder recycle (no stale state).
- **No end-to-end test** against a live server — mock `api.publish()` at the call site.

### Project Context Notes

- Min SDK 26, target SDK 35, Kotlin 2.1.20+, Java 17.
- Material Components 1.13.0 — `CircularProgressIndicator`, `LinearProgressIndicator`, `MaterialButton` all available with no new dependency.
- Both `play` and `fdroid` flavors must work (no flavor-specific publish paths).
- `ApiService.UnauthorizedException`, `EntityTooLargeException`, `ApiException` are the expected failure types from `api.publish()` (see `PublishFragment.kt` lines 583–598).
- The outbox is ephemeral — it does not survive process death. This is acceptable UX per the epic (no offline queue requirement).

### Previous Story Intelligence (Story 2.6 patterns — most recent Epic 2 story)

- **Reset on every bind** — the Story 2.6 contract mandates resetting `translationX/Y`, `alpha`, animators, and listeners before applying new state. Apply the same discipline to the progress indicator `GONE/VISIBLE` and the Retry button listener.
- **No stale listeners** — replace click listener on every bind; never keep a lambda from a previous `OptimisticMessage` binding.
- **Identity-keyed one-shot effects** — if the binder receives a Pending→Error transition for the same `localId`, it is a re-bind, not a new arrival; do not replay slide-in animation.
- **Adapter-agnostic** — the binder must not reference the feed adapter, ViewModel, or coroutine scope; all callbacks flow through `MessageCardActions`.

### Architecture Compliance

- View/XML + AppCompat; no Compose.
- `io.heckel.ntfy.ui` package for all UI classes.
- Outbox ViewModel lives at `io.heckel.ntfy.ui.OutboxViewModel` or merged into the feed's ViewModel.
- No new library dependency.
- No Room schema change (Epic 0 already owns `sequenceId`).

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 4, Story 4.9]
- [Source: `docs/ui-parity/components.md` §1, §10 — card body slot optimistic/pending/error, state surfaces]
- [Source: `docs/ui-parity/screens-layout.md` — optimistic sends at top of feed]
- [Source: `docs/ui-parity/design-tokens.md` — `priority_max`, `priority_urgent`, `accent_ui` tokens]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/PublishFragment.kt` — existing publish HTTP call, cancel/job pattern]
- [Source: `app/src/main/java/io/heckel/ntfy/msg/ApiService.kt` — `publish()` signature + exception types]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` — `Notification` entity, no Room write for optimistic]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md` — binder/actions contract]
- [Source: `_bmad-output/implementation-artifacts/2-3b-x-delete-with-token-styled-confirm-dialog.md` — NotificationDeleteConfirmation reuse]
- [Source: `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md` — reset/recycle contract]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Python 3.11 resolver unavailable; customization resolved manually from base TOML (no team/user overrides present).
- `PublishFragment.kt`, `ApiService.kt`, `Database.kt`, `Repository.kt`, and Epic 2/4 story artifacts read in full.

### Completion Notes List

- Ultimate context engine analysis completed — comprehensive developer guide created.
- Optimistic-send state machine, outbox flow design, token compliance, binder extension contract, and recycling reset rules are all explicit.
- Dependencies on Stories 2.1, 2.3b, 4.1, 4.8 are named with the exact consumed artifact.

### File List

- `_bmad-output/implementation-artifacts/4-9-optimistic-send-pending-failed-retry-card.md`
