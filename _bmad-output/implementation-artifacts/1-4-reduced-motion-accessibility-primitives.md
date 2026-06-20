# Story 1.4: Reduced-motion & accessibility primitives

Status: ready-for-dev

## Story

As a developer,
I want shared helpers for reduced-motion detection and a standard focus indicator,
so that later epics inherit consistent, owned accessibility behavior instead of reinventing it.

## Acceptance Criteria

1. **Given** the system has disabled animator-based animations, including animator duration scale `0`, **when** the shared reduced-motion helper is queried, **then** it reports reduced motion as enabled; when system animators are enabled it reports reduced motion as disabled.
2. **Given** a focusable View uses the shared focus-indicator style, **when** it receives Android's focused drawable state through keyboard, D-pad, switch-access, or accessibility navigation, **then** a density-independent 2dp outline using `@color/focus_ring` is visible without replacing the View's normal, pressed, selected, disabled, ripple, or content background.
3. **Given** the canonical light-theme token pairs in `design-tokens.md`, **when** WCAG contrast is calculated, **then** text pairs meet at least 4.5:1 and non-text UI/focus pairs meet at least 3:1, with the checked pairs, ratios, target type, formula, and result committed as project documentation.
4. **Given** a later feature needs animation or a focus treatment, **when** it is implemented, **then** it can consume these primitives without reading `Settings.Global`, duplicating focus drawables, introducing Compose, or adding a third-party accessibility/motion library.

## Tasks / Subtasks

- [ ] Add the shared reduced-motion API (AC: 1, 4)
  - [ ] Create `app/src/main/java/io/heckel/ntfy/ui/accessibility/ReducedMotion.kt`.
  - [ ] Expose a clearly named query such as `ReducedMotion.isEnabled(): Boolean`, implemented as the inverse of `ValueAnimator.areAnimatorsEnabled()`.
  - [ ] Keep a small pure/internal seam that accepts the animator-enabled value so both outcomes can be unit tested without mutating device settings.
  - [ ] Document that callers query when deciding whether to start an animation; do not cache the result for the process lifetime.
  - [ ] Do not read/write `Settings.Global.ANIMATOR_DURATION_SCALE` in production or request `WRITE_SECURE_SETTINGS`.

- [ ] Add the reusable View/XML focus indicator (AC: 2, 4)
  - [ ] Add a focused-state selector/foreground under `app/src/main/res/drawable/`; its focused item draws a transparent rectangular 2dp stroke using `@color/focus_ring`, and its default item is transparent.
  - [ ] Add one reusable style in `app/src/main/res/values/themes.xml` or a dedicated `styles.xml`, applying the indicator as a foreground/overlay rather than replacing the control background.
  - [ ] Keep the primitive squared/shape-neutral; component-specific shape remains the component's responsibility.
  - [ ] Do not force focusability, clickability, dimensions, padding, color, role, or content description. Callers own semantics and interaction.
  - [ ] Verify the full stroke remains visible and is not clipped at the View edge.

- [ ] Test both primitives (AC: 1, 2, 4)
  - [ ] Unit-test both inputs: animators enabled → reduced motion off; animators disabled → reduced motion on.
  - [ ] Add a resource/instrumentation assertion or drawable-state test proving the shared style resolves to `@color/focus_ring`, uses a 2dp stroke, and preserves a separate content background.
  - [ ] Add only the minimum JUnit/AndroidX test dependencies if the module still has no test setup.
  - [ ] Run lint/resource linking for both `playDebug` and `fdroidDebug`.

- [ ] Commit the light-theme contrast check (AC: 3)
  - [ ] Create `docs/ui-parity/accessibility-contrast.md`.
  - [ ] Record the WCAG relative-luminance formula and evaluate at least the canonical pairs listed below.
  - [ ] Classify text/icon pairs against 4.5:1 and borders/focus indicators against 3:1; do not silently reclassify a failing text color as non-text.
  - [ ] Recompute from the actual Story 1.1 resources if their values differ at implementation time, and resolve failures before completion.

## Dev Notes

### Scope and dependency boundaries

- This story creates shared primitives; it does **not** retrofit every existing animation or focusable control. Stories 2.6 and 4.2 consume reduced motion for arrival/deep-link effects; Story 2.1 and later controls consume the focus indicator.
- Story 1.1 owns `@color/focus_ring`. Reuse that exact resource; never add a duplicate color or fallback hex.
- Story 1.2 owns the broader non-color token set. The 2dp focus stroke is component anatomy from UX-DR4, not permission to invent a new design-token family.
- Story 1.3 owns app theme persistence/application. Reduced motion is system accessibility state, not another app preference.
- Stories 1.1–1.3 have ready-for-dev artifacts but are not implemented yet. Resource linking requires Story 1.1's token to be present on the implementation branch.

### Reduced-motion implementation contract

- The app's `minSdkVersion` is 26, exactly matching the API level where `ValueAnimator.areAnimatorsEnabled()` was introduced.
- The framework API reports system-wide animator availability, including duration scale `0` and system conditions such as battery saver disabling animators. Prefer it over coupling production code to global settings storage.
- Use positive caller semantics: reduced motion is enabled when animators are disabled. Consumers must choose an immediate/static final state rather than merely shortening an animation.
- Query at animation decision time. This primitive does not need a `ContentObserver`, duration-scale listener, Flow, or process-wide cache.
- Existing FAB fades in `MainActivity` and full-screen dialog XML animations are preservation context only; changing them is outside this story.

