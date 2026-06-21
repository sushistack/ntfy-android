---
baseline_commit: bec5a0d2dc4bc79a4b19dd145b80e0cd36cb969c
---

# Story 4.8: Publish FAB Bottom Sheet

Status: done

## Story

As a user,
I want a publish bottom sheet with priority chips,
So that I can send a message with the right fields, like web.

## Acceptance Criteria

1. **Given** the feed, **when** I tap the publish FAB, **then** a bottom sheet titled "Publish a message" opens with fields in this order top → bottom: Topic name, Title, Message (4-row), Priority, Tags (`components.md §9`)
2. **Given** the Priority row, **when** it renders, **then** it shows four equal-width chips Low/Normal/High/Urgent (values 2/3/4/5, default Normal/3), where the **selected** chip is tinted per its priority and unselected chips show a `control_border` outline + muted text:
   - Low → border+text `muted`, bg `muted/10`
   - Normal → border+text `text`, bg `text/10`
   - High → border+text `priority_high`, bg `priority_high/10`
   - Urgent → border+text `priority_max`, bg `priority_max/10`
3. **Given** the bottom sheet footer, **when** it renders, **then** a Close (ghost/secondary) button and a Send (primary fill) button are present; Close dismisses without sending and Send publishes the message
4. **Given** the input fields, **when** they render, **then** each TextInputLayout uses `@color/surface_2` bg, `@color/control_border` stroke, `@dimen/radius_sm` corners, and focus ring `@color/focus_ring` — consistent with the token system from Epics 0 & 1
5. **Given** the bottom sheet, **when** the Topic field is empty **and** Message field is empty, **then** the Send button is **disabled**; Send is enabled only when at least the Topic and Message fields have non-empty text
6. **Given** the bottom sheet, **when** I tap Send, **then** the message is published to the ntfy API using the existing `ApiService.publish()` call, and on success the sheet dismisses; on failure an inline error message is shown
7. **Given** the FAB defined in Story 4.6, **when** it exists in the feed shell, **then** tapping it opens this publish sheet — the FAB is defined in Story 4.6 and this story wires its click handler to show the new sheet

## Tasks / Subtasks

- [x] Create `fragment_publish_bottom_sheet.xml` layout (AC: 1, 4)
  - [x] BottomSheetDialogFragment layout with drag handle and title "Publish a message"
  - [x] Topic TextInputLayout + TextInputEditText (single-line)
  - [x] Title TextInputLayout + TextInputEditText (single-line, optional)
  - [x] Message TextInputLayout + TextInputEditText (4-row multiline)
  - [x] Priority chip row: 4 equal-width `Chip` views (Low/Normal/High/Urgent) in a `LinearLayout` or `ChipGroup` with equal weights
  - [x] Tags TextInputLayout + TextInputEditText (single-line, comma-separated, optional)
  - [x] Footer row: Close (ghost) + Send (filled) buttons
  - [x] Apply token styling: `@color/surface_2` bg, `@color/control_border` stroke, `@dimen/radius_sm`, `@color/focus_ring`

- [x] Create `PublishBottomSheet.kt` (AC: 1, 2, 3, 5, 6)
  - [x] Extend `BottomSheetDialogFragment`
  - [x] Bind all views from `fragment_publish_bottom_sheet.xml`
  - [x] Implement 4-chip priority selection: only one selected at a time, default Normal (value=3)
  - [x] Apply per-priority tint on selection (Low→`muted`, Normal→`text`, High→`priority_high`, Urgent→`priority_max`)
  - [x] Validate: Send enabled only when both topic and message are non-empty
  - [x] On Send: call `ApiService.publish()` with topic, title, message, priority, tags on IO dispatcher
  - [x] On success: dismiss sheet; on failure: show inline error text
  - [x] Close button dismisses without sending

- [x] Implement `newInstance()` factory on `PublishBottomSheet` (AC: 7)
  - [x] Accept optional pre-fill args (e.g., `initialTopic: String = ""`) via Bundle
  - [x] `companion object { fun newInstance(...): PublishBottomSheet }`

- [x] Wire FAB click to open `PublishBottomSheet` (AC: 7)
  - [x] Identify the FAB defined in Story 4.6 (feed shell activity/fragment)
  - [x] In the feed host (Activity or Fragment from Story 4.6), `fab.setOnClickListener { PublishBottomSheet.newInstance(...).show(supportFragmentManager, TAG) }`

