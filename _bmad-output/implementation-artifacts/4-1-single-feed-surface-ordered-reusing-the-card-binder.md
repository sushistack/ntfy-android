# Story 4.1: Single Feed Surface (Ordered, Reusing the Card Binder)

Status: ready-for-dev

## Story

As a user,
I want one feed of full notification cards in server order,
so that I see all my notifications in a single scrollable surface, like web.

## Acceptance Criteria

1. **Given** the former `DetailActivity` per-topic list  
   **When** it is reworked into a single-feed `FeedActivity` (or `MainActivity` repurposed)  
   **Then** the feed renders full cards via the **Epic 2 `MessageCardBinder`** (no card rework) ordered by `sequenceId DESC` (Epic 0), with an `@dimen/spacing_4` (18dp) gap between cards, showing the **first page (20 notifications)**  
   **And** the layout uses a `LinearLayoutManager` with an `ItemDecoration` providing 18dp spacing between cards (not padding on the card itself)  
   **And** the RecyclerView uses `ListAdapter<Notification, MessageCardBinder.ViewHolder>` with the `DiffUtil.Callback` defined on `Notification.id`

2. **Given** the feed  
   **When** it renders  
   **Then** there is no separate detail Activity/route to navigate to (no `startActivity(DetailActivity)` call anywhere in the feed path)  
   **And** there is no sticky topic header above cards in either All mode or per-topic mode  
   **And** tapping a card invokes `markAsRead` via the binder's existing tap contract (Story 2.5 — do not rewire it here)

3. **Given** a deep-link Intent carrying a notification `id` (from a tapped system notification)  
   **When** the feed starts or receives the intent  
   **Then** the feed resolves the target notification's position in the list, calls `RecyclerView.smoothScrollToPosition()` to that position  
   **And** passes the notification id to `MessageCardBinder` as the `deepLinkTargetId` parameter (Story 2.6 handles the highlight animation)  
   **And** the scroll-and-highlight logic lives in the feed host (this story), not in the binder

4. **Given** the feed  
   **When** mode is **All** (no subscriptionId argument)  
   **Then** it queries ALL non-deleted notifications across all subscriptions ordered by `sequenceId DESC`, and passes the topic name to `MessageCardBinder` so the topic chip shows  
   **And** when mode is **per-topic** (subscriptionId passed as argument), it queries only that subscription and passes `null` for topic name so the topic chip is omitted (Story 2.4 / Story 4.4 behavior — do not add a topic chip override here)

5. **Given** the DAO ordering constraint  
   **When** the feed data is loaded  
   **Then** the feed uses the `sequenceId DESC` DAO query added in Epic 0 Story 0.2 (NOT `timestamp DESC`)  
   **And** rows where `sequenceId` is null sort deterministically via the tiebreaker defined in Story 0.2  
   **And** the feed never applies a secondary sort by wall-clock time on its own

## Tasks / Subtasks

- [ ] Add all-notifications DAO query ordered by `sequenceId DESC` (AC: 1, 4, 5)
  - [ ] In `NotificationDao`, add `listAllFlow(): Flow<List<Notification>>` returning all non-deleted notifications across subscriptions ordered by `sequenceId DESC` with the Story 0.2 tiebreaker (e.g. `ORDER BY CAST(sequenceId AS INTEGER) DESC, timestamp DESC, id DESC`).
  - [ ] In `Repository`, expose `getAllNotificationsLiveData(): LiveData<List<Notification>>` wrapping the new DAO flow.
  - [ ] Confirm the existing `listFlow(subscriptionId)` in `NotificationDao` is also ordered by `sequenceId DESC` (Epic 0 story 0.2 may have already done this — check and update if not).

- [ ] Create `FeedViewModel` (AC: 1, 4, 5)
  - [ ] Create `app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt`.
  - [ ] Expose two LiveData sources: `listAll(): LiveData<List<Notification>>` (all subscriptions) and `listForSubscription(id: Long): LiveData<List<Notification>>` (per-topic).
  - [ ] Both sources are `sequenceId`-ordered (via the new all-flow and existing per-subscription flow respectively).
  - [ ] Add `markAsDeleted(id: String)` coroutine delegating to `Repository`.

