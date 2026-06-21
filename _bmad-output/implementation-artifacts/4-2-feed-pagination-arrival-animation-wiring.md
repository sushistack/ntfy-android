# Story 4.2: Feed Pagination & Arrival Animation Wiring

---
baseline_commit: 431bbeac5e66fdf9fbc0f8d98ab941e7514859e2
---

Status: done

## Story

As a user,
I want the feed to page in as I scroll and animate genuinely new cards,
so that long histories stay performant and new arrivals are noticeable.

## Acceptance Criteria

1. **Given** more than 20 messages (first page rendered by Story 4.1)  
   **When** I scroll to the bottom of the feed  
   **Then** the next 20 messages are appended to the RecyclerView (client-side infinite scroll, not Jetpack Paging)  
   **And** subsequent pages each load 20 messages until no more remain  
   **And** the feed does not reload the entire list — only new rows are appended.

2. **Given** a page load is triggered at scroll bottom  
   **When** that page load fails (network error, Room exception, etc.)  
   **Then** a retry affordance is surfaced — not a silent empty end  
   **And** the error state is coordinated with Story 4.3's disconnected/error container state.

3. **Given** a genuinely new message arrives via the live Room Flow  
   **When** it appears in the adapter  
   **Then** only that card plays the 0.25s slide-in animation from Story 2.6  
   **And** the animation is driven by a set of newly-arrived stable message IDs (not row index, not position == 0)  
   **And** the newly-arrived ID is consumed exactly once — rescrolling, rebinding, or pagination does not replay it.

4. **Given** reduced motion is enabled (Story 1.4 `ReducedMotion` helper, `ValueAnimator.areAnimatorsEnabled() == false`)  
   **When** a new card arrives  
   **Then** the slide-in is skipped and the card appears immediately in its final position  
   **And** the live-region announcement still fires (reduced-motion does not suppress the announcement).

5. **Given** one or more new messages arrive in the same batch  
   **When** their arrival is presented to the user  
   **Then** an accessibility live-region "new notifications" announcement fires exactly once per batch  
   **And** the announcement is emitted by this feed coordinator (Story 4.2), not by individual card binds  
   **And** pagination appends, initial loads, and recycled binds do not trigger the announcement.

6. **Given** the All-feed mode  
   **When** a new message arrives on any subscribed topic  
   **Then** it appears with arrival animation and announcement identical to per-topic arrival.

## Tasks / Subtasks

- [x] Implement client-side infinite scroll pagination (AC: 1, 2)
  - [x] Add a `RecyclerView.OnScrollListener` to the Epic 4 feed RecyclerView that detects when the last visible item is within a threshold (e.g., 3 items from the bottom).
  - [x] Add a `currentPage: Int` (or `offset: Int`) state to the Epic 4 feed ViewModel; initial value = 0 after Story 4.1's first page.
  - [x] Add a paged DAO query to `NotificationDao`: `SELECT * FROM notification WHERE subscriptionId = :subscriptionId AND deleted != 1 ORDER BY sequenceId DESC, timestamp DESC, id DESC LIMIT :limit OFFSET :offset` (or an overloaded `listFlow` variant); for All-feed use the existing `listFlow()` without a subscription filter.
  - [x] Add a `loadNextPage()` method to the feed ViewModel; it fires the paged query and appends results to a `MutableLiveData<List<Notification>>` backing the adapter.
  - [x] Guard with an `isLoadingPage: Boolean` flag to prevent concurrent page loads triggered by rapid scrolling.
  - [x] On page-load failure, post a `PageLoadError` state that Story 4.3 / the feed host consumes to surface the retry affordance (AC: 2).
- [x] Wire the newly-arrived ID set for animation (AC: 3, 4, 5, 6)
  - [x] Add a `newlyArrivedIds: MutableSet<String>` to the feed ViewModel (or a `SharedFlow<Set<String>>`) that tracks IDs of messages that arrived while the feed was loaded and visible.
  - [x] In the feed observer that receives the Room Flow update, diff the incoming list against the previous known list: IDs present in new but not in old (and not in the initial page load) are genuinely new arrivals; add them to `newlyArrivedIds`.
  - [x] Pass `newlyArrivedIds` to the feed adapter / `MessageCardBinder` per the Story 2.6 arrival contract so each flagged ID plays the slide-in exactly once.
  - [x] After the binder consumes an ID (plays or skips under reduced motion), remove it from `newlyArrivedIds` so it cannot replay.
  - [x] Do NOT treat initial page load, pagination appends, search/filter, or adapter `notifyItemChanged` / DiffUtil insertions as arrivals.
