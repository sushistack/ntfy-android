# Story 3.5: `chart` Block Renderer (Hand-Drawn Canvas)

---
baseline_commit: c216fdb4668cc8f069c7f15adc33b10986d579ed
---

Status: in-progress

## Story

As a user,
I want compact bar and line charts without a chart library,
so that time-series payloads render in parity with ntfy-web while Android stays dependency-free.

## Acceptance Criteria

1. **Given** a parsed structured block with `type: "chart"` and at least one valid point  
   **When** the card-body renderer dispatches the block  
   **Then** it renders a reusable View-system chart component at full available width with a 120dp plot height  
   **And** it uses no Jetpack Compose and adds no external chart dependency  
   **And** it remains inside the existing `card_body` slot without changing the Epic 2 card shell, header, meta row, attachments, actions, or tap/delete behavior.

2. **Given** `kind: "bar"`, an absent `kind`, or any value other than the literal `"line"`  
   **When** the chart draws  
   **Then** it renders vertical bars using the accent fill token  
   **And** bars share the available plot width without overlap, remain visible for a single-point data set, and grow away from the computed zero baseline toward their value  
   **And** mixed positive/negative values render on opposite sides of that baseline.

3. **Given** `kind: "line"`  
   **When** the chart draws  
   **Then** it renders one anti-aliased emerald polyline with a 2dp stroke using the accent token  
   **And** points preserve input order  
   **And** a one-point line data set still renders a visible point rather than an empty-looking chart.

4. **Given** chart data from JSON  
   **When** points are normalized  
   **Then** each `value` is coerced to a finite number using the shared Story 3.0/3.1 parsing contract  
   **And** non-numeric, `NaN`, positive-infinity, and negative-infinity results are dropped without crashing  
   **And** the renderer keeps the first 60 valid points in original order, rather than capping before invalid points are removed  
   **And** the source spec and caller-owned collections are not mutated.

5. **Given** the normalized points  
   **When** the y-domain is calculated  
   **Then** it is `[min(0, minimumValue), max(0, maximumValue)]`, so zero is always included  
   **And** all-negative, all-positive, and mixed-sign data map correctly into the plot bounds  
   **And** a zero-span domain, including all-zero or identical positive values, is expanded deterministically so no division by zero, `NaN`, or invisible geometry occurs.

6. **Given** 1–12 valid points  
   **When** labels render below the plot  
   **Then** each point has one muted caption label equal to its non-empty `label`, otherwise the normalized display value followed directly by `unit` when present  
   **And** labels preserve point order, divide the available width evenly, and do not alter the fixed 120dp plot height  
   **And** long label text is constrained to its cell without overlapping adjacent labels.

7. **Given** 13–60 valid points  
   **When** the chart renders  
   **Then** the label row is omitted entirely, including its height and spacing  
   **And** the 13-point boundary is explicitly tested.

8. **Given** missing, empty, or all-invalid `data`  
   **When** the chart block is dispatched  
   **Then** it contributes no visible chart or label view and no reserved vertical gap  
   **And** it does not crash the card or replace valid sibling content in a future `sections` renderer.

9. **Given** a chart View previously bound with different data, kind, labels, unit, width, or theme  
   **When** it is rebound or remeasured in a recycled card  
   **Then** all derived geometry and label children/state are replaced, the View is invalidated, and no stale bars, path segments, labels, colors, or accessibility text leak from the previous message  
   **And** drawing objects are reused rather than allocated repeatedly inside `onDraw()`.

10. **Given** light or dark theme and a chart block  
    **When** it renders  
    **Then** chart geometry uses the existing accent token selected by what is painted (`accent_ui` for shapes; use an already-established equivalent only if Stories 1.x/3.x define one) and labels use `muted`  
    **And** all dimensions and colors come from named resources (`120dp` plot height, `2dp` line stroke, token typography/spacing), with no raw hex and no new token family  
    **And** a theme/configuration change resolves current resource colors rather than retaining a stale color integer.

11. **Given** a chart with valid points  
    **When** accessibility services inspect the card  
    **Then** the chart exposes a concise, localizable summary containing chart kind, point count, and value range  
    **And** decorative Canvas geometry is not exposed as dozens of false focus targets  
    **And** visible labels remain represented by the chart summary or accessible label views without duplicate announcements.

## Tasks / Subtasks

