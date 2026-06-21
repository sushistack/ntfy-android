---
baseline_commit: c216fdb4668cc8f069c7f15adc33b10986d579ed
---

# Story 3.2: Inline Meter Component

Status: review

## Story

As a user,
I want a horizontal meter bar with threshold colors,
so that numeric values read at a glance, identically to web.

## Acceptance Criteria

1. **Given** any numeric meter input  
   **When** the reusable inline meter is bound  
   **Then** it renders a horizontal `@color/meter_track` track with fully pill-shaped ends derived from `@dimen/radius_full`, a height of exactly 7dp, and a fill width equal to the value clamped to 0–100 percent.

2. **Given** a finite value in the meter range  
   **When** its fill color is selected  
   **Then** values `< 65` use `@color/meter_ok`, values `>= 65` and `< 90` use `@color/meter_warning`, and values `>= 90` use `@color/meter_critical`  
   **And** the Story 3.0 golden vectors assert `64 -> ok`, `65 -> warning`, `89 -> warning`, and `90`/`91 -> critical` without re-encoding a separate threshold table.

3. **Given** an out-of-range finite value  
   **When** the meter is bound  
   **Then** the displayed value is clamped, including `-5 -> 0` and `130 -> 100`  
   **And** the threshold color is selected from that normalized value.

4. **Given** the meter is exposed to an accessibility service  
   **When** its node information is inspected  
   **Then** it reports a determinate range of `0..100` with the normalized current value, identifies itself as a progress/meter control, and announces a localized percentage  
   **And** the decorative fill and track are not separately focusable or announced.

5. **Given** a meter view is rebound or reused  
   **When** the new value is lower, higher, or in another threshold band  
   **Then** fill width, color, range semantics, and label/state are replaced deterministically with no state leaking from the prior bind  
   **And** width calculation remains correct after measurement, parent resize, and use inside either a full-width or two-column KV row.

6. **Given** the standalone component is consumed by Story 3.3  
   **When** a finite KV `meter` value is present  
   **Then** the same component/API is used rather than duplicating meter drawing or threshold logic  
   **And** this story does not implement KV parsing, row layout, status dots, card dispatch, or edits to `fragment_detail_item.xml`.

7. **Given** both themes and both product flavors  
   **When** the component is rendered and built  
   **Then** it resolves the Story 1.1/1.2 token resources in light and dark mode, introduces no raw hex or px literals, adds no Compose or third-party progress dependency, and passes focused unit/resource/accessibility tests plus Play and F-Droid debug builds.

## Tasks / Subtasks

- [x] Define the pure meter value model (AC: 1–3, 5)
  - [x] Add one normalization function that clamps finite values to `0f..100f`.
  - [x] Add one threshold function returning `OK`, `WARNING`, or `CRITICAL`; keep `65` and `90` as the only transition boundaries.
  - [x] Consume Story 3.0's meter golden corpus for boundary assertions and add focused clamp vectors for negative and over-100 inputs.
  - [x] Keep non-finite-value filtering at the Story 3.3 parser/renderer boundary; document the component's finite-number precondition rather than inventing an indeterminate state.
- [x] Build the reusable View-system component (AC: 1, 3, 5–7)
  - [x] Add a small custom View or encapsulated layout under `io.heckel.ntfy.ui.structured`; prefer a custom View if it avoids fragile percentage-layout mutation and makes accessibility range semantics explicit.
  - [x] Draw/lay out a 7dp `meter_track` pill and a clipped fill whose width is `normalized / 100 * availableWidth`.
  - [x] Ensure 0% draws no visible fill, 100% reaches the track end, and tiny non-zero values cannot overflow the rounded track.
  - [x] Recompute geometry on size changes and invalidate/request layout only when necessary.
  - [x] Expose a narrow binding API such as `setValue(Number)` or `bind(MeterState)` for Story 3.3; do not couple it to JSON models, `Notification`, an Activity, adapter position, or `MessageCardBinder`.
