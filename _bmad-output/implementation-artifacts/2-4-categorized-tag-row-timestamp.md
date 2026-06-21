# Story 2.4: Categorized Tag Row + Timestamp

---
baseline_commit: f0e2e90cc474feba3617fd798c5973f092df5d3f
---

Status: review

## Story

As a user,
I want the card meta row to show categorized tag chips and a right-aligned timestamp,
so that tags are color-stable and identical to web.

## Acceptance Criteria

1. **Given** a card with a non-null topic/display name, **when** its meta row renders, **then** the topic is the first chip, uses `@color/topic_chip_bg` and `@color/topic_chip_text`, and is omitted when the binder receives no topic name.
2. **Given** message tags in persisted order, **when** they are categorized, **then** topic is followed by all `service:` tags and then all general tags; order within the service and general categories remains the original message order.
3. **Given** a `service:` tag, **when** rendered, **then** its prefix is removed and the remainder uses literal background `#2a3142` and text `#9db4d8`; an empty `service:` remainder is not rendered.
4. **Given** message tags, **when** the tag row is built, **then** the exact `card` marker and every emoji shortcode recognized by the existing emoji lookup are excluded, while non-emoji text tags remain eligible.
5. **Given** general tags, **when** the row is collapsed, **then** at most the first two general tags are shown; when there are three or more, a localized `+N more` text button is appended, and tapping it reveals every remaining general tag without truncating card-body content.
6. **Given** a recycled card, **when** a different notification is bound, **then** expanded/collapsed state and dynamically created chips/listeners cannot leak from the previous notification. Expansion is either reset on bind or explicitly keyed by notification ID.
7. **Given** a general tag name, **when** its palette color is selected, **then** Android matches the web 32-bit unsigned hash exactly: for each UTF-16 code unit, `h = h * 31 + code`, with 32-bit overflow, and `index = unsigned(h) % 6`.
8. **Given** palette index `0..5`, **when** a general chip renders, **then** it uses the corresponding literal background/text pair from `components.md §3`, unchanged in light and dark themes:
   - 0: `#332b52` / `#c4b5fd`
   - 1: `#143a34` / `#7fe0cb`
   - 2: `#3a2f14` / `#f5c97a`
   - 3: `#3a1f22` / `#f5a3a5`
   - 4: `#14303a` / `#7fc8e0`
   - 5: `#283a14` / `#b7e07f`
9. **Given** any notification timestamp in Unix seconds, **when** rendered, **then** it is converted in the device's current local time zone and displayed with the locale-independent Gregorian pattern `yyyy-MM-dd HH:mm:ss`; it is never relative and never uses `DateFormat.getDateTimeInstance`.
10. **Given** zero, few, many, or expanded tags, **when** the meta row lays out, **then** the timestamp remains anchored to the card's right edge, uses caption typography and `@color/muted`, and does not disappear on tag-less cards. Tags may wrap within their left-side area without pushing the timestamp off the right edge.
11. **Given** a chip or the `+N more` control, **when** it is activated, **then** it does not invoke the card's click/mark-read action; chip text remains readable, focusable controls use the shared focus treatment, and touch targets follow existing Material/accessibility conventions.
12. **Given** Story 2.4 is complete, **when** the implementation is reviewed, **then** the legacy `Tags: a, b` `TextView` presentation and locale-short timestamp are no longer used by the redesigned binder, no Compose or new third-party layout/date library is added, and current body, attachment, action, selection, and card interactions remain intact.

## Tasks / Subtasks

- [x] Add pure tag categorization and palette-selection logic (AC: 2–8)
  - [x] Parse the persisted comma-separated tags with the existing `splitTags` contract; do not create a second storage format.
  - [x] Exclude exact `card` and tags for which existing `toEmoji(tag)` returns non-null.
  - [x] Partition `service:` and general tags while preserving order; strip `service:` only for display and skip an empty remainder.
  - [x] Implement the web-compatible 32-bit hash without `String.hashCode().absoluteValue` (which fails for unsigned parity and `Int.MIN_VALUE`).
  - [x] Keep the six general pairs and fixed service pair in named color resources documented as intentional literals, not design tokens.