- [ ] Integrate with the established structured-card model and dispatch contract (AC: 1, 4, 8–9)
  - [ ] Read the implemented Stories 3.0–3.4 artifacts and code before editing; extend their actual `CardSpec`/block/renderer APIs rather than creating a parallel parser or dispatcher.
  - [ ] Add/confirm a typed chart model containing `kind`, optional `unit`, and ordered point data; keep JSON normalization outside `onDraw()`.
  - [ ] Normalize by dropping invalid points first and then taking the first 60 valid points.
  - [ ] Make the renderer return/mount no View for zero valid points and preserve sibling rendering semantics needed by Story 3.7.

- [ ] Implement a reusable View-system chart component (AC: 1–5, 9–10)
  - [ ] Add a focused custom View (for example `StructuredChartView`) under the existing structured-card UI package.
  - [ ] Pre-create and reuse `Paint`, `Path`, and geometry buffers; never allocate per bar/segment in `onDraw()`.
  - [ ] Compute density-aware plot bounds from measured width and padding; do not assume a screen width.
  - [ ] Implement deterministic y-domain/zero-baseline mapping for positive, negative, mixed, and zero-span data.
  - [ ] Implement bar spacing/minimum visible width and line rendering, including a visible one-point line marker.
  - [ ] Clear cached geometry on new model, size, layout-direction, and resource/theme changes; call `requestLayout()` only when label-row/size needs change and `invalidate()` for drawing changes.

- [ ] Implement label presentation (AC: 6–7, 9–11)
  - [ ] Show a label row only for 1–12 valid points; omit it entirely for 13+.
  - [ ] Prefer a lightweight sibling/container label row to Canvas text if that better preserves measurement, ellipsis, RTL, font scale, and accessibility behavior.
  - [ ] Use `text_caption`, `leading_caption`, `muted`, and named spacing resources.
  - [ ] Format fallback labels deterministically from the normalized numeric value plus optional unit; do not use locale-dependent grouping that diverges from the payload text.

- [ ] Wire the chart renderer into the body slot (AC: 1, 8–10)
  - [ ] Add chart dispatch to the shared structured block renderer created by Stories 3.1–3.4.
  - [ ] Keep `MessageCardBinder` adapter-agnostic and keep parsing/drawing independent of `DetailActivity`, repository, navigation, and RecyclerView position.
  - [ ] Preserve existing raw/markdown fallback, attachment, icon, action, link, card click, long-click, and selection behavior.
  - [ ] Do not implement `sections` orchestration in this story; expose the reusable chart-block seam Story 3.7 will call.

- [ ] Add focused automated coverage (AC: 1–11)
  - [ ] Unit-test normalization/coercion, invalid-point removal, cap-after-filter behavior, source immutability, default bar kind, and label text selection.
  - [ ] Unit-test domain and coordinate mapping for positive-only, negative-only, mixed-sign, all-zero, equal positive, equal negative, and extreme finite values.
  - [ ] Test boundaries at 0, 1, 12, 13, 60, and 61 valid points, plus invalid values before/within the first 60 inputs.
  - [ ] View/Robolectric or instrumentation-test 120dp plot height, 2dp line stroke, full-width measurement, no-space empty rendering, light/dark token resolution, font scale, and rebind cleanup.
  - [ ] Assert one-point line visibility, bar/line switching on one recycled View, no external chart dependency, and no Compose addition.
  - [ ] Add screenshot/manual parity checks against `mobile-10-structured-cards.png` and the canonical web behavior for bar, line, ≤12-label, and 13-label cases.

## Dev Notes

### Dependency and Scope Gates

- This story depends on Story 3.0's golden corpus, Story 3.1's typed parsing/dispatch/fallback seam, and the renderer conventions established by Stories 3.2–3.4. Those story files and production APIs do not exist in the current working tree. Do not guess their final names or duplicate them; implement this story after those prerequisites and adapt to the APIs that actually landed.
- Consume the Epic 2 adapter-agnostic `MessageCardBinder` and named `card_body` slot. The current repository snapshot still shows the pre-Epic-2 `DetailAdapter`/`fragment_detail_item.xml`; this story must not rebuild the shell or couple chart code to that legacy holder.
- Scope is the chart block only: typed chart normalization, drawing, labels, accessibility, and shared block-dispatch integration.
- Out of scope: parser dual-gate changes (3.1), meter/kv/list implementation (3.2–3.4), markdown/security (3.6a/3.6b), sections ordering (3.7), heuristic kv (3.8), feed/navigation changes (Epic 4), chart animation/interaction, legends, axes/grid lines, tooltips, zoom, and horizontal scrolling.

### Developer Context

