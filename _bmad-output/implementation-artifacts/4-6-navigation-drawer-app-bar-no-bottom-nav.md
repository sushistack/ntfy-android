# Story 4.6: Navigation Drawer & App Bar (No Bottom Nav)

Status: ready-for-dev

## Story

As a user,
I want a hamburger drawer to switch between All, topics, Subscribe, and Settings,
so that navigation is a single drawer with no bottom bar.

## Acceptance Criteria

1. **App Bar Shell**
   - A 56dp (`actionBarSize`) top app bar is present showing:
     - Hamburger icon on the left that opens the navigation drawer
     - Center title showing the active topic name or "All notifications" (from string resource `nav_title_all_notifications`)
     - The title uses token color `@color/text`; app bar background uses `@color/surface`; bottom border uses `@color/border`

2. **Drawer Contents (top → bottom)**
   - "All notifications" row: grid/apps icon + label, navigates to the feed's All mode
   - Subscription rows (one per `Subscription` from `SubscriptionsViewModel.list()`): see AC 3
   - "Subscribe to topic" row: plus icon + label, opens `AddFragment` dialog
   - "Settings" row: gear icon + label, starts `SettingsActivity`

3. **No bottom navigation bar anywhere in the app** — if any `BottomNavigationView` exists, it is removed. (Confirmed: current codebase has none.)

4. **Publish FAB** — `accent_ui` fill (`@color/accent_ui`), `accent_on_surface` "+" icon (`@color/accent_on_surface`), `shadow_elev_2` elevation is always present on the feed surface, bottom-right corner (`bottom 24dp, end 24dp`).

5. **Drawer width** = 280dp (`@dimen/width_nav_drawer` — add if missing), left slide-in on mobile, opened via the app bar hamburger.

6. **Active state**: the currently-active destination (All or a specific topic) shows on the "All notifications" row or the matching subscription row, but row-level active bar / unread count / ⋯ menu visuals are deferred to Story 4.7 — this story only needs the drawer to open and navigate correctly.

7. **Theme compliance**: app bar and drawer use only token-keyed colors (Epic 1 resources); no raw hex. Drawer background = `@color/surface`, scrim uses `@color/overlay` (or system default).

## Tasks / Subtasks

- [ ] Task 1: Replace `MainActivity` subscriptions-list shell with the new feed shell layout (AC: 1, 5)
  - [ ] 1.1 Wrap `activity_main.xml` root in a `DrawerLayout` (`androidx.drawerlayout.widget.DrawerLayout`)
  - [ ] 1.2 Add a `NavigationView` or custom `LinearLayout` panel (280dp wide, `gravity=start`) as the drawer pane inside the `DrawerLayout`
  - [ ] 1.3 Move the existing `CoordinatorLayout` (app bar + content) inside the `DrawerLayout` as the main content child
  - [ ] 1.4 Add `@dimen/width_nav_drawer = 280dp` to `res/values/dimens.xml` if not already present
  - [ ] 1.5 Ensure `DrawerLayout` is `fitsSystemWindows="true"` for edge-to-edge compatibility

- [ ] Task 2: Wire the hamburger to open/close the drawer (AC: 1)
  - [ ] 2.1 In `MainActivity.onCreate`, call `setSupportActionBar(toolbar)` and configure `ActionBarDrawerToggle` (hamburger ↔ arrow icon tied to `DrawerLayout` open/close state)
  - [ ] 2.2 Override `onOptionsItemSelected` to delegate to `drawerToggle.onOptionsItemSelected(item)` first
  - [ ] 2.3 Update `app_bar_drawer.xml` toolbar so hamburger is the navigation icon (home-as-up)

- [ ] Task 3: Build the drawer item layout (AC: 2)
  - [ ] 3.1 Create `res/layout/drawer_item_row.xml` — a simple row: icon (24dp) + label (`body_sm` text, `@color/text`), 48dp min-height, `selectableItemBackground` foreground, horizontal `16dp` padding
  - [ ] 3.2 Create `res/layout/nav_drawer_content.xml` — a `LinearLayout` containing: static "All notifications" row (using `drawer_item_row`), a `RecyclerView` (`@+id/drawer_subscriptions_list`) for topic rows, divider, static "Subscribe to topic" row, static "Settings" row
  - [ ] 3.3 Include `nav_drawer_content.xml` as the drawer pane in `activity_main.xml`

- [ ] Task 4: Wire drawer items to navigation (AC: 2, 6)
  - [ ] 4.1 "All notifications" click → update `MainViewModel` active topic to null and swap feed destination (feed wired in Story 4.1 — for now, close drawer and update toolbar title to `nav_title_all_notifications`)
  - [ ] 4.2 "Subscribe to topic" click → close drawer, call `onSubscribeButtonClick()` (shows `AddFragment`)
  - [ ] 4.3 "Settings" click → close drawer, `startActivity(Intent(this, SettingsActivity::class.java))`
  - [ ] 4.4 Subscription row click → close drawer, call `startDetailView(subscription)` (transitional — Story 4.1 will replace this with feed mode switch)
  - [ ] 4.5 Create `DrawerSubscriptionAdapter` (minimal): single row, topic display name, click-through to topic feed — no active bar/unread/muted/menu (Story 4.7)