- [x] Emit the live-region batch announcement (AC: 5, 6)
  - [x] Use the `ArrivalAnnouncer` / host-level helper from Story 2.6 once per arrival batch (one `announceForAccessibility` call, not one per card).
  - [x] Do not announce on pagination, skeleton display, initial load, or adapter rebinds.
- [x] Wire Story 4.1 serialization prerequisite (no code change needed, confirm API compatibility)
  - [x] Confirm that the Story 4.1 feed RecyclerView, adapter, and ViewModel expose the attachment points this story requires (scroll listener registration, `newlyArrivedIds` input, `loadNextPage()` trigger).
  - [x] Confirm the `MessageCardBinder` from Epic 2 accepts a `newlyArrivedIds: Set<String>` or equivalent per Story 2.6 spec.
- [x] Add focused tests (AC: 1–6)
  - [x] Unit test: `newlyArrivedIds` only contains IDs not present in the previous snapshot; initial load produces no arrivals.
  - [x] Unit test: an ID is removed from `newlyArrivedIds` after consumption; a second consumption returns no animation state.
  - [x] Unit test: pagination append does not produce any newly-arrived IDs.
  - [x] Unit test: `isLoadingPage` guard prevents concurrent page triggers.
  - [x] Unit test: arrival batch announces exactly once regardless of batch size.
  - [x] Instrumentation test (optional but recommended): covered by source-level architecture guard tests verifying wiring.

## Dev Notes

### Serialization with Story 4.1

The epic note is explicit: **Stories 4.1 → 4.2 → 4.5 touch the same feed RV/adapter and must land in order.** This story builds directly on top of Story 4.1's RecyclerView and feed ViewModel. Do not attempt to implement this story before Story 4.1 is merged.

Story 4.2's responsibilities within the feed architecture:
- Owns the `newlyArrivedIds` set and its lifecycle (add on live arrival, remove after first presentation).
- Owns the page-load trigger (scroll listener + ViewModel `loadNextPage()`).
- Does NOT own the skeleton container (Story 4.3), the ItemTouchHelper swipe (Story 4.5), or the deep-link scroll target (Story 4.1).

### Current Codebase State (to preserve and extend)

**`DetailAdapter.kt`** — `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
- `ListAdapter<Notification, DetailAdapter.DetailViewHolder>` with `TopicDiffCallback` (compares by `notification.id`).
- `submitList()` is the data update seam; DiffUtil handles diff automatically.
- Currently Activity-coupled; Epic 4 replaces with the adapter-agnostic binder from Story 2.1, but the `ListAdapter`/DiffUtil pattern is preserved.

**`DetailActivity.kt`** — `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt`
- Current scroll-to-top-on-insert observer (lines 379–386): watches `onItemRangeInserted(positionStart, itemCount)` and scrolls to 0. **This must be replaced** in the Epic 4 feed — it is position-based and would scroll to top on every pagination append.
- Current LiveData subscription (`viewModel.listFiltered(subscriptionId).observe`) loads the entire list at once — no pagination. Epic 4 replaces this with the paged approach.

**`NotificationDao`** — `app/src/main/java/io/heckel/ntfy/db/Database.kt` lines 585–650
- `listFlow(subscriptionId)`: `ORDER BY timestamp DESC` (pre-Story 0.2) → will become `ORDER BY sequenceId DESC, timestamp DESC, id DESC` after Story 0.2.
- No paged query exists. This story adds a `LIMIT / OFFSET` variant (or a separate `listFlowPaged` query).
- **Dependency**: Story 0.2 must be merged before using `sequenceId`-based ordering. If 0.2 is not yet merged, implement with the existing `timestamp DESC` order but leave an inline comment marking the ordering upgrade point.

**`DetailViewModel.kt`** — `app/src/main/java/io/heckel/ntfy/ui/DetailViewModel.kt`
- Currently thin: `list()` and `listFiltered()` delegate to Repository, which in turn calls the DAO Flow.
- The Epic 4 feed ViewModel (new class, e.g. `FeedViewModel`) extends this pattern with `currentPage`, `isLoadingPage`, `newlyArrivedIds`, and `loadNextPage()`.
- Do NOT modify `DetailViewModel` itself; create a new ViewModel for the Epic 4 feed.

**`Repository.kt`** — `app/src/main/java/io/heckel/ntfy/db/Repository.kt`
- Add a `getNotificationsPaged(subscriptionId: Long, limit: Int, offset: Int)` method that delegates to the new DAO query.
- For the All-feed, a matching `getAllNotificationsPaged(limit: Int, offset: Int)` variant without the subscription filter.

### Pagination Design: Client-Side Offset Paging (No Jetpack Paging Library)

The architecture decision is **no Jetpack Paging** (consistent with the no-Compose, minimal-change re-skin mandate). Implement simple offset-based paging:

```
PAGE_SIZE = 20

