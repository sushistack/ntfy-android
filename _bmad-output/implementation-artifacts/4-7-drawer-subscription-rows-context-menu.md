# Story 4.7: Drawer Subscription Rows & Context Menu

Status: ready-for-dev

## Story

As a user,
I want each topic row in the navigation drawer to show its state and offer a context menu,
so that I can see unread/muted status at a glance and manage the subscription without leaving the feed.

## Acceptance Criteria

1. **Given** a subscription row in the drawer, **when** it renders, **then** it shows the anatomy:
   `[active bar] [message/chat icon] [name] [unread count] [muted indicator?] [⋯ button]`
   — left icon is a **chat bubble** (NOT a bell), as specified in `components.md §6`.

2. **Given** the active topic (currently selected feed), **when** its drawer row renders, **then**:
   - The far-left active bar (4dp wide, 16dp tall, `radius_full` pill) is colored `@color/accent_ui` with the `glow_accent_dot` shadow treatment (dark mode); it is transparent/gone for inactive rows.
   - The left icon color is `@color/accent_ui` (active) vs `@color/muted` (inactive).

3. **Given** a subscription with `newCount > 0`, **when** the row renders, **then** the unread count shows right-aligned as `caption`-style `@color/accent_ui` text, capped at `99+`; when `newCount == 0` the count is hidden.

4. **Given** a muted subscription (`mutedUntil == 1L` or `mutedUntil > System.currentTimeMillis()/1000`), **when** the row renders, **then** a **passive bell-off icon** appears just left of the ⋯ button with `contentDescription = "Notifications muted"` and `importantForAccessibility = "yes"` — it is NOT clickable (indicator only, no button behavior).

5. **Given** any subscription row, **when** the ⋯ button is tapped, **then** a `PopupMenu` opens anchored to the ⋯ button with items **in this exact order**:
   - **Mute** (label "Mute") — when subscription is currently unmuted (`mutedUntil == 0L`)
   - **Unmute** (label "Unmute") — when subscription is currently muted (`mutedUntil > 0L`); label flips dynamically
   - _(separator)_
   - **Rename** — opens an inline `EditText` dialog (AlertDialog with EditText pre-filled with current `displayName`) to set a custom name
   - **Clear** — opens a confirm dialog; on confirm calls `repository.markAllAsDeleted(subscriptionId)`
   - _(separator)_
   - **Unsubscribe** — opens a confirm dialog; on confirm calls `repository.removeSubscription(subscription)` (+ Firebase unsubscribe if `baseUrl == appBaseUrl`)

6. **Given** a subscription row, **when** it is tapped (not the ⋯ button), **then** the drawer closes and the feed navigates to that topic's per-topic feed — it **never** opens a per-message detail screen (`DetailActivity`). (Non-goal guard: do not launch `DetailActivity`.)

7. **Given** Mute is selected from the ⋯ menu, **when** the user has not yet selected a duration, **then** a mute duration picker dialog is shown (reuse `NotificationFragment` or an equivalent inline dialog offering the same duration options: 30 min · 1h · 2h · 8h · Tomorrow 8:30 AM · Forever). After selection, `repository.updateSubscription(subscription.copy(mutedUntil = <computed timestamp>))` is called and the row's muted indicator updates reactively.

8. **Given** Unmute is selected, **when** tapped, **then** `repository.updateSubscription(subscription.copy(mutedUntil = Repository.MUTED_UNTIL_SHOW_ALL))` is called immediately (no dialog needed) and the muted indicator disappears.

9. **Given** Rename is selected, **when** the user confirms a new name, **then** `repository.updateSubscription(subscription.copy(displayName = if (value.isBlank()) null else value.trim()))` is called and the row name updates reactively. Leaving the field blank resets to default (`topicShortUrl` fallback). The dialog follows the same pattern as `DetailSettingsActivity.loadDisplayNamePref()`.

10. **Given** the drawer rows, **when** any mutation (mute/unmute/rename/clear/unsubscribe) is completed, **then** the drawer row reflects the change reactively (via LiveData from `MainViewModel.list()`/`getSubscriptionsLiveData()`) without requiring a full drawer re-open.

## Tasks / Subtasks