- [x] Add a dedicated absolute timestamp formatter (AC: 9, 10)
  - [x] Convert `Notification.timestamp` from seconds to an instant/date in the current system time zone.
  - [x] Pin the output pattern to ASCII/Gregorian `yyyy-MM-dd HH:mm:ss` with `Locale.ROOT`.
  - [x] Keep `formatDateShort()` unchanged for legacy callers; the redesigned card must call the new formatter.
- [x] Build the reusable card meta-row view (AC: 1–6, 10–12)
  - [x] Extend the Story 2.1 shell/binder seam with stable meta-row/tag-container/timestamp IDs; do not disturb `@id/card_body`.
  - [x] Use a wrapping Material/View layout already available in the project (for example `ChipGroup`) for dynamic tags; do not add Flexbox or Compose.
  - [x] Anchor the timestamp independently at the end and constrain the wrapping tag region between the card start and timestamp.
  - [x] Render topic, service, and general chips with `radius_full`, caption sizing, category-specific colors, and category order.
  - [x] Replace/remove the legacy `detail_item_tags_text` binding only after all binder references and layout constraints are migrated.
- [x] Implement collapsed/expanded general tags safely (AC: 5, 6, 11)
  - [x] Show first two general tags in collapsed state and calculate `N` from general tags only.
  - [x] Add a localized formatted string such as `notification_card_tags_more` (`+%1$d more`).
  - [x] Stop the expansion control from bubbling into the card click action.
  - [x] Clear dynamic children and listeners on every bind; choose and test reset-on-bind or notification-ID-keyed expansion.
- [x] Integrate through `MessageCardBinder` (AC: 1, 10–12)
  - [x] Consume the nullable topic/display-name argument established by Story 2.1; do not query `DetailActivity`, `DetailAdapter`, or `Repository` for it.
  - [x] Keep per-topic mode passing null so no topic chip appears; future Epic 4 All-feed mode passes the display/topic name.
  - [x] Preserve all non-meta row behavior and keep tag expansion separate from Story 2.5 mark-read behavior.
- [x] Add parity and regression tests (AC: 1–12)
  - [x] Add golden hash vectors including `warning → 4`, `skull → 3`, `deployment → 1`, `backend → 2`, `alpha → 2`, and `서비스 → 0`, asserting both index and exact hex pair.
  - [x] Test category ordering, duplicate/order preservation, exact `card` exclusion, emoji exclusion, service prefix stripping, empty `service:` handling, zero tags, and topic present/absent.
  - [x] Test 0/1/2/3+ general tags, `+N more`, expansion to all tags, and recycled-holder state reset.
  - [x] Test timestamp epoch/known instants in at least UTC and a non-UTC zone, locale changes, and a DST-sensitive zone; restore process defaults after each test.
  - [x] Add a layout/binder test proving the timestamp stays end-aligned for no tags and expanded tags and that expansion does not call the card action.
  - [x] Run focused tests plus Play and F-Droid debug resource processing/assembly.

## Dev Notes

### Current State and Required Change

- The checked-in code still represents the pre-Epic-2 row: `fragment_detail_item.xml` has `detail_item_tags_text` as one full-width `TextView`, while `DetailAdapter.DetailViewHolder.bind()` calls `unmatchedTags(splitTags(notification.tags))` and formats `Tags: ...`.
- The current date path calls `formatDateShort()`, which delegates to locale-dependent `DateFormat.SHORT`. It cannot satisfy the absolute parity format.
- Story 2.1 defines the target seam: a standalone `MessageCardBinder`, nullable topic/display-name input, squared shell, and stable `card_body`. Story 2.4 should modify that binder and the shell's meta region as it exists after Stories 2.1–2.3b, not recreate a second row implementation in `DetailAdapter`.
- Stories 2.2, 2.3a, and 2.3b do not yet have local story artifacts. Before coding, reconcile their merged header/accent changes rather than applying this story mechanically to today's legacy XML.