On scroll to bottom:
  if (!isLoadingPage && hasMorePages) {
      isLoadingPage = true
      val results = repository.getNotificationsPaged(subscriptionId, PAGE_SIZE, currentPage * PAGE_SIZE)
      if (results.isEmpty()) { hasMorePages = false }
      else {
          currentPage++
          append results to adapter via submitList(currentList + results)
      }
      isLoadingPage = false
  }
```

**Critical: split the data source.** The feed uses two data streams:
1. A **live Room Flow** for the first page (newest 20), observed continuously so new arrivals appear automatically.
2. **Discrete page fetches** (suspend queries, not Flow) for pages 2+, appended once on demand.

This avoids the race condition where a new arrival would cause the live Flow to emit a full re-sorted list that collides with the appended older pages. The paged append is a one-shot `suspend fun` call from `Dispatchers.IO`, not a Flow observation.

**Alternative acceptable approach:** A single `MutableList<Notification>` in the ViewModel that the live Flow updates (page 1 window) and page fetches append to. The key invariant is that the live Flow observation is only for the newest 20, and pagination loads older rows on demand.

### Arrival Detection Algorithm

The arrivals set must be populated by comparing the live Flow's new emission to the prior known state:

```kotlin
private var knownIds: Set<String> = emptySet()  // in FeedViewModel

