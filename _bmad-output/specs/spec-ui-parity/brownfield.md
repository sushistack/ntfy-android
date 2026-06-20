# Brownfield Notes — current ntfy-android vs. the parity target

Current state of `io.heckel.ntfy` that downstream architecture/epics must reconcile against the
target described in SPEC.md and the `docs/ui-parity/*` companions.

## Stack

- **View/XML + AppCompat** (`androidx.appcompat:appcompat:1.7.1`). **No Jetpack Compose** in the
  build today. The structured-card renderer (charts via Canvas, markdown, dynamic kv layout) is the
  piece most likely to motivate introducing Compose — this is the open question in SPEC.md.
- Package layout: `app/src/main/java/io/heckel/ntfy/{ui,db,msg,service,up,work,util,backup,app}`.

## Navigation gap (the biggest structural delta)

Current shell does **not** match the single-feed + drawer target:

| current | file(s) | target |
|---------|---------|--------|
| `MainActivity` = list of **subscriptions** (topics) | `MainActivity.kt`, `MainAdapter.kt`, `activity_main.xml`, `fragment_main_item.xml` | drawer lists topics; the main surface is the **feed** |
| `DetailActivity` = per-topic **message list** (opened by tapping a subscription) | `DetailActivity.kt`, `DetailAdapter.kt`, `activity_detail.xml`, `fragment_detail_item.xml` | this becomes the **feed** (All + per-topic), no separate Activity to "enter" |
| `DetailSettingsActivity` = per-subscription settings | `DetailSettingsActivity.kt` | folds into drawer row ⋯ menu (Rename/Clear/Unsubscribe/Mute) |
| `SettingsActivity` | `SettingsActivity.kt`, `activity_settings.xml` | Settings destination (largely unchanged behavior, re-skinned) |
| `PublishFragment` / `fragment_publish_dialog.xml` | publish | publish FAB + bottom sheet (`components.md §9`) |
| `AddFragment` | subscribe dialog | drawer "Subscribe to topic" |

The current per-message row (`DetailAdapter` + `fragment_detail_item.xml`) is the closest thing to
the target notification card and is the natural place the redesigned card replaces.

## To remove (parity Non-goals already in the current app)

- The two-level **subscriptions → detail** drill-down as the primary nav model (replace with feed + drawer).
- Any per-message detail/expansion screen.
- Per-card mute/overflow controls that don't match the X-delete-only header.

(There is no bottom nav in the current app to remove, but confirm during implementation.)

## Carries over

- `db/` (Room) message/subscription model, `msg/`/`service/`/`work/` (notification delivery,
  WorkManager, connection), `up/` (UnifiedPush) — delivery/storage stays; this is a **UI** rework.
- The new `sequenceId`-descending feed ordering needs a server-sequence field on the message model;
  verify whether the current schema already stores it or whether it must be added.