### Tag Model and Categorization

Use one deterministic transformation from raw persisted tags to a render model. A suitable shape is:

```kotlin
data class CardTags(
    val topic: String?,
    val service: List<String>,
    val general: List<GeneralTag>,
)

data class GeneralTag(
    val name: String,
    val paletteIndex: Int,
)
```

Rules:

- `topicName` is binder input, not part of `Notification.tags`.
- Preserve tag order inside each category. The final order is topic, all service tags, all general tags.
- Exclude only the exact marker `card`; do not accidentally remove names containing that substring.
- Emoji exclusion must reuse `toEmoji()`/the installed emoji alias catalog so behavior stays aligned with existing title/message emoji rendering.
- Keep duplicate tags unless the canonical web implementation explicitly deduplicates them; the supplied contract requires order preservation, not deduplication.
- Prefix matching is the exact lowercase `service:` protocol unless the authoritative web source proves otherwise. Do not silently make it case-insensitive.

### Hash Parity Guardrail

Kotlin `String` iteration yields UTF-16 `Char` code units, matching JavaScript `charCodeAt` semantics. Implement overflow deliberately:

```kotlin
var hash = 0u
for (char in name) {
    hash = hash * 31u + char.code.toUInt()
}
val index = (hash % 6u).toInt()
```

An equivalent `Int` overflow implementation followed by `toUInt()` is acceptable. Do not use Kotlin/Java `String.hashCode()` plus `abs()` as the specification: although the recurrence is related for BMP text, signed modulo/absolute-value handling diverges from the required unsigned operation.

### Meta-Row Layout

- The tag region must wrap independently while the timestamp remains constrained to the end edge. A practical View/XML structure is a `ConstraintLayout` meta row with a left `ChipGroup` width `0dp` constrained start-to-start and end-to-start of the timestamp, and the timestamp constrained end-to-end.
- Use `@dimen/spacing_2` gaps/padding, `@dimen/text_caption`, `@dimen/leading_caption`, and `@dimen/radius_full` from Stories 1.1/1.2. Do not re-declare canonical tokens.
- Topic chip uses token colors. Service and general colors are the explicitly permitted literal palette and should be named resources with comments tying them to `components.md §3`.
- Timestamp remains visible for tag-less notifications. Avoid a chain whose visibility changes move it away from the end edge.
- Expanded tags may increase meta-row height. This is intentional; only card-body compact/show-more behavior is prohibited.

### Expansion and RecyclerView State

- The simplest compliant behavior is reset-to-collapsed on every bind. If product continuity requires expansion to survive rebinding, store IDs in host/UI state keyed by `Notification.id`; never store an unkeyed `expanded` Boolean in a recycled holder.
- Remove all dynamically created chip/button children before rendering the next model.
- The `+N more` count is `general.size - 2`; service and topic chips do not count toward the two-tag cap.
- Expansion reveals all remaining general tags and removes/hides the expansion button. A collapse affordance is not required by the contract.
- Set an explicit click listener on the expansion control and ensure it does not invoke the card click callback. Story 2.5 later formalizes tap-to-mark-read, so this separation is a forward compatibility requirement.

### Timestamp Guardrails

- `Notification.timestamp` is Unix seconds. Do not treat it as milliseconds.
- Use the current device time zone at format time. The output must represent local wall time but use fixed field order and ASCII digits independent of the user's language.
- Prefer `java.time` (available at min SDK 26), e.g. `Instant.ofEpochSecond(value).atZone(ZoneId.systemDefault())` with `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)`.
- Use calendar year `yyyy`, not week-based year `YYYY` in Java/Kotlin formatter syntax. The product prose writes `YYYY`, but the required sample and intent are calendar date; uppercase `Y` can produce the wrong year near New Year.
- Do not globally replace `formatDateShort()`: other screens may intentionally retain localized formatting.