- Stay in View/XML + AppCompat. The architecture decision explicitly forbids introducing Compose for structured rendering.
- Use a custom `View` overriding `onDraw(Canvas)` for plot geometry. Android's official guidance recommends creating expensive drawing objects ahead of `onDraw()` and calculating size-dependent geometry from actual View dimensions rather than assuming screen size.
- Prefer a composite `StructuredChartBlockView` only if needed: a custom plot View plus a lightweight label row. This keeps Canvas responsible for bars/lines while standard TextViews handle font scaling, bidi, ellipsis, and accessibility.
- Keep normalization/domain mapping as pure Kotlin functions. Canvas should consume an immutable render model containing already-normalized points and derived semantics.
- Gson 2.13.2 is already present. Reuse the parser/number-coercion policy established in Story 3.1; do not add kotlinx.serialization or another JSON dependency.
- The exact web contract says values are coerced to numbers. The Story 3.0 corpus must pin ambiguous coercions (numeric strings, null, booleans, empty strings). Until then, do not invent Android-only coercion behavior.

### Chart Geometry Guardrails

- Valid-point pipeline: parse/coerce → reject non-finite → preserve order → `take(60)`.
- Domain:
  - `domainMin = min(0.0, points.minOf { it.value })`
  - `domainMax = max(0.0, points.maxOf { it.value })`
  - if `domainMax == domainMin`, expand to a stable non-zero range containing zero before mapping.
- Map values into the content rectangle after padding. Clamp computed coordinates to finite plot bounds as a final safety guard.
- The zero baseline is the mapped coordinate for `0`. Bars span between the baseline and value coordinate; never assume bars start at the bottom.
- Bar width/gap must be derived from measured plot width and point count. Keep a non-zero visible bar for a one-point chart and avoid negative width at 60 points on narrow phones.
- A line requires at least two values for a segment; for one valid value, draw a small filled point using the same accent paint so valid data is not invisible.
- The 120dp requirement is the plot area. If labels are present, their caption row is additional measured height below it; if labels are omitted, do not reserve label height.
- Respect View padding and layout direction. Data order remains payload order; do not reverse the series in RTL.

### Label and Number Rules

- Labels are based on the **valid, capped** point list. Thus 12 valid points show labels and 13 valid points show none, even if the raw input count differs.
- Label precedence: non-empty point `label`; otherwise normalized display value plus `unit` with no inserted separator (`34` + `%` → `34%`).
- Preserve a sensible, deterministic numeric representation without binary floating-point noise. Centralize this formatter and golden-test integers, decimals, negatives, and large/small finite values.
- Do not draw labels into a single unconstrained Canvas line where neighboring text can overlap. Each label gets an equal-width cell with centered or parity-approved alignment and end ellipsis.
- Do not truncate chart data or add “show more”; the 60-point protocol cap is data normalization, not a compact-card affordance.

### Existing and Expected Files

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current state: legacy monolithic `CardView` with `detail_item_message_text`, attachment/action children, and no `card_body`.
  - Expected prerequisite state: Epic 2 squared shell with a named `card_body` container.
  - Preserve the shell and all non-body children; chart mounting belongs inside `card_body`.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state: parses markdown and binds all message/attachment/action behavior directly in an Activity-coupled holder.
  - Expected prerequisite state: delegates body/card presentation to an adapter-agnostic binder.
  - Do not put Canvas math or JSON parsing back into the adapter.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Expected from Story 2.1 and updated by Epic 3.
  - It may select/mount the shared body renderer, but chart normalization/drawing should live in focused structured-rendering classes.
- Expected new file: a focused custom View such as `app/src/main/java/io/heckel/ntfy/ui/structured/StructuredChartView.kt`.
- Possible expected new file: `StructuredChartBlockView.kt` or a small XML layout if plot + labels are composite.
- Expected updates: the Story 3.1 shared model/parser/dispatcher files, exact paths determined by prerequisite implementation.
- Expected tests: pure model/geometry tests under `app/src/test/.../ui/structured/` and narrow rendering tests under `app/src/test` or `app/src/androidTest`.
- Resource updates should be minimal and named. Reuse `accent_ui`, `muted`, `text_caption`, `leading_caption`, and spacing tokens from Story 1. Add chart-specific dimensions only where the canonical contract requires them (plot height and stroke width).

### Preservation Requirements