- [ ] Task 5: Apply token styling to app bar and drawer (AC: 7)
  - [ ] 5.1 Set app bar background to `@color/surface`, title text color to `@color/text`, hamburger icon tint to `@color/muted` (default, active state is `@color/accent_text`)
  - [ ] 5.2 Set drawer background to `@color/surface`
  - [ ] 5.3 Verify no raw hex literals in new layouts or code — use only `@color/` token references

- [ ] Task 6: Token-styled Publish FAB (AC: 4)
  - [ ] 6.1 Update the FAB in `activity_main.xml`: set `backgroundTint="@color/accent_ui"`, icon tint `@color/accent_on_surface`, `elevation` matching `@dimen/shadow_elev_2` (or `app:elevation="6dp"` as a proxy if dimen not yet defined)
  - [ ] 6.2 Confirm FAB is anchored bottom-end 24dp, always visible on the feed (not hidden)
  - [ ] 6.3 FAB click → still opens `AddFragment` (subscribe) for this story; publish sheet wiring is Story 4.8

- [ ] Task 7: String resources (AC: 1, 2)
  - [ ] 7.1 Add `nav_title_all_notifications` = "All notifications" to `strings.xml`
  - [ ] 7.2 Add `nav_drawer_subscribe` = "Subscribe to topic" (or reuse existing add string)
  - [ ] 7.3 Add `nav_drawer_settings` = "Settings" (or reuse existing settings string)
  - [ ] 7.4 Add `nav_drawer_open` / `nav_drawer_close` content descriptions for the toggle a11y

- [ ] Task 8: Preserve existing behavior (no regressions)
  - [ ] 8.1 All existing subscription long-click / action mode (multi-delete) behavior must still work
  - [ ] 8.2 All banners (battery, websocket, network) still appear in the content area above the list
  - [ ] 8.3 Back-press: if drawer is open, back closes it; does not exit the app
  - [ ] 8.4 `onSubscribe` callback from `AddFragment` still adds a subscription and navigates into it

## Dev Notes

### Architecture Decision: `DrawerLayout` wrapper approach

The cleanest path for this story is to wrap `activity_main.xml`'s root `CoordinatorLayout` inside a `DrawerLayout`. The existing `CoordinatorLayout` (app bar + content) becomes the main content child; a new `LinearLayout` drawer pane is added as the second child. **Do not** create a new Activity or fragment for this shell — `MainActivity` already owns the subscription list and the app lifecycle. The feed surface (Story 4.1) will later swap out the `RecyclerView` contents; the shell scaffolding added here is reused unchanged.

### Current `activity_main.xml` structure (must be preserved)

```
CoordinatorLayout (root, fitsSystemWindows)
  ├── include: app_bar_drawer (AppBarLayout + MaterialToolbar)
  └── ConstraintLayout (main content)
        ├── MaterialCardView banners (battery, websocket, network) — KEEP
        ├── SwipeRefreshLayout > RecyclerView (subscriptions list) — KEEP
        ├── LinearLayout (empty state, main_no_subscriptions) — KEEP
        └── FloatingActionButton (fab) — UPDATE colors
```

After this story:
```
DrawerLayout (new root, fitsSystemWindows)
  ├── CoordinatorLayout (existing — no change inside)
  │     ├── include: app_bar_drawer
  │     └── ConstraintLayout (same banners, list, empty state, FAB)
  └── LinearLayout (drawer pane, gravity=start, width=280dp)
        └── include: nav_drawer_content
```

### `ActionBarDrawerToggle` pattern

```kotlin
// In MainActivity.onCreate, after setSupportActionBar(toolbar):
drawerLayout = findViewById(R.id.drawer_layout)
drawerToggle = ActionBarDrawerToggle(
    this, drawerLayout, toolbar,
    R.string.nav_drawer_open,
    R.string.nav_drawer_close
)
drawerLayout.addDrawerListener(drawerToggle)
drawerToggle.syncState()
```

Delegate `onOptionsItemSelected` before custom handling:
```kotlin
if (drawerToggle.onOptionsItemSelected(item)) return true
```

And in `onPostCreate`:
```kotlin
override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    drawerToggle.syncState()
}
```

### DrawerSubscriptionAdapter (minimal for this story)

The `MainAdapter` (subscription list) already knows how to render a subscription row. For the drawer, create a minimal `DrawerSubscriptionAdapter` backed by `SubscriptionsViewModel.list()`. Each row = simple horizontal layout: icon (use existing `ic_sms_gray_24dp` or a chat-bubble icon) + topic display name (truncate). Click = close drawer + navigate. Full active bar / unread / muted / ⋯ menu are Story 4.7.