// In the live Flow observer:
val incomingIds = newNotifications.map { it.id }.toSet()
val arrivals = if (knownIds.isEmpty()) {
    emptySet()  // First emission = initial load, not arrivals
} else {
    incomingIds - knownIds  // Genuinely new
}
knownIds = incomingIds
newlyArrivedIds.addAll(arrivals)
// announceArrivals(arrivals) if non-empty
```

**Do NOT treat as arrivals:**
- Initial page load (first Flow emission when `knownIds.isEmpty()`).
- Pagination append (these are old messages, just newly visible).
- DiffUtil `onItemRangeInserted` at non-zero positions (old data revealed by scroll).
- Any card that was previously in `knownIds` and re-appears (e.g., after an undelete).

### Announcement Seam (Story 2.6 Contract)

Story 2.6 defines a host-level `ArrivalAnnouncer` or equivalent. Story 4.2 calls it once per arrival batch (after detecting `arrivals.isNotEmpty()`):

```kotlin
// Call once per batch, not per card
arrivalAnnouncer.announce(context, arrivals.size)
// String resource: "new notifications" (localizable, from Story 2.6)
```

If Story 2.6 is not yet merged, add a placeholder call with `TODO("consume Story 2.6 ArrivalAnnouncer")` rather than implementing announcement logic twice.

### Scroll Listener Pattern

```kotlin
mainList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val total = layoutManager.itemCount
        if (dy > 0 && lastVisible >= total - SCROLL_THRESHOLD && !viewModel.isLoadingPage) {
            viewModel.loadNextPage()
        }
    }
})
```

`SCROLL_THRESHOLD = 3` (load when within 3 items of the end).

Remove the existing `DetailActivity` `AdapterDataObserver` scroll-to-0 logic; it must not be carried into the Epic 4 feed (it would scroll to top on every pagination append).

### sequenceId Field — Existing vs. Epic 0

**IMPORTANT DISAMBIGUATION:** The `Notification.sequenceId: String` field already in the codebase (since DB migration 17→18) is used for **update-grouping** semantics — when a new message arrives with the same `sequenceId`, the old one is marked deleted. This is NOT the server-sequence ordering field that Epic 0 stories discuss.

Epic 0's Story 0.2 reuses this same field for ordering (changing the DAO `ORDER BY` from `timestamp DESC` to `sequenceId DESC, timestamp DESC, id DESC`). Once Story 0.2 is merged, the ordering naturally picks up.

This story (4.2) does not need to wait for Epic 0 to implement pagination — use `timestamp DESC` as the interim tie-breaker if needed, but structure the DAO query so that swapping to `sequenceId DESC` requires only changing the `ORDER BY` clause.

### Architecture Compliance

- Stay in View/XML + AppCompat; do not introduce Jetpack Compose or Jetpack Paging.
- New classes go under `io.heckel.ntfy.ui` (ViewModel, adapter) and `io.heckel.ntfy.db` (DAO additions).
- No new external dependencies — RecyclerView `OnScrollListener` and `LinearLayoutManager.findLastVisibleItemPosition()` are in the existing `androidx.recyclerview:recyclerview:1.4.0` dependency.
- Preserve min SDK 26, Java/Kotlin 17, both `play` and `fdroid` flavors.
- `ItemTouchHelper` (swipe) is Story 4.5's territory — do not add it here; just ensure the RecyclerView setup does not preclude 4.5 attaching it later.

### Files to Create

- `app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt` — new ViewModel owning `currentPage`, `isLoadingPage`, `newlyArrivedIds`, `knownIds`, `loadNextPage()`, and the live Flow observer; replaces `DetailViewModel` for the Epic 4 feed surface.
- DAO query additions in `app/src/main/java/io/heckel/ntfy/db/Database.kt` — `listFlowPaged(subscriptionId, limit, offset)` and optionally `listAllFlowPaged(limit, offset)`.
- Repository additions in `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — delegate methods for the paged queries.
- Test class, e.g. `app/src/test/java/io/heckel/ntfy/ui/FeedViewModelTest.kt` — unit tests for arrival detection and page guard.

### Files to Update

- `app/src/main/java/io/heckel/ntfy/db/Database.kt` (NotificationDao) — add paged queries.
- `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — add paged accessor methods.
- The Epic 4 feed Fragment/Activity (from Story 4.1) — register scroll listener, wire `FeedViewModel`, wire `newlyArrivedIds` to the adapter/binder.

### Files to NOT Modify

- `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` — the Epic 4 shell is a new surface; do not patch the old Activity.
- `app/src/main/java/io/heckel/ntfy/ui/DetailViewModel.kt` — kept as-is for backwards compat with the existing Detail surface.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt` (from Epic 2) — consume its `newlyArrivedIds` API, do not redesign it.
- Any Epic 3 body renderer files — this story has no structured body concern.

### Project Structure Notes