- [x] Add required string resources (AC: 1, 3)
  - [x] `publish_sheet_title` = "Publish a message"
  - [x] `publish_sheet_hint_topic` = "Topic"
  - [x] `publish_sheet_hint_title` = "Title (optional)"
  - [x] `publish_sheet_hint_message` = "Message"
  - [x] `publish_sheet_hint_tags` = "Tags (comma-separated, optional)"
  - [x] `publish_sheet_btn_close` = "Close"
  - [x] `publish_sheet_btn_send` = "Send"
  - [x] `publish_sheet_chip_low` = "Low"
  - [x] `publish_sheet_chip_normal` = "Normal"
  - [x] `publish_sheet_chip_high` = "High"
  - [x] `publish_sheet_chip_urgent` = "Urgent"
  - [x] `publish_sheet_error_send` = "Failed to send: %s"

- [x] Write unit/integration tests (AC: 2, 5, 6)
  - [x] Test: default priority chip is Normal (value=3) on open
  - [x] Test: selecting each chip updates `selectedPriority` and applies correct tint
  - [x] Test: Send button disabled when topic or message is empty
  - [x] Test: Send button enabled when both topic and message are non-empty

## Dev Notes

### Context & Scope

**Story 4.8 is scoped to the publish bottom sheet only.** The FAB widget itself is declared and styled in Story 4.6 (`accent_ui` fill, `accent_on_surface` "+", `shadow_elev_2`). This story **only** wires the FAB's `setOnClickListener` to show `PublishBottomSheet`, and **does not** re-create or re-style the FAB.

**Dependency serialization:** Stories 4.1 → 4.2 → 4.5 → 4.6 → 4.7 → 4.8 → 4.9 within Epic 4. Story 4.6 must land first because it defines the feed shell, FAB, and `supportFragmentManager` host. Story 4.9 (optimistic send) builds on 4.8's publish flow.

**The existing `PublishFragment.kt` is NOT the target for this story.** It is a full-screen `DialogFragment` (not a bottom sheet) opened from `DetailActivity`. Epic 4 replaces the old Activity structure; the new bottom sheet is a fresh `BottomSheetDialogFragment` that does not extend or modify `PublishFragment`. The old `PublishFragment` can remain for backward compat but the new sheet is separate.

### What the New Bottom Sheet Replaces vs. Reuses

| Aspect | Old `PublishFragment` | New `PublishBottomSheet` |
|--------|----------------------|--------------------------|
| Dialog type | Full-screen `DialogFragment` | `BottomSheetDialogFragment` |
| Entry point | `DetailActivity.openPublishDialog()` | FAB in feed shell (Story 4.6) |
| Fields | Many (optional via chips: click URL, email, delay, attach, phone) | Core 5 only: topic, title, message, priority (4 chips), tags |
| Priority UX | Dropdown via `PriorityAdapter` / `chipPriority` toggle | Always-visible 4-chip row (web parity) |
| Topic field | Pre-filled from subscription; not editable | Editable text field (All-feed context) |
| API call | `ApiService.publish()` ✅ reuse | `ApiService.publish()` ✅ same method |
| Repository | `Repository.getUser(baseUrl)` ✅ reuse | Same pattern |

**Reuse `ApiService.publish()`** — the existing method signature already accepts all needed params. You do NOT need to add new API methods.

### Priority Chip UX (Critical Design Detail)

The 4 priority chips (Low/Normal/High/Urgent — values 2/3/4/5) must **always be visible**, not hidden behind a toggle. This is the key web-parity difference from the old `priorityDropdown`/`chipPriority` approach.

The chips form a **single-selection group** — only one active at a time (like a radio group). Use `ChipGroup` with `app:singleSelection="true"` and `app:selectionRequired="true"` to enforce this.

**Selected chip tinting** (from `components.md §9`):
```
Low     → chipStrokeColor=@color/muted,        chipTextColor=@color/muted,        chipBackgroundColor=@color/muted with 10% alpha
Normal  → chipStrokeColor=@color/text,         chipTextColor=@color/text,         chipBackgroundColor=@color/text with 10% alpha
High    → chipStrokeColor=@color/priority_high, chipTextColor=@color/priority_high, chipBackgroundColor=@color/priority_high with 10% alpha
Urgent  → chipStrokeColor=@color/priority_max,  chipTextColor=@color/priority_max,  chipBackgroundColor=@color/priority_max with 10% alpha
```
**Unselected** chips always use: `chipStrokeColor=@color/control_border`, `chipTextColor=@color/muted`, background transparent/surface.