### Architecture Compliance and Scope

- Continue with Kotlin, Views/XML, AppCompat, Material, ConstraintLayout, and RecyclerView. No Compose, Flexbox dependency, date library, or dependency upgrade belongs here.
- Preserve min SDK 26, target/compile SDK 36, Java/Kotlin 17, and both `play`/`fdroid` flavors.
- Reuse `splitTags()` and `toEmoji()`; extend with focused pure helpers rather than embedding categorization/hash code in a large bind method.
- Keep `MessageCardBinder` adapter/activity agnostic. Topic comes through its existing nullable input.
- Preserve body rendering, attachments, action buttons, links, selection, delete controls, unread state, and priority UI.
- The Epic 2 card remains feature-gated until Stories 2.1–2.4 and 2.3b ship as one complete release bundle.

### Expected Files

Update the post-Story-2.1/2.3 versions of:

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Add/finalize the meta row, wrapping tag host, and end-anchored timestamp.
  - Preserve the shell, accent, header, `card_body`, attachment/action behavior, and stable IDs used by the binder.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Bind topic/service/general chips, expansion, resets, and the absolute timestamp.
  - Preserve adapter-agnostic boundaries.
- `app/src/main/java/io/heckel/ntfy/util/Util.kt` or a focused new utility such as `ui/message/CardTagFormatter.kt`
  - Add pure categorization/hash and absolute timestamp formatting without changing legacy callers.
- `app/src/main/res/values/colors.xml`
  - Add named literal service/general tag colors only if Story 1.2 has not already created them.
- `app/src/main/res/values/strings.xml`
  - Add the localizable `+N more` format string.
- Focused tests under the project's chosen JVM/Robolectric or instrumentation source set.

Do not add a parallel card layout or a second adapter-specific implementation. If prerequisite stories chose different focused package/resource names, extend those established locations.

### Testing Requirements

- Hash tests are contract tests, not visual snapshots. Assert the unsigned index and exact background/text values.
- Include at least one non-ASCII BMP vector to catch accidental byte-based hashing. Emoji tags are excluded before general hashing, but the hash helper should still be deterministic for arbitrary UTF-16 strings.
- Control `Locale` and `TimeZone`/`ZoneId` explicitly in date tests and restore global defaults in `finally`/test teardown to avoid order-dependent failures.
- Test DST conversion with a fixed instant, not by assuming the developer machine's current zone.
- Exercise recycled binding from expanded/many-tags to no-tags and from topic-present to topic-null.
- Verify the timestamp remains right-aligned when the tag host is `GONE`, empty, wrapped, and expanded.
- Verify interactive chips/expander do not trigger the card action; preserve whole-card behavior elsewhere.

### Previous Story Intelligence

- Story 2.1 established `MessageCardBinder` as the only reusable card binding seam and `fragment_detail_item.xml` as the shared shell. Story 2.4 must extend those artifacts, not return logic to `DetailAdapter`.
- Story 2.1 explicitly warns that conditional views and dynamic children are the highest recycling risk. The dynamic tag host must be fully reset on every bind.
- Topic input is intentionally nullable: current per-topic lists pass null; Epic 4 All-feed mode supplies a display/topic name.
- The body slot belongs to Epic 3. Meta-row work must not move structured/body content out of `card_body` or force Epic 3 to reopen the shell.
- Existing repository changes are planning artifacts and user-owned work; do not overwrite unrelated story/status edits.

### Git Intelligence

- Recent commits add the UI-parity SPEC, companion catalogs, epics, and sprint tracking; they contain no implemented tag-row pattern to copy.
- Historical `DetailAdapter` work is incremental and behavior-preserving around actions, links, icons, and attachments. This story should make a focused meta-row replacement rather than rewrite the card.
- No dependency change is indicated by history or the current stack.

### Latest Technical Information

