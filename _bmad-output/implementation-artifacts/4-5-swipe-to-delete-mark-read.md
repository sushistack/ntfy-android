# Story 4.5: Swipe to Delete / Mark Read (FR6b)

Status: ready-for-dev

## Story

As a user,
I want to swipe a card to delete or mark it read,
so that I can triage quickly with touch gestures.

## Acceptance Criteria

1. **Given** a card in the feed RecyclerView  
   **When** I swipe left past the 72dp snap threshold  
   **Then** a coral (`@color/priority_max`) delete backing is revealed at 96dp width  
   **And** releasing past threshold opens the delete-confirm dialog (same copy/style as Story 2.3b's X-delete confirm)  
   **And** the notification is deleted only when the user confirms Delete; swiping back or cancelling leaves it intact.

2. **Given** an **unread** card (`notificationId != 0`) in the feed  
   **When** I swipe right past the 72dp snap threshold  
   **Then** an emerald (`@color/accent_text`) mark-read backing is revealed at 96dp width  
   **And** releasing past threshold calls `repository.markAsRead(notificationId)` exactly once  
   **And** the unread dot clears through the normal Room observable update (no manual UI mutation).

3. **Given** a **read** card (`notificationId == 0`) in the feed  
   **When** I swipe right  
   **Then** the right-swipe gesture is **not intercepted** — `ItemTouchHelper` reports no backing, no snap, no action  
   **And** the card returns to resting position immediately (no jank).

4. **Given** a card is being swiped in any direction  
   **When** the drag is in progress (below threshold)  
   **Then** the colored backing layer is mounted and rendered only while the swipe is active  
   **And** when the card snaps back the backing is removed from the view hierarchy (not merely hidden with GONE/INVISIBLE).

5. **Given** the delete-confirm dialog is open  
   **When** the user rotates the screen, navigates away, or the Activity is recreated  
   **Then** the dialog state is preserved (via Fragment back-stack or retained dialog fragment)  
   **And** neither the dialog nor the swipe-dismissed card state is lost.

6. **Given** the user swipes a card and the gesture completes  
   **When** the `ItemTouchHelper` callback returns  
   **Then** the RecyclerView adapter item is not manually removed before the Room observable fires  
   **And** the list diff animates the removal naturally when Room emits the updated list.

## Tasks / Subtasks

- [ ] Add ID-scoped `markAsRead` to DAO and Repository (AC: 2)
  - [ ] In `NotificationDao`: add `@Query("UPDATE notification SET notificationId = 0 WHERE id = :notificationId AND notificationId != 0") fun markAsRead(notificationId: String)`.
  - [ ] In `Repository`: expose `fun markAsRead(notificationId: String) = notificationDao.markAsRead(notificationId)`.
  - [ ] Do not use `markAllAsRead(subscriptionId)` or `markAsReadBySequenceId()` for swipe — wrong scope.
- [ ] Implement `FeedSwipeCallback` (AC: 1–6)
  - [ ] Create `app/src/main/java/io/heckel/ntfy/ui/FeedSwipeCallback.kt` extending `ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)`.
  - [ ] Override `getSwipeDirs`: return 0 for read notifications on right-swipe (disable gesture); left-swipe always enabled.
  - [ ] Override `onSwiped`: route left→ trigger delete confirm dialog; right→ launch IO coroutine calling `repository.markAsRead(notification.notificationId)` [Note: pass the actual Android notification popup ID as the String for the DAO update via `notification.id`].
  - [ ] Override `onChildDraw`: draw the colored backing rectangle (coral or emerald) behind the card only while `dX != 0`; compute backing width from `abs(dX)`, capped at 96dp.
  - [ ] Override `clearView`: remove the custom backing view (added to parent) when the gesture ends.
  - [ ] Snap threshold: 72dp (convert to pixels using `resources.displayMetrics.density`).
  - [ ] `getSwipeThreshold`: return `threshold / itemView.width` so the 72dp becomes the release threshold.
- [ ] Wire `FeedSwipeCallback` to the feed RecyclerView (AC: 1–6)
  - [ ] After Stories 4.1 and 4.2 have established the feed RV, attach via `ItemTouchHelper(FeedSwipeCallback(...)).attachToRecyclerView(feedList)`.
  - [ ] The callback must accept a `(position: Int) -> Notification?` accessor and a `lifecycleScope` so swipe actions run off the main thread.
  - [ ] **Do not modify the existing DetailActivity swipe-to-delete** — that is a separate host and must be left intact.
- [ ] Delete-confirm dialog (AC: 1, 5)
  - [ ] Reuse the same `MaterialAlertDialogBuilder` pattern and string keys as Story 2.3b's X-delete confirm (`R.string.detail_clear_dialog_message`, `R.string.detail_clear_dialog_permanently_delete`, `R.string.detail_clear_dialog_cancel`).
  - [ ] Show dialog via a `DialogFragment` subclass (not a bare `AlertDialog.show()`) to survive rotation.
  - [ ] On Delete: `lifecycleScope.launch(Dispatchers.IO) { repository.markAsDeleted(notification.id) }`.
  - [ ] On Cancel: `adapter.notifyItemChanged(position)` to snap card back without data change.
- [ ] Tests (AC: 1–6)
  - [ ] DAO test: `markAsRead` on an unread notification sets `notificationId = 0`; another row in the same subscription is unchanged; calling on an already-read row is a no-op.
  - [ ] `FeedSwipeCallback` unit test: `getSwipeDirs` returns 0 for right-swipe on a read notification; returns both dirs for unread.
  - [ ] Integration smoke: left-swipe → dialog cancel → card unchanged; left-swipe → dialog confirm → Room emits without the row; right-swipe on unread → mark-read write issued once.

## Dev Notes

### Dependency Gate (CRITICAL)

Story 4.5 is **the third story** in the feed serialization chain: **4.1 → 4.2 → 4.5**.

- Story 4.1 establishes the single-feed `RecyclerView` + adapter; this story's `ItemTouchHelper` attaches to that RV **without modifying the adapter**.
- Story 4.2 establishes pagination and arrival animation wiring; this story adds swipe as a separate gesture layer.
- **Neither 4.1 nor 4.2 has a story file yet** (both are `backlog` in sprint-status). Those must be created and implemented first. Treat their APIs as context from the epics spec; adapt to their final implementations on landing.

### Key Design Decisions

- **`ItemTouchHelper` is the right mechanism.** The existing `DetailActivity` already uses `ItemTouchHelper.SimpleCallback` for swipe-to-delete (see `DetailActivity.kt` lines 356–375). The feed uses the same pattern, extended with directional backing colors and the right-swipe mark-read variant.
- **Backing layers are drawn in `onChildDraw`, NOT inflated XML views.** Drawing directly on the Canvas inside `onChildDraw` is both simpler and avoids the view-lifecycle bugs that arise from adding/removing child views to a RecyclerView. Draw the coral/emerald `Paint` rect directly. No inflation.
- **Read-state guard is in `getSwipeDirs`.** Returning `0` from `getSwipeDirs` for read-on-right disables the gesture before any drawing occurs — the cleanest way to block the backing appearance.

### Existing Code to Reuse and Not Break

#### `DetailActivity.kt` — lines 356–375 (existing swipe logic — DO NOT TOUCH)
```
val itemTouchCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    override fun onMove(...) = false
    override fun onSwiped(viewHolder, swipeDir) {
        val notification = adapter.get(viewHolder.absoluteAdapterPosition)
        lifecycleScope.launch(Dispatchers.IO) { repository.markAsDeleted(notification.id) }
        // shows snackbar with undo
    }
}
val itemTouchHelper = ItemTouchHelper(itemTouchCallback)
itemTouchHelper.attachToRecyclerView(mainList)
```
This is `DetailActivity`'s own swipe-to-delete (bidirectional delete). The new `FeedSwipeCallback` is for the **new feed Activity/Fragment** from Epic 4. These are separate host surfaces — the old code must remain untouched.

#### `NotificationDao` — existing mark-as-read queries (Database.kt lines 624–628)
```kotlin
@Query("UPDATE notification SET notificationId = 0 WHERE subscriptionId = :subscriptionId")
fun markAllAsRead(subscriptionId: Long)

@Query("UPDATE notification SET notificationId = 0 WHERE subscriptionId = :subscriptionId AND sequenceId = :sequenceId")
fun markAsReadBySequenceId(subscriptionId: Long, sequenceId: String)
```
These are too broad (subscription-scope) or use sequenceId (wrong key). Add the new narrow ID-scoped query as a separate method; do not alter these.

#### `Repository.kt` — existing mark operations (lines 159–192)
- `markAsDeleted(notificationId: String)` — call this in the delete-confirm confirm path.
- `markAllAsRead(subscriptionId)` — NOT for card swipe.
- Add `markAsRead(notificationId: String)` for the right-swipe path.

### Architecture Compliance

- Stay in the existing View/XML + AppCompat/RecyclerView/Room stack; **no Compose**.
- `FeedSwipeCallback` must accept its data accessor and scope from outside — no `Repository` reference captured in the callback constructor if the feed ViewModel provides the accessor. Keep the callback testable in isolation.
- Use `notification.id` (String primary key) for both `markAsDeleted` and `markAsRead` calls. Do not use `sequenceId` — it is not guaranteed unique per row.
- Snap threshold and backing width are **dp constants**, converted to pixels at runtime via `displayMetrics.density`. Never hardcode pixel integers.
- The confirm dialog must be a `DialogFragment` to survive configuration changes (AC 5). Bare `MaterialAlertDialogBuilder.show()` inside the swipe callback will dismiss on rotation.

### Token Color Mapping

| Swipe direction | Backing color | Token key | Usage |
|---|---|---|---|
| Left (delete) | coral | `@color/priority_max` | from Story 1.1 color resources |
| Right (mark read) | emerald | `@color/accent_text` | from Story 1.1 color resources |

Both tokens must exist before this story lands (Epic 1 prerequisite). The backing uses these as solid fill colors for the rectangle drawn in `onChildDraw`.

### Snap Threshold and Backing Dimensions

From epics.md Story 4.5 ACs:
- **Reveal width:** 96dp
- **Snap threshold:** 72dp (release past this = action fires)
- Implement `getSwipeThreshold(viewHolder)` returning `72dp_px / viewHolder.itemView.width.toFloat()`

### Data Model Note

`Notification.notificationId` (Int) is the Android OS popup notification ID (0 = read/no-popup, non-zero = unread). `Notification.id` (String) is the ntfy-server-assigned unique message identifier — this is the primary key for DAO writes.

The mark-read DAO query: `WHERE id = :notificationId AND notificationId != 0` uses `id` (the String PK) and the `notificationId` column (Int). Name the DAO parameter carefully to avoid confusion:

```kotlin
@Query("UPDATE notification SET notificationId = 0 WHERE id = :id AND notificationId != 0")
fun markAsRead(id: String)

// Repository:
fun markAsRead(notificationId: String) = notificationDao.markAsRead(notificationId)
```

### Previous Story Patterns to Follow

- Story 2.3b (X-delete confirm dialog): same `MaterialAlertDialogBuilder` pattern, same string keys. Reference that story's implementation for dialog styling.
- Story 2.5 (tap-to-mark-read): shows the correct narrow ID-scoped `markAsRead` DAO pattern. This story reuses the same `markAsRead` method that Story 2.5 adds — if 2.5 already landed, use its existing DAO/repository method instead of adding a duplicate.
- Story 2.2 (priority bar): establishes the rule that **all state must reset on every bind** to avoid RecyclerView holder reuse bugs. Apply the same discipline in `FeedSwipeCallback`'s `clearView`.
- DetailActivity existing swipe (lines 356–375): template for `ItemTouchHelper.SimpleCallback` setup. Extend, don't copy-paste.

### Project Structure Notes

New file to create:
- `app/src/main/java/io/heckel/ntfy/ui/FeedSwipeCallback.kt`

Files to update:
- `app/src/main/java/io/heckel/ntfy/db/Database.kt` — add `markAsRead(id: String)` to `NotificationDao`
- `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — expose `markAsRead`
- Future Epic 4 feed host (Activity/Fragment created in Story 4.1) — wire `ItemTouchHelper`

Files to leave untouched:
- `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` — its own swipe logic stays as-is
- `app/src/main/res/layout/fragment_detail_item.xml` — card layout is Epic 2's domain

### Testing Requirements

Place tests under:
- `app/src/test/java/io/heckel/ntfy/` (JVM unit tests for DAO, callback logic)
- `app/src/androidTest/java/io/heckel/ntfy/` (instrumented tests for Room/integration)

Use Room's `in-memory` database for instrumented DAO tests (see pattern in any existing Room test if present).

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 4, Story 4.5, FR6b]
- [Source: `_bmad-output/planning-artifacts/epics.md` — FR6b note: "FR6b swipe→delete/mark-read = Epic 4 ItemTouchHelper on feed RecyclerView"]
- [Source: `_bmad-output/planning-artifacts/epics.md` — Feed RecyclerView serialization note: "Stories 4.1 → 4.2 → 4.5 touch same feed RV/adapter and must land in order. 4.5's ItemTouchHelper attaches to the RV without modifying the adapter."]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` lines 356–375 — existing ItemTouchHelper swipe pattern]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` lines 624–628 — existing mark-as-read DAO methods]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Repository.kt` lines 159–192 — existing mark/delete repository operations]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` lines 146–186 — Notification entity: `id` (String PK) vs `notificationId` (Int, read/unread flag)]
- [Source: `_bmad-output/implementation-artifacts/2-5-tap-to-mark-read.md` — same narrow ID-scoped markAsRead DAO contract]
- [Source: `_bmad-output/implementation-artifacts/2-3b-x-delete-with-token-styled-confirm-dialog.md` — delete-confirm dialog pattern and string keys]
- [Source: `docs/ui-parity/components.md` §1 — card interaction spec, swipe colors]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- No `project-context.md` found; project uses SPEC + companion model documented in `epics.md`.
- No Epic 4 story files exist yet (4-1 through 4-4 all backlog); this is the first Epic 4 story to be created per user request.
- `Database.kt` already has `sequenceId` column on Notification (version 18), confirming Epic 0 context is present.
- No existing `FeedSwipeCallback.kt` in working tree; `DetailActivity.kt`'s existing `ItemTouchHelper` is the reference template.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Added dependency gate note (4.1 → 4.2 → 4.5 serialization), existing swipe code in DetailActivity as do-not-touch reference, backing-color token mapping, dp constant rules, `notificationId` vs `id` naming trap, Dialog vs bare AlertDialog rotation bug prevention.

### File List

- `_bmad-output/implementation-artifacts/4-5-swipe-to-delete-mark-read.md`