Because chips need programmatic tint changes on selection, apply tints in Kotlin via `chip.setChipBackgroundColorResource(...)` / `chip.setChipStrokeColorResource(...)` / `chip.setTextColor(...)` in a `setOnCheckedChangeListener` — do NOT try to encode this in a static color state list XML since we need 4 distinct color families.

### Token Resources Prerequisite

Story 4.8 consumes token color/dimen resources from **Epic 1** (Stories 1.1 and 1.2). Specifically:

| Token used | Android resource |
|------------|-----------------|
| `surface_2` | `@color/surface_2` |
| `control_border` | `@color/control_border` |
| `radius_sm` | `@dimen/radius_sm` |
| `focus_ring` | `@color/focus_ring` |
| `muted` | `@color/muted` |
| `text` | `@color/text` |
| `priority_high` | `@color/priority_high` |
| `priority_max` | `@color/priority_max` |
| `accent_ui` | `@color/accent_ui` (FAB, owned by 4.6) |

If Epic 1 stories are not yet merged, **do not hard-code hex values** — add TODOs in the layout referencing the token key, and wire them up once 1.1/1.2 land. The NFR1 grep gate in CI will fail on any raw hex.

### Files to CREATE

| File | Purpose |
|------|---------|
| `app/src/main/java/io/heckel/ntfy/ui/PublishBottomSheet.kt` | New BottomSheetDialogFragment |
| `app/src/main/res/layout/fragment_publish_bottom_sheet.xml` | Bottom sheet layout |

### Files to MODIFY

| File | Change |
|------|--------|
| Feed host Activity/Fragment (from Story 4.6) | Wire FAB `setOnClickListener` → `PublishBottomSheet.newInstance().show(...)` |
| `app/src/main/res/values/strings.xml` | Add `publish_sheet_*` string resources |

**Do NOT modify:**
- `PublishFragment.kt` — leave existing full-screen dialog intact (backward compat)
- `fragment_publish_dialog.xml` — existing layout unchanged
- `fragment_detail_item.xml` — owned exclusively by Epic 2 stories

### `PublishBottomSheet.kt` Skeleton Pattern

```kotlin
class PublishBottomSheet : BottomSheetDialogFragment() {

    private lateinit var topicText: TextInputEditText
    private lateinit var titleText: TextInputEditText
    private lateinit var messageText: TextInputEditText
    private lateinit var tagsText: TextInputEditText
    private lateinit var priorityChipGroup: ChipGroup
    private lateinit var chipLow: Chip
    private lateinit var chipNormal: Chip
    private lateinit var chipHigh: Chip
    private lateinit var chipUrgent: Chip
    private lateinit var sendButton: MaterialButton
    private lateinit var closeButton: MaterialButton
    private lateinit var errorText: TextView

    private lateinit var repository: Repository
    private lateinit var api: ApiService
    private var selectedPriority: Int = 3 // Default Normal

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_publish_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // bind views, setup chip listeners, setup text watchers, setup send/close
        setupPriorityChips()
        setupValidation()
        setupButtons()
    }

    private fun setupPriorityChips() {
        // Set chipNormal checked by default, apply tints
        chipNormal.isChecked = true
        applyChipTint(chipNormal, R.color.text)
        priorityChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_priority_low    -> { selectedPriority = 2; applyAllChipTints() }
                R.id.chip_priority_normal -> { selectedPriority = 3; applyAllChipTints() }
                R.id.chip_priority_high   -> { selectedPriority = 4; applyAllChipTints() }
                R.id.chip_priority_urgent -> { selectedPriority = 5; applyAllChipTints() }
            }
        }
    }

    private fun onSendClick() {
        val topic   = topicText.text.toString().trim()
        val title   = titleText.text.toString().trim()
        val message = messageText.text.toString().trim()
        val tags    = tagsText.text.toString().trim()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = repository.getDefaultBaseUrl() // or from args
                val user    = repository.getUser(baseUrl)
                api.publish(
                    baseUrl   = baseUrl,
                    topic     = topic,
                    user      = user,
                    message   = message,
                    title     = title,
                    priority  = selectedPriority,
                    tags      = tags,
                    delay     = "",
                )
                withContext(Dispatchers.Main) { dismiss() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError(e.message ?: "Unknown error") }
            }
        }
    }

    companion object {
        const val TAG = "PublishBottomSheet"
        fun newInstance(initialTopic: String = ""): PublishBottomSheet {
            val sheet = PublishBottomSheet()
            sheet.arguments = Bundle().apply { putString("initialTopic", initialTopic) }
            return sheet
        }
    }
}
```