- [ ] Create `fragment_drawer_subscription_item.xml` — new layout for a single drawer subscription row (AC: 1–4)
  - [ ] `ConstraintLayout` root, `match_parent` width, `wrap_content` height, `selectableItemBackground` foreground
  - [ ] Active bar: `View`, `@+id/drawer_item_active_bar`, 4dp wide × 16dp tall, `radius_full` shape, start-aligned, vertically centered; colored `@color/accent_ui` (active) or transparent (inactive); add `glow_accent_dot` elevation treatment in dark mode (see Story 1.2 glow rule)
  - [ ] Left icon: `ImageView`, `@+id/drawer_item_icon`, 20×20dp, `@drawable/ic_chat_bubble_24dp` (new drawable to add — see Dev Notes); tint `@color/accent_ui` (active) or `@color/muted` (inactive)
  - [ ] Name: `TextView`, `@+id/drawer_item_name`, `body_sm` text appearance, `@color/text` (primary), `maxLines=1`, `ellipsize=end`
  - [ ] Unread count: `TextView`, `@+id/drawer_item_unread`, `caption` text appearance, `@color/accent_ui`, right-aligned, gone when 0
  - [ ] Muted indicator: `ImageView`, `@+id/drawer_item_muted`, bell-off icon (`@drawable/ic_notifications_off_gray_outline_24dp` existing), `@color/muted` tint, `importantForAccessibility="yes"`, `contentDescription="Notifications muted"`, not clickable, gone when not muted
  - [ ] ⋯ button: `ImageButton`, `@+id/drawer_item_overflow`, 24dp, 3-dots icon (use existing or add `ic_more_vert_24dp`), `@color/muted` tint, `?attr/actionBarItemBackground` background for ripple

- [ ] Create `DrawerSubscriptionAdapter` — `ListAdapter<Subscription, DrawerSubscriptionViewHolder>` (AC: 1–6, 10)
  - [ ] Reuse `TopicDiffCallback` pattern from `MainAdapter` for efficient diffing
  - [ ] `DrawerSubscriptionViewHolder.bind(subscription, activeTopic)`: bind active bar visibility/color, icon tint, name (`displayName(appBaseUrl, subscription)` utility), unread count (cap `99+`), muted indicator visibility, overflow button click
  - [ ] Row click → callback to host (FeedActivity/shell from Story 4.6) to navigate to per-topic feed, close drawer; **no `DetailActivity` launch**
  - [ ] Overflow button click → show `PopupMenu` anchored to the `⋯` button (AC: 5–9)

- [ ] Implement `PopupMenu` context menu in the ViewHolder (AC: 5–9)
  - [ ] Create `res/menu/menu_drawer_subscription_overflow.xml` with items: `drawer_sub_menu_mute`, `drawer_sub_menu_unmute`, _(group separator)_, `drawer_sub_menu_rename`, `drawer_sub_menu_clear`, _(group separator)_, `drawer_sub_menu_unsubscribe`; use `android:visible="false"` for the correct mute/unmute item to start hidden
  - [ ] Before showing, set `mute` item visible when `mutedUntil == 0L`; set `unmute` item visible otherwise (exactly one is visible at a time)
  - [ ] Mute click → show mute duration dialog (see Task below)
  - [ ] Unmute click → call `updateSubscription(mutedUntil = MUTED_UNTIL_SHOW_ALL)` via coroutine
  - [ ] Rename click → show rename AlertDialog with `EditText` (see Task below)
  - [ ] Clear click → show confirm AlertDialog; on confirm → `markAllAsDeleted(subscriptionId)` via coroutine
  - [ ] Unsubscribe click → show confirm AlertDialog; on confirm → `removeSubscription(subscription)` + Firebase unsubscribe if needed, via coroutine; host/activity notified to close drawer / navigate to All feed

- [ ] Implement mute duration dialog (AC: 7)
  - [ ] Re-open and reuse `NotificationFragment` (it already handles the duration picker and fires `onNotificationMutedUntilChanged`); pass the subscription id through callbacks, OR implement an inline `AlertDialog` with the same duration options (matching `NotificationFragment` durations verbatim: 30min / 1h / 2h / 8h / Tomorrow 8:30 / Forever) to avoid coupling to `NotificationFragment`'s global-mute interface
  - [ ] On selection, compute the `mutedUntil` timestamp using the same calendar arithmetic as `DetailSettingsActivity.loadMutedUntilPref()` and call `repository.updateSubscription(subscription.copy(mutedUntil = <computed>))`