- [ ] Create `FeedActivity` (AC: 1–5)
  - [ ] Create `app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt` extending `AppCompatActivity`.
  - [ ] Accept an optional `EXTRA_SUBSCRIPTION_ID: Long` and `EXTRA_SUBSCRIPTION_TOPIC: String?` Intent extras to determine All vs per-topic mode.
  - [ ] Accept an optional `EXTRA_DEEP_LINK_NOTIFICATION_ID: String?` Intent extra for deep-link scrolling.
  - [ ] Inflate a new layout `activity_feed.xml` (see layout task below).
  - [ ] Wire up `FeedViewModel` via `viewModels<FeedViewModel>`.
  - [ ] Observe the appropriate LiveData source and submit list to `FeedAdapter`.
  - [ ] Handle deep-link: on list update, if `deepLinkNotificationId` is set and not yet consumed, find its position via `adapter.currentList.indexOfFirst { it.id == deepLinkId }`, scroll to it, and pass the id to the adapter as the `deepLinkTargetId` for Story 2.6 highlight.
  - [ ] Do NOT call `startActivity()` for any notification tap — the tap is handled by `MessageCardBinder` (mark-as-read only).
  - [ ] Keep no reference to `DetailActivity` — if existing code in `MainActivity` calls `startDetailView()` pointing at `DetailActivity`, leave that intact; this story only adds the new feed surface. The nav wiring from `MainActivity` → `FeedActivity` is done in Epic 4 Story 4.6 (drawer/app-bar).

- [ ] Create `FeedAdapter` (AC: 1, 2, 4)
  - [ ] Create `app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt`.
  - [ ] Extend `ListAdapter<Notification, MessageCardBinder.ViewHolder>` using `Notification.id` in `DiffUtil.ItemCallback`.
  - [ ] `onCreateViewHolder`: inflate `fragment_detail_item.xml` (the Epic 2 card layout — do not create a new layout) and construct a `MessageCardBinder.ViewHolder`.
  - [ ] `onBindViewHolder`: call `MessageCardBinder.bind(holder, notification, topicName, newlyArrivedIds, deepLinkTargetId)` passing through all state from the activity. Do not re-implement any binding logic here.
  - [ ] Expose `fun setDeepLinkTargetId(id: String?)` and `fun setNewlyArrivedIds(ids: Set<String>)` — the activity calls these before/after `submitList`.
  - [ ] The `topicName` parameter is the subscription topic string in All mode and `null` in per-topic mode.

- [ ] Create `activity_feed.xml` layout (AC: 1)
  - [ ] Create `app/src/main/res/layout/activity_feed.xml`.
  - [ ] Root: `CoordinatorLayout`.
  - [ ] Contains: `RecyclerView` with id `@+id/feed_recycler`, `LinearLayoutManager` (vertical), no `clipToPadding` issues.
  - [ ] Add an `ItemDecoration` in code (not XML) that adds `@dimen/spacing_4` (18dp) vertical spacing between cards. Use a simple `DividerItemDecoration`-style decorator that adds bottom margin of 18dp to each item.
  - [ ] No placeholder for the app bar, FAB, or drawer — those are Epic 4.6's responsibility. This story delivers the bare RecyclerView surface only.
  - [ ] Do NOT add a `SwipeRefreshLayout` here — that is not in this story's ACs.

- [ ] Register `FeedActivity` in AndroidManifest.xml (AC: 1)
  - [ ] Add `<activity android:name=".ui.FeedActivity" ... />` to `AndroidManifest.xml`.
  - [ ] No intent filters yet (deep-link routing from system notifications is handled in Epic 4.6 or existing notification handling code).

- [ ] Wire deep-link intent handling (AC: 3)
  - [ ] In `FeedActivity.onCreate()` and `onNewIntent()`, extract `EXTRA_DEEP_LINK_NOTIFICATION_ID` from the intent.
  - [ ] After the first non-empty `submitList` callback, find the target index and call `recyclerView.smoothScrollToPosition(index)`.
  - [ ] Pass the target id to `FeedAdapter.setDeepLinkTargetId(id)` so `MessageCardBinder` applies the Story 2.6 highlight.
  - [ ] Consume the deep-link id (set to null) after the first scroll so re-submits don't re-trigger it.