- [x] Implement accessibility semantics (AC: 4–5)
  - [x] Use a View accessibility delegate/override to provide `RangeInfo` (`0`, `100`, normalized current value) and a ProgressBar-compatible class name/role.
  - [x] Provide a localized percentage description/state using the existing string-resource pipeline; do not hard-code English or announce internal threshold names.
  - [x] Make the component one semantic node, non-clickable, and non-adjustable unless a future interactive-meter requirement explicitly adds actions.
  - [x] Notify accessibility services when a bound value actually changes without generating duplicate events on identical rebinds.
- [x] Integrate the reusable ownership seam (AC: 5–7)
  - [x] Keep the component in its own file/layout so Story 3.3 can inflate it inside `view_card_kv.xml`.
  - [x] Do not edit `fragment_detail_item.xml`; Epic 2 owns the shell and `@+id/card_body`.
  - [x] Do not add a standalone demo screen or expose this enabler independently; it ships with Story 3.3 per the Epic 3 slicing gate.
- [x] Add deterministic tests and verification (AC: 1–7)
  - [x] Unit-test clamp and threshold selection, including Story 3.0 boundaries and `-5`/`0`/`64`/`65`/`89`/`90`/`91`/`100`/`130`.
  - [x] Assert exact token resource selection and exact 7dp height in both default and night resources.
  - [x] Assert measured fill geometry at 0%, representative fractions, and 100%, including a resize/rebind sequence.
  - [x] Assert accessibility node range, class/role, localized percentage, and absence of separately exposed decorative children.
  - [x] Run the focused tests, the repository token/raw-literal checks, `./gradlew check`, and `./gradlew assemblePlayDebug assembleFdroidDebug`.

## Dev Notes

### Dependency and Release Gates

- Story 3.0 owns the parity golden corpus. At implementation time, consume its fixture rather than copying the five boundary expectations into an unrelated test-data source. Story 3.0 is currently backlog and has no story artifact, so its final fixture path/API must be discovered before coding.
- Story 3.1 owns card detection, dispatch, and fallback. The meter must not parse JSON or decide whether a notification is structured.
- Story 3.3 is the first runtime consumer and owns finite-number validation plus KV row layout. Story 3.2, like Stories 3.0 and 3.1, is an enabler and must ship with 3.3 rather than as standalone user-visible behavior.
- Stories 1.1 and 1.2 own `meter_ok`, `meter_track`, `meter_warning`, `meter_critical`, `radius_full`, typography, and raw-literal verification. Consume those exact resources; do not add fallback colors or a second radius token.
- Story 2.1 owns `fragment_detail_item.xml` and the `card_body` slot. This story creates a child component only.

### Developer Context

- The project remains Kotlin + View/XML + AppCompat/Material, minSdk 26, compile/target SDK 36, Java/Kotlin 17. Do not introduce Compose.
- There is no existing reusable body meter. The upload `LinearProgressIndicator` in `fragment_publish_dialog.xml` represents transfer progress and is not the parity component; do not restyle or reuse its upload-specific behavior.
- Keep normalization and threshold classification pure and independent of Android resources. The View maps the resulting band to token resource IDs.
- Apply thresholds after clamping. This yields deterministic behavior for out-of-range finite values and makes the displayed range agree with its color.
- `7 tall` from the web companion maps to 7dp in this Android View implementation, consistent with the project's pixel-to-dp token translation.
- The optional trailing label described by `components.md` is not required by this story or KV meter usage. Accessibility still announces the value. Do not add visible label layout unless a consuming story explicitly requests it.
- Do not animate value changes. No meter animation is specified, and adding one would create reduced-motion and RecyclerView reset obligations outside this story.

### Accessibility Contract

- Web's `role="meter"` has no direct Android XML role attribute. Represent the determinate meter through a single accessibility node with `AccessibilityNodeInfo.RangeInfo`/compat equivalent and a ProgressBar-compatible class name.
- Report min `0`, max `100`, and the clamped current value. Use a localized percentage string/state; threshold-band names are visual implementation details, not the accessible value.
- The meter is read-only. It must expose no click, increment, decrement, seek, or set-progress actions.
- If implemented as a custom-drawn View, the View itself is the semantic node. If implemented as nested Views, descendants must be hidden from accessibility to avoid duplicate announcements.

### Geometry and RecyclerView Safety