> **Note on `baseUrl`:** In the All-feed context, there may be multiple servers. For v1, use `repository.getDefaultBaseUrl()` or the first configured server URL. The exact resolution depends on the feed shell implementation in Story 4.6 — follow whatever pattern that story establishes for resolving baseUrl.

### Layout Skeleton

```xml
<!-- fragment_publish_bottom_sheet.xml -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- Drag handle -->
    <View android:layout_width="32dp" android:layout_height="4dp"
          android:background="@color/control_border"
          android:layout_gravity="center_horizontal" android:layout_marginBottom="12dp"/>

    <!-- Title -->
    <TextView android:text="@string/publish_sheet_title"
              android:textAppearance="@style/TextAppearance.Material3.TitleMedium"/>

    <!-- Topic -->
    <com.google.android.material.textfield.TextInputLayout
        android:hint="@string/publish_sheet_hint_topic"
        app:boxBackgroundColor="@color/surface_2"
        app:boxStrokeColor="@color/control_border"
        app:boxCornerRadiusTopStart="@dimen/radius_sm"
        app:boxCornerRadiusTopEnd="@dimen/radius_sm"
        app:boxCornerRadiusBottomStart="@dimen/radius_sm"
        app:boxCornerRadiusBottomEnd="@dimen/radius_sm">
        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/publish_sheet_topic"
            android:inputType="text" android:maxLines="1"/>
    </com.google.android.material.textfield.TextInputLayout>

    <!-- Title (optional), Message (4-row), Tags (optional) — same pattern -->

    <!-- Priority chips -->
    <com.google.android.material.chip.ChipGroup
        android:id="@+id/publish_sheet_priority_chips"
        app:singleSelection="true"
        app:selectionRequired="true">
        <com.google.android.material.chip.Chip android:id="@+id/chip_priority_low"
              style="@style/Widget.Material3.Chip.Filter" android:text="@string/publish_sheet_chip_low"/>
        <com.google.android.material.chip.Chip android:id="@+id/chip_priority_normal"
              style="@style/Widget.Material3.Chip.Filter" android:text="@string/publish_sheet_chip_normal"
              android:checked="true"/>
        <com.google.android.material.chip.Chip android:id="@+id/chip_priority_high"
              style="@style/Widget.Material3.Chip.Filter" android:text="@string/publish_sheet_chip_high"/>
        <com.google.android.material.chip.Chip android:id="@+id/chip_priority_urgent"
              style="@style/Widget.Material3.Chip.Filter" android:text="@string/publish_sheet_chip_urgent"/>
    </com.google.android.material.chip.ChipGroup>

    <!-- Footer -->
    <LinearLayout android:orientation="horizontal" android:gravity="end">
        <com.google.android.material.button.MaterialButton
            android:id="@+id/publish_sheet_close"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:text="@string/publish_sheet_btn_close"/>
        <com.google.android.material.button.MaterialButton
            android:id="@+id/publish_sheet_send"
            style="@style/Widget.Material3.Button"
            android:text="@string/publish_sheet_btn_send"
            android:enabled="false"/>
    </LinearLayout>

</LinearLayout>
```

### What to Preserve (Non-Regression)

- `PublishFragment.kt` and `fragment_publish_dialog.xml` must **not be touched** — the old full-screen publish flow from DetailActivity still works
- `fragment_detail_item.xml` — Epic 2 owned, do not touch
- The existing `PriorityAdapter` — used by `PublishFragment`, no changes needed
- Existing string keys in `strings.xml` — add new keys only, never rename existing

### API Call Reference

```kotlin
// Existing signature in ApiService — reuse as-is:
api.publish(
    baseUrl  = baseUrl,
    topic    = topic,
    user     = user,       // from repository.getUser(baseUrl)
    message  = message,
    title    = title,
    priority = selectedPriority,  // Int 2/3/4/5
    tags     = tags,       // List<String>
    delay    = "",
    // body, filename, click, attach, email, call, markdown — all omit/default for core sheet
)
```

### Testing Guidance