- All new production classes in `app/src/main/java/io/heckel/ntfy/ui/` or `app/src/main/java/io/heckel/ntfy/db/`.
- Tests in `app/src/test/java/io/heckel/ntfy/` (unit) and/or `app/src/androidTest/java/io/heckel/ntfy/` (instrumentation for DAO).
- No new string resources beyond the Story 2.6 "new notifications" announcement string (already specified there).
- No new drawable/layout resources required by this story — it is purely data/logic wiring.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 4, Stories 4.1, 4.2, 4.3, 4.5; FR8; serialization note after Story 4.1]
- [Source: `_bmad-output/planning-artifacts/epics.md` — NFR8 (`sequenceId` descending order)]
- [Source: `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md` — Story 2.6 arrival animation + announcement contract]
- [Source: `_bmad-output/implementation-artifacts/0-2-populate-sequenceid-on-receive-and-order-the-dao-query.md` — ordering change, DAO contract]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` lines 379–386 — scroll-to-0 observer pattern to remove]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` lines 305–353 — existing list observation via LiveData / submitList]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` lines 46–50 — ListAdapter + TopicDiffCallback]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailViewModel.kt` — ViewModel pattern to extend for FeedViewModel]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` lines 585–650 — NotificationDao, existing listFlow queries]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` lines 145–186 — Notification entity, sequenceId field]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Repository.kt` lines 123–149 — getNotificationsLiveData, addNotification]
- [Source: `_bmad-output/implementation-artifacts/1-4-reduced-motion-accessibility-primitives.md` — ReducedMotion helper]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Python 3.11 not available; workflow customization resolved manually from base `customize.toml` with no team/user overrides found.
- Epic 4 story 4-1 does not yet have an implementation artifact; no previous story learnings available.
- Full epics.md, NotificationDao, DetailActivity, DetailAdapter, DetailViewModel, Repository, and Notification entity analyzed.

### Completion Notes List

- Ultimate context engine analysis completed — comprehensive developer guide created.
- Critical disambiguation documented: existing `sequenceId` field = update-grouping (not server-sequence ordering); Epic 0 reuses it for ordering via DAO ORDER BY change.
- Pagination design decided: client-side offset paging with split data sources (live Flow for page 1, discrete suspend queries for pages 2+) to avoid race condition between live updates and pagination appends.
- Scroll-to-0 AdapterDataObserver pattern in DetailActivity identified as a pattern to NOT carry forward into the Epic 4 feed.
- Arrival detection algorithm implemented with explicit exclusions (initial load → knownIds.isEmpty() guard; pagination appends never pass through onEmission()).
- `PageLoadError` data class added to FeedViewModel — Story 4.3 consumes this to show retry UI.
- `FeedAdapter.onArrivalConsumedCallback` added so adapter notifies ViewModel when an ID is consumed; both the local adapter set and ViewModel LiveData are kept in sync.
- `ALL_SUBSCRIPTIONS` constant in FeedActivity companion kept as deprecated alias pointing to `ALL_SUBSCRIPTIONS_ID` in FeedViewModel for backwards compat.
- 23 unit tests pass (failures=0, errors=0); full regression suite: BUILD SUCCESSFUL.

### File List

- `_bmad-output/implementation-artifacts/4-2-feed-pagination-arrival-animation-wiring.md`
- `app/src/main/java/io/heckel/ntfy/db/Database.kt` — added `listPaged` and `listAllPaged` DAO queries
- `app/src/main/java/io/heckel/ntfy/db/Repository.kt` — added `getNotificationsPaged` and `getAllNotificationsPaged`
- `app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt` — extended with `PAGE_SIZE`, `SCROLL_THRESHOLD`, `isLoadingPage`, `hasMorePages`, `nextOffset`, `knownIds`, `newlyArrivedIds`, `pageLoadError`, `observeLivePage()`, `onLivePageUpdate()`, `loadNextPage()`, `consumeArrivedId()`; `ALL_SUBSCRIPTIONS_ID` constant
- `app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt` — wired `addOnScrollListener`, `observeLivePage`, `onLivePageUpdate`, `feedItems`, `newlyArrivedIds`, `ArrivalAnnouncer`, `pageLoadError`; `onArrivalConsumedCallback` passed to adapter
- `app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt` — added `onArrivalConsumedCallback` parameter; consumed callback now also calls `onArrivalConsumedCallback`
- `app/src/test/java/io/heckel/ntfy/ui/FeedViewModelLogicTest.kt` — 23 unit tests covering AC1–AC6 (arrival detection, pagination guard, hasMorePages flag, consume-once, batch announcement, source architecture guards)

### Review Findings

- [x] [Review][Patch] F1: per-topic mode topicName was non-null, causing chip to appear in per-topic feed [FeedViewModel.kt:onLivePageUpdate, loadNextPage, subscriptionObserver] — fixed: branch on activeSubscriptionId to pass null for per-topic mode
- [x] [Review][Patch] F2: pageLoadError null case unhandled — disconnected panel not cleared on retry success [FeedActivity.kt:pageLoadError observer] — fixed: added else branch restoring HasContent/Empty state

### Change Log

- Date: 2026-06-21 | Story: 4-2 | Implemented client-side offset pagination (PAGE_SIZE=20, SCROLL_THRESHOLD=3) and live-arrival detection; wired ArrivalAnnouncer batch announcement; added paged DAO/Repository queries; 23 unit tests green.
- Date: 2026-06-21 | Review | Fixed F1 (per-topic topicName null), F2 (pageLoadError null recovery).