- Any structured-rendering exception must continue through Story 3.1's safe raw-message fallback; never let a custom View crash RecyclerView binding/drawing.
- Rebinding empty/all-invalid data must hide/remove the entire chart block and clear old labels/path data.
- Preserve full card content and the current message's attachments, icon, action buttons, click links, selection state, unread state, and card interactions.
- Do not retain `Activity`, adapter, notification, or mutable JSON references in the custom View.
- Do not cache theme colors globally. Resolve them per View/context/configuration and refresh after configuration changes.
- Both `play` and `fdroid` variants must compile; preserve min SDK 26, compile/target SDK 36, and Java/Kotlin 17.

### Testing Requirements

- Pure tests should own most risk:
  - normalization and the 60-valid-point cap;
  - exact 12/13 label threshold;
  - domain expansion and finite coordinate outputs;
  - baseline behavior for mixed signs;
  - deterministic fallback-label formatting.
- Rendering tests should verify:
  - Canvas commands or pixels for bars vs line;
  - a visible single-point line;
  - correct baseline direction for negative bars;
  - 120dp plot measurement across densities;
  - current light/dark token colors;
  - no stale drawing after `bar → line → empty → bar` rebinding;
  - long labels, Korean labels, RTL locale, and increased font scale do not overlap or clip the plot.
- Manual parity fixtures:
  - bar: `12, 34`;
  - line: one point and multiple points;
  - mixed signs: `-10, 0, 20`;
  - constant/all-zero;
  - 12 labeled points and 13 labeled points;
  - 61 valid points;
  - invalid values interleaved before the 60th valid point.

### Previous Story Intelligence

- No earlier Epic 3 story artifact exists yet, so there are no implementation/review learnings from Stories 3.0–3.4 to consume today. Re-read those completed story records before development.
- The nearest card story artifact, Story 2.6, reinforces two rules relevant here: every bind must reset transient/recycled state, and card internals remain adapter-agnostic with host/adapter responsibilities outside the reusable renderer.
- Current generated stories consistently preserve user-owned worktree changes and avoid modifying unrelated sprint entries. Follow the same discipline.

### Git Intelligence

- The latest relevant commits (`5e3972d6`, `a4d9b073`) add the SPEC/epics and canonical UI-parity companions; no structured-card production code or chart implementation has landed.
- The current worktree contains user-owned generated story artifacts and a modified `sprint-status.yaml`. Update only this story's status entry and `last_updated`.
- The checked-in app remains the legacy renderer. This is planning-time context, not permission to bypass prerequisite stories.

### Library and Framework Requirements

- Required platform APIs: `android.view.View`, `android.graphics.Canvas`, `Paint`, and `Path`.
- No dependency upgrade is required. No MPAndroidChart, GraphView, Compose, SVG/chart wrapper, or other chart library may be added.
- Reuse Android resources and existing dependencies. Gson remains the JSON library; AppCompat/View/XML remains the rendering stack.
- Official Android guidance: custom drawing is performed by overriding `onDraw(Canvas)`; reuse drawing objects and calculate geometry from actual View size. See the Android Developers references below.

### Project Structure Notes

- Keep structured-card production code grouped under a focused package beneath `io.heckel.ntfy.ui` (for example `ui.structured`), following whatever package Stories 3.0–3.4 establish.
- Keep parsing/models, pure geometry, and View drawing separable. This supports fast JVM tests and prevents a large binder/ViewHolder god class.
- Do not edit database, network, notification delivery, manifest, navigation, or feed pagination code for this story.
- Do not hard-code raw `dp`, `sp`, or color literals in Kotlin; convert named resources once and use pixel values in Canvas.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.5, NFR3]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-5, CAP-6, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack and current card seam]
- [Source: `docs/ui-parity/message-format.md` §2.3, §2.4, §7 — chart protocol, sections reuse, full-content rule]
- [Source: `docs/ui-parity/components.md` §1 — `card_body` ownership and full-content requirement]
- [Source: `docs/ui-parity/design-tokens.md` — Accent Sub-Token Decision Table, typography, spacing]
- [Source: `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md` — binder/recycling guardrails]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — current message/attachment/action binding]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml` — current pre-Epic-2 row]
- [Source: `app/build.gradle` — SDK, language level, dependencies]
- [Android custom drawing guide](https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing)
- [Android `Canvas` API reference](https://developer.android.com/reference/android/graphics/Canvas)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- The customization resolver required Python 3.11; workflow customization was manually resolved from base TOML with no team/user overrides.
- Loaded the full sprint status and epic plan, canonical SPEC companions, nearest related story artifact, current renderer/layout/build files, official Android Canvas guidance, and recent git history.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story status set to `ready-for-dev`; implementation has not started.

### File List

- `_bmad-output/implementation-artifacts/3-5-chart-block-renderer-hand-drawn-canvas.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