- External web research is unnecessary for this story. The authoritative current contract is the checked-in 2026-06 web-parity documentation and the pinned project stack.
- Relevant pinned components are AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0, Material 1.13.0, min SDK 26, and Kotlin/JVM 17.
- `java.time` is available natively at the project's min SDK and is preferred over adding a formatter dependency.

### Project Structure Notes

- Production UI code remains under `app/src/main/java/io/heckel/ntfy/ui/` or the focused subpackage introduced by Story 2.1.
- Shared card layout remains `app/src/main/res/layout/fragment_detail_item.xml`.
- Canonical tokens remain in Android resources; only the documented tag palette/service colors may be literal.
- There is no project-level `project-context.md`; the SPEC kernel, brownfield notes, epics, and `docs/ui-parity` companions are the project context.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — FR3, NFR2, NFR7, Epic 2, Story 2.4, merge gating]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-4, success signal, assumptions]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack, current row, carry-over constraints]
- [Source: `docs/ui-parity/components.md` — §1 Meta row, §3 CardTags, §5 Chips]
- [Source: `docs/ui-parity/design-tokens.md` — Typography, spacing, radius, literal tag colors]
- [Source: `docs/ui-parity/CHANGELOG-redesign-2026-06.md` — Categorized tags and timestamp delta]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — current tag/date binding and recycling behavior]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Util.kt` — `formatDateShort`, `splitTags`, `toEmoji`, `unmatchedTags`]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml` — current tag/date views]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` — `Notification.timestamp` and `tags`]
- [Source: `app/build.gradle` — platform and library versions]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; base/team/user customization was resolved manually.
- No team or user override file was present; base workflow persistent facts found no `project-context.md`.
- `EmojiManager` uses `org.json` in its static initializer which is stubbed in JVM unit tests; solved by injecting an `isEmoji: (String) -> Boolean` lambda into `CardTagFormatter.categorize()` so tests can supply a local known-alias set without invoking the real EmojiManager.
- `CardEffectControllerDecisionTest.kt` (from another session) had two backtick method names containing `:` which is illegal in JVM method descriptors; fixed by removing the colons from the names.

### Completion Notes List

- Implemented `CardTagFormatter` as a pure Kotlin object with `categorize()`, `webHash()`, and `formatAbsoluteTimestamp()`.
- `webHash()` uses unsigned 32-bit arithmetic matching the JavaScript charCodeAt-based hash exactly; golden vectors all pass (warning→4, skull→3, deployment→1, backend→2, alpha→2, 서비스→0).
- `formatAbsoluteTimestamp()` uses `java.time.Instant.ofEpochSecond` with `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)` — calendar year `yyyy` prevents ISO-week-year drift near New Year.
- Layout updated: `detail_item_tags_text` removed, replaced with `card_meta_row` ConstraintLayout containing `card_tag_chip_group` (ChipGroup, 0dp, end-to-start of timestamp) and `card_meta_timestamp` (TextView, caption/muted, end-anchored).
- `MessageCardBinder.renderMetaRow()` resets ChipGroup on every bind, renders topic/service/general chips with correct palette colors, and shows `+N more` button that expands inline without bubbling to the card click handler.
- TypedArray resolved eagerly in `buildMoreButton()` before the lambda captures it; no recycle-after-use bug.
- 24 JVM unit tests written in `CardTagFormatterTest`; all pass. Full fdroid debug unit test suite passes. `assembleFdroidDebug` succeeds.

### File List

- `_bmad-output/implementation-artifacts/2-4-categorized-tag-row-timestamp.md`
- `app/src/main/java/io/heckel/ntfy/ui/CardTagFormatter.kt`
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
- `app/src/main/res/layout/fragment_detail_item.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/heckel/ntfy/ui/CardTagFormatterTest.kt`
- `app/src/test/java/io/heckel/ntfy/ui/CardEffectControllerDecisionTest.kt`

### Change Log

- 2026-06-21: Story 2.4 implemented — categorized tag row (topic/service/general chips), unsigned hash parity, +N more expansion, absolute timestamp, meta-row layout, full JVM test suite.