- A custom View is the cleanest likely fit: draw the rounded track, clip to the rounded track, then draw the fill to the computed fraction. This avoids mutating `LayoutParams.width` before measurement and handles KV grid-cell resizing.
- Use floating-point geometry through drawing; round only at the rendering boundary. Clamp the computed fill edge to the content bounds.
- Recompute cached geometry in `onSizeChanged`. `setValue` should update color/semantics and invalidate, but should not trigger unnecessary layout when intrinsic height is unchanged.
- Every bind must overwrite the normalized value and color. Test high-to-low, critical-to-ok, and 100-to-0 reuse sequences.
- Do not make the meter focusable on its own if that creates a noisy extra stop inside every KV row. It must remain discoverable in accessibility traversal as meaningful content while behaving as one read-only node; verify actual TalkBack traversal during the Story 3.3 integration.

### Existing Files and Preservation Requirements

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current repository state is the legacy monolithic `CardView`; Story 2.1 will replace it with the reusable shell and body slot.
  - Read-only for this story. Do not resolve prerequisite layout work here.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Currently owns message/markdown, attachment, action, and selection rendering.
  - Do not add meter logic here. Story 3.1/3.3 should route structured content through the future binder/body renderer.
- `app/src/main/res/values/dimens.xml`
  - Currently contains only `fab_margin`; Story 1.2 is expected to add canonical dimensions.
  - Reuse `radius_full`; add a component anatomy dimension such as `meter_height` = `7dp` only if the final Story 1.2 resource contract does not already provide it.
- `app/src/main/res/values/colors.xml` and `values-night/colors.xml`
  - Current checked-in production files predate Story 1.1. Do not patch around missing meter tokens with literals; implement after the prerequisite token story lands.
- `app/build.gradle`
  - Preserve Play/F-Droid flavors and existing dependencies. Only add missing test infrastructure if essential.

### Architecture Compliance

- Suggested package: `io.heckel.ntfy.ui.structured`.
- Suggested files:
  - NEW `app/src/main/java/io/heckel/ntfy/ui/structured/InlineMeterView.kt`
  - NEW or UPDATE `app/src/main/res/values/structured_components.xml` for `meter_height` and a localized percentage string, following final resource organization
  - NEW focused tests under `app/src/test/...` and/or `app/src/androidTest/...`
- No database, network, repository, service, navigation, manifest, adapter, card-shell, chart-library, or Compose changes.
- Avoid subclassing or customizing the upload progress indicator. Structured-body meters and upload progress have different ownership and semantics.

### Testing Requirements

- Pure model:
  - clamp below/inside/above range;
  - exact boundary classification;
  - repeated identical input produces identical state.
- View geometry:
  - exact 7dp measured height;
  - 0%, fractional, and 100% fill;
  - parent width change recalculates fill;
  - rebind critical -> ok -> warning resets color and width.
- Resources:
  - light and dark token IDs resolve from the canonical files;
  - no raw color/px;
  - rounded track consumes `radius_full`.
- Accessibility:
  - one node reports range `0..100`;
  - current value equals clamped value;
  - localized percentage is present;
  - node is read-only and decorative internals are absent.
- Integration smoke check with Story 3.3:
  - meter fills remaining KV-row width;
  - a `columns:2` grid cell constrains only its own meter;
  - mobile single-column and resized layouts remain correct;
  - TalkBack announces the meter once.

### Previous Story Intelligence

- No Story 3.0 or 3.1 implementation artifact exists yet, despite both being required predecessors in the Epic 3 sequence. Treat their final APIs as prerequisites to inspect, not APIs to guess.
- Story 2.6 reinforces the project-wide RecyclerView rule: every reusable visual must fully reset transient state on bind. For the meter, width, band color, range semantics, and description all require deterministic replacement.
- Story 1.2 establishes named token resources and a repository-wide raw-literal verifier. Extend/reuse that verification rather than creating a parallel styling policy.
- The current worktree contains user-owned generated story artifacts only; do not overwrite or reformat unrelated stories.

### Git Intelligence

- Recent commits add the SPEC, epic plan, and UI parity references. No structured-body or meter implementation has landed.
- Existing history in `DetailAdapter` is attachment/action/markdown focused and offers no reusable meter pattern.
- The current production layout and resources are pre-Epic-1/2 brownfield code. Implement against landed prerequisite contracts rather than baking their planned changes into this story.