**Important:** the main `RecyclerView` (`main_subscriptions_list`) in the content area still shows the subscription list (as today) until Story 4.1 replaces it with the feed. Do not remove or hide the content-area list in this story.

### FAB current state

The FAB currently opens `AddFragment` (subscribe). This is **correct behavior for this story**. Story 4.8 will reroute it to the publish bottom sheet. Do not change FAB click behavior yet.

### Token references needed from Epic 1

These color tokens must exist (added in Story 1.1) before or alongside this story:
- `@color/surface` — app bar bg, drawer bg
- `@color/text` — toolbar title, drawer item labels
- `@color/muted` — hamburger icon default, inactive item icons
- `@color/accent_text` — active hamburger/icon
- `@color/accent_ui` — FAB background fill
- `@color/accent_on_surface` — FAB "+" icon color
- `@color/border` — app bar bottom border

If Story 1.1 has not landed yet, define temporary fallbacks using `?android:attr/colorPrimary` etc., but leave a `// TODO: replace with @color/token when Epic 1 lands` comment.

### No bottom nav to remove

A grep of all layout XMLs for `BottomNavigationView` returns nothing — there is no existing bottom navigation bar to delete.

### Back-press / drawer handling

Override `onBackPressedDispatcher` or add an `addCallback` via `OnBackPressedDispatcher`:
```kotlin
onBackPressedDispatcher.addCallback(this) {
    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
        drawerLayout.closeDrawer(GravityCompat.START)
    } else {
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
    }
}
```

### Dependency on Story 4.1

Story 4.1 will replace the content-area subscription list with the single-feed `RecyclerView`. This story's drawer navigation (topic row click → `startDetailView`) is a transitional stub. When Story 4.1 lands, those click handlers are updated to switch the feed topic argument. **No file-collision risk**: this story owns `activity_main.xml` structure, `MainActivity.kt` drawer wiring, and new files (`nav_drawer_content.xml`, `drawer_item_row.xml`, `DrawerSubscriptionAdapter.kt`). Story 4.1 owns the RecyclerView inside the existing `ConstraintLayout` and the `DetailActivity` refactor.

### Serialization note

> **Drawer serialization:** Story 4.6 (drawer/app-bar scaffold) lands before Story 4.7 (topic rows + ⋯ menu); both touch the drawer layout.

Story 4.7 will extend `DrawerSubscriptionAdapter` with the full row anatomy. Plan the adapter to be extensible (a `ViewHolder` with clearly named field stubs for unread count, muted indicator, active bar, and overflow menu).

### Project Structure Notes

| File | Action | Owner |
|------|--------|-------|
| `app/src/main/res/layout/activity_main.xml` | UPDATE — wrap in `DrawerLayout`, add drawer pane | This story |
| `app/src/main/res/layout/app_bar_drawer.xml` | UPDATE — confirm toolbar is correct for `ActionBarDrawerToggle` | This story |
| `app/src/main/res/layout/nav_drawer_content.xml` | NEW — drawer inner layout | This story |
| `app/src/main/res/layout/drawer_item_row.xml` | NEW — single drawer row | This story |
| `app/src/main/java/io/heckel/ntfy/ui/MainActivity.kt` | UPDATE — add `DrawerLayout`, toggle, back-press, drawer nav | This story |
| `app/src/main/java/io/heckel/ntfy/ui/DrawerSubscriptionAdapter.kt` | NEW — minimal drawer list adapter | This story |
| `app/src/main/res/values/strings.xml` | UPDATE — add nav string resources | This story |
| `app/src/main/res/values/dimens.xml` | UPDATE — add `width_nav_drawer = 280dp` | This story |

### References

- Story spec (epics.md §Epic 4 / Story 4.6): drawer contents, 56dp app bar, FAB anchor, 280dp width
- [Source: docs/ui-parity/components.md §6] — Sidebar/drawer row anatomy, active bar, menu structure
- [Source: docs/ui-parity/components.md §7] — Mobile App Bar: 56dp, hamburger left, center title, no bottom nav
- [Source: docs/ui-parity/components.md §8] — Publish FAB: `accent_ui` fill, `accent_on_surface` icon, `shadow_elev_2`
- [Source: docs/ui-parity/screens-layout.md §Navigation model] — no bottom nav, single drawer, FAB always present
- [Source: docs/ui-parity/design-tokens.md §Color Tokens] — `accent_ui`, `accent_on_surface`, `surface`, `text`, `muted`, `border`
- [Source: app/src/main/res/layout/activity_main.xml] — current layout structure to preserve
- [Source: app/src/main/java/io/heckel/ntfy/ui/MainActivity.kt] — existing drawer toggle, FAB, subscription navigation
- [Source: app/src/main/java/io/heckel/ntfy/ui/MainViewModel.kt (SubscriptionsViewModel)] — `list()` LiveData for subscriptions

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_None_

### Completion Notes List

_Ready for development._

### File List

_To be populated by the dev agent after implementation._