- [ ] Add automated tests (AC: 1–5)
  - [ ] DAO test: `listAllFlow()` returns notifications from multiple subscriptions sorted by `sequenceId` desc; null-sequenceId rows sort after the rest deterministically.
  - [ ] DAO test: per-subscription `listFlow(subscriptionId)` is also `sequenceId`-ordered (regression guard for Epic 0).
  - [ ] `FeedAdapter` unit test: `onBindViewHolder` calls `MessageCardBinder.bind` with correct `topicName` (non-null in All mode, null in per-topic mode) and correct `deepLinkTargetId`.
  - [ ] Integration test (Robolectric or instrumented): starting `FeedActivity` with `EXTRA_DEEP_LINK_NOTIFICATION_ID` triggers a scroll to the matching item position.

## Dev Notes

### Critical: What this story is and is NOT

**This story delivers:**
- A new `FeedActivity` with a `RecyclerView` that renders the Epic 2 `MessageCardBinder` cards
- A new `FeedViewModel` + repository method for the all-notifications (cross-subscription) query
- Deep-link scroll-to-position + highlight pass-through
- All vs per-topic mode (toggled by Intent extra)

**This story does NOT deliver:**
- The app bar, hamburger, drawer, or FAB — those are Story 4.6
- Pagination (infinite scroll) — that is Story 4.2
- Feed container states (skeleton, empty panel, disconnected) — that is Story 4.3
- Topic chip conditional rendering logic — the binder already handles this (null topic name = no chip); Story 4.4 formalizes the mode switch
- Swipe gestures — Story 4.5 attaches `ItemTouchHelper` to this RV
- Any modification to `DetailActivity` or `MainActivity` — leave them untouched

### Architecture constraints (non-negotiable)

- **No Jetpack Compose.** The entire app is View/XML + AppCompat. Use View/XML everywhere.
- **Reuse `MessageCardBinder` / `fragment_detail_item.xml` exactly as delivered by Epic 2.** Do not copy, re-implement, or fork the card layout or binder. The entire value of the Epic 2 design decision is that the feed swap incurs zero card rework.
- **No direct reference to `DetailActivity` in `FeedActivity`.** The old drill-down remains for now; this story adds the new surface in parallel.
- **DAO ordering: `sequenceId DESC`, never `timestamp DESC` alone.** See NFR8. Epic 0 Story 0.2 owns the per-subscription query ordering; this story adds the all-subscriptions variant with the same ordering guarantee.

### Existing code to understand before implementing

**`DetailActivity`** ([app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt](app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt))
- This is the per-topic message list Activity being superseded. Study its LiveData observation pattern and deep-link handling (it scrolls to top on new notification). Do NOT modify it.
- Uses `DetailViewModel.listFiltered(subscriptionId)` → `repository.getNotificationsLiveData(subscriptionId)` → `notificationDao.listFlow(subscriptionId)`.
- The current `listFlow` orders by `timestamp DESC` — **this is what Story 0.2 must change to `sequenceId DESC`**. Verify before writing the new all-subscriptions query.

**`NotificationDao`** ([app/src/main/java/io/heckel/ntfy/db/Database.kt:586–650](app/src/main/java/io/heckel/ntfy/db/Database.kt))
- `listFlow(subscriptionId)` at line 590: `ORDER BY timestamp DESC` — if Story 0.2 hasn't run yet, this needs updating too (but story 0.2 owns it; check the story 0.2 file for status).
- Add `listAllFlow()` here for the all-subscriptions feed: `SELECT * FROM notification WHERE deleted != 1 ORDER BY CAST(sequenceId AS INTEGER) DESC, timestamp DESC, id DESC`.
  - Cast `sequenceId` to INTEGER for numeric sort (it is stored as a String in the schema — see `Notification.sequenceId: String`).
  - Tiebreaker: `timestamp DESC, id DESC` for null/empty sequenceId rows.