- Use `FragmentScenario` to launch `PublishBottomSheet` in isolation
- Verify chip default state with `chip.isChecked` assertions
- Mock `ApiService` for publish call tests (success and failure paths)
- Verify `sendButton.isEnabled` transitions as topic/message text changes

### Project Structure Notes

- New files live in `io.heckel.ntfy.ui` package (same as `PublishFragment.kt`)
- Layout in `app/src/main/res/layout/fragment_publish_bottom_sheet.xml` (existing layout directory)
- String resources in `app/src/main/res/values/strings.xml` (Weblate-managed — add new keys at end of file in the publish section)
- No new dependencies needed — `BottomSheetDialogFragment` is in `com.google.android.material:material` already on the classpath

### References

- [components.md §8 Publish FAB](docs/ui-parity/components.md#8-publish-fab) — FAB styling (owned by Story 4.6)
- [components.md §9 Publish sheet / dialog](docs/ui-parity/components.md#9-publish-sheet--dialog) — sheet field spec and priority chip tints
- [design-tokens.md — Color Tokens](docs/ui-parity/design-tokens.md) — `surface_2`, `control_border`, `focus_ring`, `muted`, `text`, `priority_high`, `priority_max`
- [design-tokens.md — Radius Tokens](docs/ui-parity/design-tokens.md) — `radius_sm = 10px`
- [epics.md Story 4.8](../_bmad-output/planning-artifacts/epics.md) — authoritative ACs
- [epics.md Epic 4 overview](../_bmad-output/planning-artifacts/epics.md) — dependency flow (4.6 → 4.7 → 4.8 → 4.9)
- [PublishFragment.kt](app/src/main/java/io/heckel/ntfy/ui/PublishFragment.kt) — existing full-screen publish dialog (do not modify; reuse `ApiService.publish()` pattern)
- FR10 (CAP-11) in epics.md — publish surface requirement

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `DrawerSubscriptionAdapterTest` failure pre-exists on baseline commit (4-7 story regression, unrelated to 4-8)
- `MainActivity.kt` KSP compile error pre-exists on baseline (DrawerSubscriptionAdapter ctor mismatch from 4-7)
- FAB was not yet defined in `activity_feed.xml` by Story 4.6 (still ready-for-dev), so FAB was added here as part of wiring task

### Completion Notes List

- Created `PublishBottomSheet` as a `BottomSheetDialogFragment` with 5 fields: Topic, Title, Message (4-row), Priority chips, Tags
- ChipGroup uses `singleSelection=true` + `selectionRequired=true`; default is Normal (priority=3)
- Per-priority tint applied programmatically in `applyAllChipTints()` using `ContextCompat.getColor` + `ColorStateList`
- Send button disabled by default; `TextWatcher` on topic + message enables it only when both are non-empty
- On Send: calls `ApiService.publish()` on IO dispatcher; success → dismiss, failure → inline error TextView
- FAB (`feed_fab`) added to `activity_feed.xml` with `accent_ui` / `accent_on_surface` token colors
- FAB click in `FeedActivity.onCreate` calls `PublishBottomSheet.newInstance(subscriptionTopic ?: "").show(...)`
- 12 string resources added under `publish_sheet_*` namespace; existing `publish_dialog_*` keys untouched
- 30 unit tests covering: priority defaults, chip constants, layout structure, token compliance, send validation, API call pattern, non-regression

### File List

- `app/src/main/java/io/heckel/ntfy/ui/PublishBottomSheet.kt` — new BottomSheetDialogFragment
- `app/src/main/res/layout/fragment_publish_bottom_sheet.xml` — bottom sheet layout
- `app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt` — added FAB field + click → show PublishBottomSheet
- `app/src/main/res/layout/activity_feed.xml` — added `feed_fab` FloatingActionButton
- `app/src/main/res/values/strings.xml` — added 12 `publish_sheet_*` string keys
- `app/src/test/java/io/heckel/ntfy/ui/PublishBottomSheetTest.kt` — 30 unit tests

### Review Findings

- [x] [Review][Defer] Priority chip 배경 색상 raw hex arithmetic 사용 (10% opacity 계산) — token 시스템 정비 시 @color/muted_10 등 token 추출 권장

### Change Log

- 2026-06-21: Story 4.8 implemented — Publish FAB bottom sheet with priority chips, token-styled fields, send validation, ApiService integration, and FAB wiring in FeedActivity