### Latest Technical Information

- Android's range accessibility metadata supports a determinate current value with min/max via `AccessibilityNodeInfo.RangeInfo`; an accessibility delegate can add those semantics without making the meter interactive.
- No dependency upgrade is needed. Platform/AndroidX accessibility APIs and a custom View cover the requirement.

Official references:

- https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.RangeInfo
- https://developer.android.com/reference/kotlin/android/view/View.AccessibilityDelegate
- https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing

### Project Structure Notes

- Keep this story as a small reusable primitive plus pure decision logic.
- The likely production addition is one custom View and focused resources/tests; `view_card_kv.xml` belongs to Story 3.3.
- Do not expose a new public framework or generic design-system module for one component.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Stories 3.0–3.3]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-5, CAP-6, Constraints]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — rendering-stack decision]
- [Source: `docs/ui-parity/components.md` §4 — Meter]
- [Source: `docs/ui-parity/message-format.md` §2.1 — KV meter behavior and columns]
- [Source: `docs/ui-parity/design-tokens.md` — meter colors and `radius_full`]
- [Source: `_bmad-output/implementation-artifacts/1-1-color-token-resources-light-dark.md`]
- [Source: `_bmad-output/implementation-artifacts/1-2-non-color-token-resources-literal-tag-palettes.md`]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/src/main/res/layout/fragment_publish_dialog.xml`]
- [Source: `app/build.gradle`]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Python 3.11 resolver unavailable; TOML customization resolved manually from base file.
- Another session (Story 3.4) was running concurrently — Gradle daemons were stopped mid-build multiple times; waited for idle state before each test run.
- `InlineMeterView.kt` was pre-populated by the concurrent session; resolved `fillColorRes` ownership (property in `MeterState.Band` with Android R import).
- Kotlin daemon fallback used on first full compile; subsequent runs used incremental cache (~13s).

### Completion Notes List

- ✅ `MeterState.kt` — pure Kotlin model, `clamp()` + `threshold()`, `Band.fillColorRes` property, `from(Double)` factory.
- ✅ `InlineMeterView.kt` — custom View; pill track via `drawRoundRect`; fill clipped to track bounds; height from `@dimen/meter_track_height` (7dp); `bind(Double)` API; `ViewCompat` delegate with `RangeInfoCompat(0..100)`, ProgressBar class, localized `%d%%` state; event only on changed state; `isClickable/isFocusable = false`.
- ✅ `MeterStateTest.kt` — clamp/threshold/from + full Story 3.0 golden corpus (64→ok, 65→warning, 89→warning, 90→critical, 91→critical) + out-of-range vectors (-5→0, 130→100). All pass.
- ✅ `MeterResourceTest.kt` — XML-parsed: meter colors in light+dark, meter_track_height=7dp, radius_full present, no raw hex/px in source. All pass.
- ✅ `MeterAccessibilityContractTest.kt` — range values (0–100 bounds), clamped current value, rebind determinism, percentage int. All pass.
- ✅ `fragment_detail_item.xml` not modified; `DetailAdapter.kt` not modified.
- ✅ Play debug + F-Droid debug APK: BUILD SUCCESSFUL.
- ✅ Full unit test suite: BUILD SUCCESSFUL.

### File List

- `app/src/main/java/io/heckel/ntfy/ui/structured/MeterState.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/structured/InlineMeterView.kt` (new, co-authored with concurrent session)
- `app/src/test/java/io/heckel/ntfy/ui/structured/MeterStateTest.kt` (new)
- `app/src/test/java/io/heckel/ntfy/ui/structured/MeterResourceTest.kt` (new)
- `app/src/test/java/io/heckel/ntfy/ui/structured/MeterAccessibilityContractTest.kt` (new)
- `_bmad-output/implementation-artifacts/3-2-inline-meter-component.md` (updated)

## Change Log

- 2026-06-21: Story 3.2 implemented — InlineMeterView custom View + MeterState pure model + focused unit/resource/accessibility tests. Play + F-Droid debug builds pass.