**`Notification` entity** ([app/src/main/java/io/heckel/ntfy/db/Database.kt:145–186](app/src/main/java/io/heckel/ntfy/db/Database.kt))
- `sequenceId: String` (not nullable — may be empty string `""` for legacy rows, not SQL NULL)
- `id: String` (primary key component alongside `subscriptionId: Long`)
- `deleted: Boolean` — filter `WHERE deleted != 1`
- `tags: String` — comma-separated, used by binder to build chip list

**`Repository.getNotificationsLiveData`** ([app/src/main/java/io/heckel/ntfy/db/Repository.kt:123](app/src/main/java/io/heckel/ntfy/db/Repository.kt))
- Pattern to follow for the new `getAllNotificationsLiveData()`: `notificationDao.listAllFlow().asLiveData()`

**`DetailViewModel`** ([app/src/main/java/io/heckel/ntfy/ui/DetailViewModel.kt](app/src/main/java/io/heckel/ntfy/ui/DetailViewModel.kt))
- Pattern to follow for `FeedViewModel`: simple ViewModel delegating to Repository, no search filter needed for this story.

**`MessageCardBinder`** (Epic 2 output — do not modify)
- The binder's `bind()` method signature (as established by Epic 2): accepts `(holder, notification, topicName: String?, newlyArrivedIds: Set<String>, deepLinkTargetId: String?)`.
- The `topicName` parameter controls topic chip visibility: non-null shows chip, null hides it.
- The `deepLinkTargetId` parameter triggers Story 2.6 highlight on the matching card.
- The binder handles mark-as-read on tap (Story 2.5) — do not re-implement.
- `fragment_detail_item.xml` is the layout to inflate in `FeedAdapter.onCreateViewHolder` — same file used by Epic 2.

### Deep-link flow

System notifications (`NotificationService`) currently call `startActivity(DetailActivity)` with a notification ID extra. Epic 4.6 will reroute these to `FeedActivity`. For this story, implement the receiving side only:
1. `FeedActivity` reads `intent.getStringExtra(EXTRA_DEEP_LINK_NOTIFICATION_ID)` in `onCreate` and `onNewIntent`.
2. Observe the list LiveData. In the observer, after `adapter.submitList(list)`, if `deepLinkNotificationId != null`, find `list.indexOfFirst { it.id == deepLinkNotificationId }`, scroll to it, set `adapter.setDeepLinkTargetId(deepLinkNotificationId)`, then clear the field.
3. The `MessageCardBinder` (Story 2.6) does the highlight animation — the feed only scrolls.

### 18dp card gap implementation

Do NOT add margin to `fragment_detail_item.xml` (owned by Epic 2 — file-collision guard).
Instead, attach an `ItemDecoration` in `FeedActivity`:

```kotlin
recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos != 0) outRect.top = resources.getDimensionPixelSize(R.dimen.spacing_4) // 18dp token
    }
})
```

Use `@dimen/spacing_4` (18dp) from the Epic 1.2 tokens — do not hard-code `18.dp`.

### All-subscriptions vs per-topic mode

```kotlin
// FeedActivity.kt
companion object {
    const val EXTRA_SUBSCRIPTION_ID = "subscriptionId"   // Long, 0 = All mode
    const val EXTRA_SUBSCRIPTION_TOPIC = "subscriptionTopic" // String?, null = All mode
    const val EXTRA_DEEP_LINK_NOTIFICATION_ID = "deepLinkNotificationId" // String?
}
```