- [ ] Implement rename dialog (AC: 9)
  - [ ] `MaterialAlertDialogBuilder` with an `EditText` pre-filled with `subscription.displayName ?: ""`; hint = `topicShortUrl(subscription.baseUrl, subscription.topic)` as placeholder
  - [ ] On confirm: `repository.updateSubscription(subscription.copy(displayName = if (text.isBlank()) null else text.trim()))` via coroutine
  - [ ] Follow the same logic as `DetailSettingsActivity.SettingsFragment.loadDisplayNamePref()` (line ~391)

- [ ] Wire adapter into Story 4.6's drawer layout (AC: 10)
  - [ ] In the FeedActivity/shell drawer panel (built in Story 4.6), replace or populate the subscription-list slot with a `RecyclerView` bound to `DrawerSubscriptionAdapter`
  - [ ] Observe `MainViewModel.list()` (or `getSubscriptionsLiveData()`) and submit the list to the adapter; pass the currently-active topic as a second argument to `bind()`
  - [ ] When unsubscribe completes, if the unsubscribed topic was the active one, navigate to All feed

- [ ] Add chat-bubble drawable (AC: 1)
  - [ ] Add `res/drawable/ic_chat_bubble_24dp.xml` — a filled chat bubble vector matching Material Design `chat_bubble` (24×24dp viewBox). If `com.google.android.material` already includes a bundled chat icon accessible via `R.drawable.*`, use that instead of adding a new file.

- [ ] Add string resources (AC: 5, 7–9)
  - [ ] `drawer_sub_menu_mute` = "Mute"
  - [ ] `drawer_sub_menu_unmute` = "Unmute"
  - [ ] `drawer_sub_menu_rename` = "Rename"
  - [ ] `drawer_sub_menu_clear` = "Clear"
  - [ ] `drawer_sub_menu_unsubscribe` = "Unsubscribe"
  - [ ] `drawer_sub_rename_hint` = "Display name (leave blank for default)"
  - [ ] `drawer_sub_clear_dialog_message` = "Delete all notifications for this subscription?"
  - [ ] `drawer_sub_clear_dialog_confirm` = "Delete"
  - [ ] `drawer_sub_unsubscribe_dialog_message` = "Unsubscribe from this topic?"
  - [ ] `drawer_sub_unsubscribe_dialog_confirm` = "Unsubscribe"
  - [ ] Add muted indicator `contentDescription` = "Notifications muted" as `drawer_sub_muted_content_description`

- [ ] Tests (AC: 1–10)
  - [ ] Layout test: `drawer_item_active_bar` is 4dp wide, `drawer_item_icon` uses chat icon NOT bell icon
  - [ ] Adapter test: muted indicator visibility matches `mutedUntil` state; unread count shows/hides and caps at `99+`; Mute menu item visible only when unmuted and Unmute only when muted
  - [ ] Rename test: blank input → `displayName = null`; non-blank → trimmed string
  - [ ] Navigation guard test: row click fires navigate-to-topic callback, NOT `DetailActivity` intent

## Dev Notes

### Story Dependency: 4.6 must land first

Story 4.6 builds the drawer/app-bar scaffold — the `DrawerLayout`, the drawer panel's `NavigationView` or `LinearLayout`, and the `RecyclerView` slot for subscription rows. **4.7 populates that slot.** Do not touch `activity_feed.xml` (or equivalent) beyond connecting the adapter to the existing `RecyclerView` id defined in 4.6.

### Current State — What Exists Today

The existing subscription list lives in `MainActivity` + `MainAdapter` + `fragment_main_item.xml`:
- [app/src/main/java/io/heckel/ntfy/ui/MainAdapter.kt](app/src/main/java/io/heckel/ntfy/ui/MainAdapter.kt) — current `ListAdapter<Subscription, SubscriptionViewHolder>`; uses `DiffUtil`, reuses `displayName()` util, handles selection for multi-delete action mode. **Do NOT modify this file** — it belongs to `MainActivity`, not the new feed shell.
- [app/src/main/res/layout/fragment_main_item.xml](app/src/main/res/layout/fragment_main_item.xml) — current subscription row layout (image, name, status, date, icons, badge). **Do NOT modify** — the new drawer row gets its own layout.
- [app/src/main/java/io/heckel/ntfy/ui/MainViewModel.kt](app/src/main/java/io/heckel/ntfy/ui/MainViewModel.kt) — exposes `list()` = `getSubscriptionsLiveData()`, `add()`, `remove()`. **Reuse** — the drawer adapter observes the same `SubscriptionsViewModel` LiveData.