### Focus-indicator implementation contract

- This remains a Kotlin + View/XML project; do not introduce Jetpack Compose.
- Use `state_focused` and a foreground/overlay so the primitive does not erase component backgrounds, ripples, shape drawables, or state lists.
- Treat the web's 2px ring as a 2dp Android stroke for density-independent visual parity.
- The primitive visualizes focus but does not manufacture semantics. Components remain responsible for focusability, labels, content descriptions, roles, and actions.
- Verify whether the styled widget also draws Android's default focus highlight. If double-highlighting occurs, disable the default highlight only in the shared style and retain the token ring.

### Required contrast record

Expected ratios from the canonical light values currently documented:

| Pair | Use | Ratio | Target |
|---|---|---:|---:|
| `text` / `bg` | text | 15.18:1 | 4.5:1 |
| `text` / `surface` | text | 16.71:1 | 4.5:1 |
| `muted` / `bg` | secondary text | 4.55:1 | 4.5:1 |
| `muted` / `surface` | secondary text | 5.01:1 | 4.5:1 |
| `accent_text` / `bg` | text/icon | 4.90:1 | 4.5:1 |
| `accent_text` / `surface` | text/icon | 5.39:1 | 4.5:1 |
| `accent_ui` / `bg` | non-text UI | 3.13:1 | 3:1 |
| `accent_ui` / `surface` | non-text UI | 3.44:1 | 3:1 |
| `focus_ring` / `bg` | focus indicator | 3.13:1 | 3:1 |
| `focus_ring` / `surface` | focus indicator | 3.44:1 | 3:1 |
| `accent_on_surface` / `accent_ui` | text on accent fill | 5.20:1 | 4.5:1 |
| `priority_high_on_surface` / `priority_high` | badge text | 4.57:1 | 4.5:1 |
| `priority_max_on_surface` / `priority_max` | badge text | 4.82:1 | 4.5:1 |
| `topic_chip_text` / `topic_chip_bg` | chip text | 5.63:1 | 4.5:1 |
| `button_fill_text` / `button_fill` | button text | 16.45:1 | 4.5:1 |
| `control_border` / `bg` | non-text boundary | 3.88:1 | 3:1 |
| `control_border` / `surface` | non-text boundary | 4.27:1 | 3:1 |

These values guide implementation but do not replace recomputation from committed resources.

### Architecture and project compliance

- Suggested package: `io.heckel.ntfy.ui.accessibility`; keep UI accessibility concerns out of delivery, database, and service packages.
- No API, database, navigation, manifest permission, localization, or network changes are required.
- No production dependency is required. Preserve both product flavors and Java/Kotlin 17 compatibility.
- Story 1.3's segmented theme control also requires accessible focus treatment. It should consume this primitive when implementation order permits rather than create its own ring.

### Testing and completion guardrails

- A helper that returns a constant, only checks app preferences, or caches startup state forever fails AC1.
- A focus drawable that replaces existing backgrounds/ripples, hard-codes a color, or is not exposed through one reusable style fails AC2.
- Citing the existing claim in `design-tokens.md` without a reproducible checked-pairs document fails AC3.
- Do not claim reduced-motion compliance for later animations until their own stories branch to static/immediate states.

### Project Structure Notes

- Expected new files:
  - `app/src/main/java/io/heckel/ntfy/ui/accessibility/ReducedMotion.kt`
  - `app/src/main/res/drawable/focus_indicator.xml` (exact name may follow local conventions)
  - `docs/ui-parity/accessibility-contrast.md`
  - focused tests under `app/src/test/...` and/or `app/src/androidTest/...`
- Expected updates:
  - `app/src/main/res/values/themes.xml` or a dedicated style resource
  - `app/build.gradle` only if a missing test dependency is required
- Do not edit card/feed layouts or animation call sites in this story.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 1, Story 1.4, UX-DR4, Stories 2.6/4.2]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-1, CAP-9, Constraints, Assumptions]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack]
- [Source: `docs/ui-parity/design-tokens.md` — Color Tokens and Accent Sub-Token Decision Table]
- [Source: `docs/ui-parity/components.md` — Notification Card focus ring]
- [Source: `app/build.gradle` — minSdk 26, compile/target SDK 36, View/XML dependencies]
- [Android `ValueAnimator.areAnimatorsEnabled()` API](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled())
- [Android `Settings.Global.ANIMATOR_DURATION_SCALE` API](https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE)

## Previous Story Intelligence

- Story 1.3 establishes that the Appearance segmented control needs keyboard, D-pad, switch-access, and screen-reader support. Reuse this story's focus primitive there when implementation order permits.
- Story 1.1 defines the exact `focus_ring` light/dark values and preserves existing Material colors; consume its resource contract without changing its manifest or verification policy.
- Story 1.2 explicitly assigns reduced-motion and focus primitives to Story 1.4; do not mix them into its typography, spacing, palette, elevation, or glow contract.
- Recent commits contain planning/reference documentation only; no established Story 1.x runtime implementation pattern exists yet.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- The customization resolver fallback was used because the available `python3` lacks Python 3.11 `tomllib`; base/team/user TOML was resolved manually.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story is implementation-ready subject to the explicit Story 1.1 token prerequisite.

### File List

- `_bmad-output/implementation-artifacts/1-4-reduced-motion-accessibility-primitives.md`