In the Activity's observer block:
- If `subscriptionId == 0L` → use `viewModel.listAll()`, pass `topicName = topic` per card (each notification's subscription topic, not a fixed string — see note below)
- If `subscriptionId != 0L` → use `viewModel.listForSubscription(subscriptionId)`, pass `topicName = null`

**Important for All-mode topic chip:** In All mode, each card must show its own topic name. The `Notification` entity does NOT contain `topic` directly — it has `subscriptionId`. You must join or look up the topic name. Options:
- Preload a `Map<Long, String>` of `subscriptionId → topic` from `Repository.getSubscriptions()` and refresh it when subscriptions change.
- Or add a `listAllFlowWithTopic()` DAO query that JOINs `notification` and `subscription` tables — cleaner but requires a new data class.
- Recommended: preload the subscription map in `FeedViewModel` as a second LiveData (`getSubscriptionsLiveData()`) and combine with `MediatorLiveData` or `combineWith` extension. The `topicName` per card = `subscriptionMap[notification.subscriptionId]?.topic`.
- Do NOT expose the subscription object to the adapter — just the resolved `String?` topic name per item. Consider wrapping as `data class FeedItem(val notification: Notification, val topicName: String?)` as the adapter's data type for All mode.

### File locations

| File | Path |
|------|------|
| `FeedActivity.kt` | `app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt` |
| `FeedViewModel.kt` | `app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt` |
| `FeedAdapter.kt` | `app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt` |
| `activity_feed.xml` | `app/src/main/res/layout/activity_feed.xml` |
| DAO addition | `app/src/main/java/io/heckel/ntfy/db/Database.kt` (NotificationDao) |
| Repository addition | `app/src/main/java/io/heckel/ntfy/db/Repository.kt` |

### What MUST NOT be modified

- `fragment_detail_item.xml` — Epic 2 ownership. Use it; do not edit it.
- `MessageCardBinder.kt` — Epic 2 ownership. Call it; do not modify it.
- `DetailActivity.kt` — leave untouched; nav rerouting is Story 4.6.
- `MainActivity.kt` — leave untouched; drawer is Story 4.6.
- `Database.kt` DB version / migrations — Story 0.2 owns `sequenceId` column; do not add new migrations here.

### Testing approach

- **Unit tests** for `FeedViewModel` using a fake Repository (same pattern as `DetailViewModel`).
- **DAO tests** using Room's in-memory DB (same approach as any existing DAO test in the project). Assert `listAllFlow()` ordering across multiple subscriptions.
- **Robolectric test** for `FeedActivity` deep-link: create activity with `EXTRA_DEEP_LINK_NOTIFICATION_ID`, submit a list containing that id, assert `RecyclerView.getLayoutManager().findFirstVisibleItemPosition()` is at the target index after the scroll.
- **Adapter test**: mock `MessageCardBinder`; assert `bind()` is called with `topicName = null` in per-topic mode and `topicName = "my-topic"` in All mode for a notification belonging to that subscription.

### References

- Epic 4 Story 4.1 spec: [_bmad-output/planning-artifacts/epics.md#Story-4.1](_bmad-output/planning-artifacts/epics.md) lines 483–498
- Card binder (Epic 2): `fragment_detail_item.xml` + `MessageCardBinder` — Epic 2 output, do not modify
- DAO pattern: [app/src/main/java/io/heckel/ntfy/db/Database.kt:586-650](app/src/main/java/io/heckel/ntfy/db/Database.kt) — `NotificationDao`
- Repository pattern: [app/src/main/java/io/heckel/ntfy/db/Repository.kt:123-125](app/src/main/java/io/heckel/ntfy/db/Repository.kt)
- `Notification` entity (incl. `sequenceId: String`): [app/src/main/java/io/heckel/ntfy/db/Database.kt:145-186](app/src/main/java/io/heckel/ntfy/db/Database.kt)
- Feed spec (gap, order, pagination, deep-link): [docs/ui-parity/screens-layout.md](docs/ui-parity/screens-layout.md) §Feed
- 18dp token: `@dimen/spacing_4` from Epic 1.2 ([docs/ui-parity/design-tokens.md](docs/ui-parity/design-tokens.md))
- Deep-link highlight: Story 2.6 binder API (AC 4, Task "Keep target lookup outside the binder")
- Tag-chip topic name conditional: [docs/ui-parity/components.md](docs/ui-parity/components.md) §3 CardTags — "Shown when a topic name is provided"
- NFR8 (sequenceId ordering): [_bmad-output/planning-artifacts/epics.md](_bmad-output/planning-artifacts/epics.md) NFR8
- FR7 (single feed shell): epics.md FR7
- FR8 (feed ordering, 18dp gap, 20-per-page): epics.md FR8
- Feed serialization note: epics.md line 498 — "Stories 4.1 → 4.2 → 4.5 touch the same feed RV/adapter and must land in order."

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