### Repository Methods to Use (No New DB Work Needed)

All needed methods exist in [app/src/main/java/io/heckel/ntfy/db/Repository.kt](app/src/main/java/io/heckel/ntfy/db/Repository.kt):
- `updateSubscription(subscription: Subscription)` — for mute, unmute, rename (line ~91)
- `markAllAsDeleted(subscriptionId: Long)` — for Clear (line ~180)
- `removeSubscription(subscription: Subscription)` — for Unsubscribe (line ~101)
- `MUTED_UNTIL_SHOW_ALL = 0L`, `MUTED_UNTIL_FOREVER = 1L`, `MUTED_UNTIL_TOMORROW` constant — same constants as used in `DetailSettingsActivity`

All calls must be dispatched to `Dispatchers.IO` via `lifecycleScope.launch(Dispatchers.IO) { … }` or the host activity's coroutine scope.

### Mute Logic — Reuse DetailActivity Pattern

The mute/unmute calendar arithmetic is already implemented twice — in `DetailSettingsActivity.SettingsFragment.loadMutedUntilPref()` (~line 222) and `NotificationFragment`. The **duration options and timestamp computation** must match exactly:
- Forever → `mutedUntil = Repository.MUTED_UNTIL_FOREVER` (= 1L)
- Tomorrow 8:30 AM → `Calendar.getInstance().apply { add(DAY_OF_MONTH,1); set(HOUR_OF_DAY,8); set(MINUTE,30); set(SECOND,0); set(MILLISECOND,0) }.timeInMillis/1000`
- N minutes → `System.currentTimeMillis()/1000 + N * 60`

A subscription is **currently muted** when: `mutedUntil == 1L || (mutedUntil > 1L && mutedUntil > System.currentTimeMillis()/1000)`. The `MainAdapter.bind()` method (line ~112 in MainAdapter) already shows this logic for the global muted check — mirror it per-subscription.

### Rename — Reuse DisplayName Pattern

The `displayName()` utility in [app/src/main/java/io/heckel/ntfy/util/Util.kt](app/src/main/java/io/heckel/ntfy/util/Util.kt) returns `subscription.displayName ?: topicShortUrl(baseUrl, topic)`. The rename dialog should:
1. Pre-fill `EditText` with `subscription.displayName ?: ""`
2. On confirm: `subscription.copy(displayName = if (input.isBlank()) null else input.trim())`
3. The row name re-renders from LiveData update — no manual call needed.

### Unsubscribe — Firebase Cleanup

When unsubscribing from an ntfy.sh topic (`subscription.baseUrl == appBaseUrl`), also call `messenger.unsubscribe(subscription.topic)` — same pattern as `DetailActivity.onDeleteClick()` (line ~916–920). The `FirebaseMessenger` instance must be available in the host activity/fragment.

### Chat Bubble Icon — No Bell

The spec is explicit: **left icon = chat bubble, NOT a bell**. `ic_sms_gray_24dp` (current MainAdapter icon) is an SMS icon — acceptable fallback if a proper chat-bubble drawable is not available, but a `ic_chat_bubble_24dp` Material Design vector is preferred. Check if Material icons bundled with the `com.google.android.material` dependency include one before adding a new XML vector. The existing `ic_sms_gray_24dp.xml` icon **must not** appear in the drawer rows.

### Active Bar Glow — Dark Mode Only

`glow_accent_dot` (`0 0 7px #42D392`) applies to the active bar in dark mode only, per Story 1.2 definition. Use the shared glow rule defined in Story 1.2 (the single reusable glow-application helper). Do not hardcode the blur/opacity values.

### Unread Count — Same Badge as MainAdapter

The unread badge uses `subscription.newCount`. Cap at `99+` (same as `MainAdapter.bind()` line ~129). The count uses `@color/accent_ui` text — not the circular `ic_circle` badge background (that is for the old main list). The drawer row uses plain right-aligned text.

### PopupMenu vs ContextMenu

Use `android.widget.PopupMenu` (or `androidx.appcompat.widget.PopupMenu`) anchored to the ⋯ `ImageButton`, as done in `DetailAdapter.maybeCreateMenuPopup()` (line ~295). Do **not** use `ContextMenu` (registerForContextMenu) — it requires long-press and does not anchor to a specific view.

The menu XML should use `android:orderInCategory` to enforce item order since Android does not always guarantee declaration order. Apply `app:showAsAction="never"` to all items.

### Drawer Row Width

The drawer is 280dp wide (`width_nav_drawer`). The row layout must handle long subscription names with `maxLines=1` + `ellipsize=end`.

### Token Usage

All colors must be `@color/` token references — never raw hex:
- Active bar + icon (active): `@color/accent_ui`
- Icon (inactive), muted indicator, ⋯ button: `@color/muted`
- Name text: `@color/text` (primary)
- Unread count: `@color/accent_ui`

### Serialization with 4.6

Per the epic note: "Drawer serialization: Story 4.6 (drawer/app-bar scaffold) lands before Story 4.7 (topic rows + ⋯ menu); both touch the drawer layout." If 4.6 has not merged, create the adapter and menu XML as standalone files, and leave a clear TODO comment where the adapter is wired into the drawer RecyclerView. Do not modify the same drawer layout XML 4.6 is editing without coordinating.

### No-Op Guard: DetailActivity Must Not Be Launched

AC #6 is a Non-goal guard. The row click must call the host callback for feed navigation — never call `startActivity(Intent(context, DetailActivity::class.java))`. Add an architecture guard comment or a test assertion verifying no `DetailActivity` intent is created on row tap.

### Files to Create / Modify

| File | Action | Notes |
|------|--------|-------|
| `res/layout/fragment_drawer_subscription_item.xml` | CREATE | New drawer row layout |
| `res/menu/menu_drawer_subscription_overflow.xml` | CREATE | ⋯ popup menu XML |
| `res/drawable/ic_chat_bubble_24dp.xml` | CREATE (if needed) | Chat icon vector |
| `ui/DrawerSubscriptionAdapter.kt` | CREATE | New ListAdapter for drawer rows |
| `res/values/strings.xml` | MODIFY | Add drawer menu + dialog strings |
| FeedActivity/shell from 4.6 | MODIFY | Wire adapter to drawer RecyclerView, observe LiveData |

**Do NOT modify:** `MainAdapter.kt`, `fragment_main_item.xml`, `MainActivity.kt`, `activity_main.xml`, `DetailActivity.kt`, `DetailSettingsActivity.kt`.

### Project Structure

Package: `io.heckel.ntfy.ui` — same package as `MainAdapter`, `DetailAdapter`. Do not introduce a new sub-package unless the team has established one in earlier stories.

### References

- [docs/ui-parity/components.md §6](docs/ui-parity/components.md) — Sidebar/Navigation drawer spec, subscription row anatomy
- [docs/ui-parity/design-tokens.md](docs/ui-parity/design-tokens.md) — `accent_ui`, `muted`, `text`, `glow_accent_dot`, `radius_full` token values
- [app/src/main/java/io/heckel/ntfy/ui/MainAdapter.kt](app/src/main/java/io/heckel/ntfy/ui/MainAdapter.kt) — DiffUtil pattern, `displayName()` usage, muted icon logic (~line 112)
- [app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt](app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt) — `onClearClick()` (~878), `onDeleteClick()` (~909), mute logic (~756–790), Firebase unsubscribe (~920)
- [app/src/main/java/io/heckel/ntfy/ui/DetailSettingsActivity.kt](app/src/main/java/io/heckel/ntfy/ui/DetailSettingsActivity.kt) — `loadMutedUntilPref()` (~222), `loadDisplayNamePref()` (~391)
- [app/src/main/java/io/heckel/ntfy/ui/NotificationFragment.kt](app/src/main/java/io/heckel/ntfy/ui/NotificationFragment.kt) — mute duration dialog pattern
- [app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt](app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt) — `maybeCreateMenuPopup()` PopupMenu pattern (~294)
- [app/src/main/java/io/heckel/ntfy/db/Repository.kt](app/src/main/java/io/heckel/ntfy/db/Repository.kt) — `updateSubscription`, `markAllAsDeleted`, `removeSubscription`, mute constants
- [app/src/main/java/io/heckel/ntfy/util/Util.kt](app/src/main/java/io/heckel/ntfy/util/Util.kt) — `displayName()`, `topicShortUrl()`
- [_bmad-output/planning-artifacts/epics.md §Epic 4 Story 4.7](../planning-artifacts/epics.md) — canonical ACs

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
