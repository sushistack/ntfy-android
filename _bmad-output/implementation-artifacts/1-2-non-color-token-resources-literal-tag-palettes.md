# Story 1.2: Non-color Token Resources + Literal Tag Palettes

Status: ready-for-dev

## Story

As a developer,
I want typography, spacing, radius, and elevation tokens plus the literal tag palettes as Android resources,
so that sizing, shape, elevation, and tag colors are all referenced by key.

## Acceptance Criteria

1. Given the canonical non-color tables in `docs/ui-parity/design-tokens.md`, when Android resources are inspected, then `spacing_1..7`, `radius_sm`, `radius_md`, `radius_full`, `radius_badge`, all documented font-size and line-height keys, `font_sans`, `font_mono`, and `shadow_flat/elev_1/elev_2` exist under the exact snake_case names in the manifest below.
2. Web pixel values are translated consistently for Android: spacing/radius/shadow geometry uses `dp`; text size and line height use `sp`. The checked-in values match the manifest exactly and are consumed through resources rather than duplicated literals.
3. Reusable `TextAppearance.Ntfy.*` styles pair each canonical text size with its matching line height and family. Sans styles resolve to bundled Plus Jakarta Sans with an Android-safe sans fallback; mono resolves to bundled JetBrains Mono with a monospace fallback. Font assets and family XML are available offline to both Play and F-Droid builds.
4. `shadow_flat`, `shadow_elev_1`, and `shadow_elev_2` expose the documented Android elevation mapping (`0dp`, `2dp`, `6dp`). This mapping is explicitly documented as the View-system approximation of the web CSS shadows; no external shadow library or Compose dependency is introduced.
5. The six general-tag background/text pairs and fixed service-tag background/text colors are defined verbatim as literal color resources. Two same-length typed arrays preserve the general background/text index pairing. These resources live in unqualified `values/` only, so both themes use the same literals.
6. General-tag palette order is exactly 0–5 from the manifest. This story does not implement tag hashing; Story 2.4 owns the 32-bit hash and golden vectors and must index these arrays without re-encoding their colors.
7. The dark glow specifications for `glow_priority_high`, `glow_priority_max`, and `glow_accent_dot` preserve the documented color/alpha and blur radius and are available only from the night-qualified glow resource set. Light mode neither resolves nor applies a glow.
8. One reusable View-system glow contract is implemented and documented for later consumers. It returns no glow in light mode and a `GlowSpec(color, blurRadiusDp)` in dark mode; priority bars, unread/status dots, deep-link highlights, and any chart glow must consume this contract rather than component-local blur/alpha literals. It must support minSdk 26 without relying solely on API 31 `RenderEffect`.
9. Automated verification fails on missing/duplicate/wrongly named resources, wrong units or values, palette order/length mismatch, literal divergence, a glow in the default qualifier, or component-local glow constants. Verification is wired into Gradle `check` and includes negative fixtures.
10. Existing Material resources, `fab_margin`, theme bindings, layouts, and current screen appearance remain unchanged. This story exposes the design-system API only: it does not re-theme screens, edit `fragment_detail_item.xml`, add theme switching, migrate legacy literals, or introduce Compose.

## Tasks / Subtasks

- [ ] Add canonical dimensions and elevation mappings (AC: 1, 2, 4, 10)
  - [ ] Extend `app/src/main/res/values/dimens.xml` without removing `fab_margin`.
  - [ ] Add all spacing, radius, text-size, line-height, and shadow keys from the manifest.
  - [ ] Document that notification cards use a square `0dp` shape directly; do not invent `radius_card` or an Android `rounded_card` token.
- [ ] Add offline typography resources and text appearances (AC: 1, 2, 3, 10)
  - [ ] Add licensed Plus Jakarta Sans and JetBrains Mono font assets under `res/font/`, including the weights required by the documented text appearances.
  - [ ] Add `font_sans` and `font_mono` family XML with Android-safe fallback behavior.
  - [ ] Add `TextAppearance.Ntfy.Display`, `Title`, `Subtitle`, `Body`, `BodySmall`, `Caption`, and `Mono` styles that reference the canonical size/leading/family resources.
  - [ ] Keep component-specific weight choices (for example semibold title or extra-bold badge) in component styles owned by later stories; do not create new size tokens.
- [ ] Add literal tag/service palette resources (AC: 5, 6)
  - [ ] Define `tag_general_bg_0..5`, `tag_general_text_0..5`, `tag_service_bg`, and `tag_service_text` with the exact literals below.
  - [ ] Define `tag_general_backgrounds` and `tag_general_texts` typed arrays in matching index order.
  - [ ] Keep the literals in default `values/` with no night overrides.
  - [ ] Integrate with Story 1.1's raw-color verifier by allowing only these 14 named literal resources; do not broaden the allowlist.
- [ ] Add the shared dark-only glow specification (AC: 7, 8, 10)
  - [ ] Store the three glow colors and radii in `values-night/`; do not create light-mode glow values.
  - [ ] Add a small runtime design-system helper (for example `io.heckel.ntfy.ui.design.GlowSpec`) that checks `UI_MODE_NIGHT_MASK` before resolving a night-only glow.
  - [ ] Use a minSdk-26-compatible drawing contract such as a shared `Paint.setShadowLayer`/custom-drawable path; API 31 `RenderEffect` may be an optimization, not the only implementation.
  - [ ] Ensure consumers can provide unclipped drawing bounds/layer behavior; the helper must not silently mutate unrelated view elevation.
  - [ ] Document token assignment: P4 uses `glow_priority_high`, P5 uses `glow_priority_max`, unread/status/accent dots and deep-link accent emphasis use `glow_accent_dot`. Charts glow only when their owning renderer explicitly requests accent glow.
- [ ] Add deterministic verification (AC: 1–9)
  - [ ] Extend the repository-owned token verifier introduced by Story 1.1 rather than creating a second unrelated framework.
  - [ ] Verify exact names, units, values, TextAppearance references, font-family presence, palette contents/order, and night-only glow placement.
  - [ ] Add negative fixtures for missing/duplicate dimensions, a `px` unit, wrong palette order, mismatched array lengths, an altered literal, a default-qualified glow, and a local glow radius/alpha.
  - [ ] Wire the focused verification task into `check`.
- [ ] Verify build and regression safety (AC: 9, 10)
  - [ ] Run the focused token/palette/glow verification tasks.
  - [ ] Run `./gradlew check`.
  - [ ] Run `./gradlew assemblePlayDebug assembleFdroidDebug`.
  - [ ] Confirm no existing layout or theme file changed unless required solely to host reusable TextAppearance styles.

## Dev Notes

### Canonical Resource Manifest

#### Spacing and Radius

| Android key | Android value |
|---|---:|
| `spacing_1` | `4dp` |
| `spacing_2` | `8dp` |
| `spacing_3` | `12dp` |
| `spacing_4` | `16dp` |
| `spacing_5` | `24dp` |
| `spacing_6` | `32dp` |
| `spacing_7` | `48dp` |
| `radius_sm` | `10dp` |
| `radius_md` | `16dp` |
| `radius_full` | `9999dp` |
| `radius_badge` | `6dp` |

`radius_md` remains valid for dialogs/empty-state tiles. Notification cards are explicitly square and must not consume it.

#### Typography

| Text appearance | Size key/value | Leading key/value | Family |
|---|---|---|---|
| `TextAppearance.Ntfy.Display` | `text_display` / `28sp` | `leading_display` / `34sp` | `font_sans` |
| `TextAppearance.Ntfy.Title` | `text_title` / `22sp` | `leading_title` / `28sp` | `font_sans` |
| `TextAppearance.Ntfy.Subtitle` | `text_subtitle` / `18sp` | `leading_subtitle` / `24sp` | `font_sans` |
| `TextAppearance.Ntfy.Body` | `text_body` / `16sp` | `leading_body` / `24sp` | `font_sans` |
| `TextAppearance.Ntfy.BodySmall` | `text_body_sm` / `14sp` | `leading_body_sm` / `20sp` | `font_sans` |
| `TextAppearance.Ntfy.Caption` | `text_caption` / `12sp` | `leading_caption` / `16sp` | `font_sans` |
| `TextAppearance.Ntfy.Mono` | `text_mono` / `14sp` | `leading_mono` / `20sp` | `font_mono` |

Use `android:textSize`, `android:lineHeight`, and `android:fontFamily` references. Do not substitute Material's default typography scale: several names overlap while values/families differ.

#### Shadow/Elevation Mapping

| Android key | Android value | Web source |
|---|---:|---|
| `shadow_flat` | `0dp` | `none` |
| `shadow_elev_1` | `2dp` | `0 1px 2px rgba(0,0,0,0.4)` |
| `shadow_elev_2` | `6dp` | two-layer shadow ending at `0 6px 16px rgba(0,0,0,0.3)` |

Android `View.elevation` cannot encode CSS blur stacks or colored shadows. These values are the stable elevation API for ordinary surfaces; colored glows use the separate shared glow contract.

#### Literal Tag Palettes

| Index | Background resource/value | Text resource/value |
|---:|---|---|
| 0 | `tag_general_bg_0` / `#332b52` | `tag_general_text_0` / `#c4b5fd` |
| 1 | `tag_general_bg_1` / `#143a34` | `tag_general_text_1` / `#7fe0cb` |
| 2 | `tag_general_bg_2` / `#3a2f14` | `tag_general_text_2` / `#f5c97a` |
| 3 | `tag_general_bg_3` / `#3a1f22` | `tag_general_text_3` / `#f5a3a5` |
| 4 | `tag_general_bg_4` / `#14303a` | `tag_general_text_4` / `#7fc8e0` |
| 5 | `tag_general_bg_5` / `#283a14` | `tag_general_text_5` / `#b7e07f` |

Fixed service colors:

- `tag_service_bg` = `#2a3142`
- `tag_service_text` = `#9db4d8`

Only the palette data belongs here. The hash implementation belongs to Story 2.4.

#### Dark-only Glow Manifest

| Glow | Color including alpha | Blur radius |
|---|---:|---:|
| `glow_priority_high` | `#44F5A95C` (26.7%) | `10dp` |
| `glow_priority_max` | `#55FF6B6E` (33.3%) | `10dp` |
| `glow_accent_dot` | `#FF42D392` | `7dp` |

Use exact alpha bytes derived from the documented CSS opacity (`0.267 × 255 ≈ 0x44`, `0.333 × 255 ≈ 0x55`). Keep these values in the night-qualified glow file and never duplicate them in component code.

### Architecture and Scope Guardrails

- Keep the existing View/XML + AppCompat/Material 3 stack. No Compose, design-system framework, or external shadow library.
- Story 1.1 owns canonical light/dark color tokens and the raw-hex policy. Reuse its verifier and allowlist contract.
- Story 1.3 owns theme selection and theme-to-token application. Do not alter `AppTheme` bindings here.
- Story 1.4 owns reduced-motion/focus primitives. Do not add animation behavior here.
- Story 2.1 owns `fragment_detail_item.xml`; Story 1.2 must not edit it or migrate current card visuals.
- Story 2.4 owns tag categorization, hash parity, and golden vectors.
- Existing legacy `dp`/`sp` literals remain a brownfield baseline. This story must not become a repo-wide visual migration.
- `res/font` assets must be bundled and license-compatible. Do not use downloadable fonts because offline/F-Droid behavior must be deterministic.

### Existing Files to Preserve

- `app/src/main/res/values/dimens.xml`: currently contains only `fab_margin`. Append or split resources without deleting/renaming it.
- `app/src/main/res/values/colors.xml` and `values-night/colors.xml`: preserve the existing Material palette and Story 1.1 canonical colors. Literal tag colors may live in a separate default-qualified file to keep policy obvious.
- `app/src/main/res/values/themes.xml` and `values-night/themes.xml`: existing `AppTheme` bindings remain unchanged. Prefer a new `typography.xml` for text appearances.
- `app/src/main/res/layout/fragment_detail_item.xml`: read-only for this story.
- `app/build.gradle`: change only for deterministic verification wiring; preserve both product flavors and Google Services handling.

### Project Structure Notes

Suggested production structure:

- UPDATE `app/src/main/res/values/dimens.xml`
- NEW `app/src/main/res/values/typography.xml`
- NEW `app/src/main/res/values/tag_palettes.xml`
- NEW `app/src/main/res/values-night/glows.xml`
- NEW `app/src/main/res/font/plus_jakarta_sans*.ttf` and family XML
- NEW `app/src/main/res/font/jetbrains_mono*.ttf` and family XML
- NEW `app/src/main/java/io/heckel/ntfy/ui/design/GlowSpec.kt` (or the project's equivalent small design-system package)
- UPDATE the Story 1.1 verifier/fixtures and Gradle task wiring

Do not add a default-qualified glow file merely to make lookup convenient. The shared helper must gate on night mode before resolving night-only resources.

### Testing Requirements

- Manifest test: all expected dimensions have exact names, units, and values.
- Typography test: every TextAppearance points to the expected size, leading, and family.
- Font test: both families and all referenced bundled files exist in merged resources for Play and F-Droid.
- Palette test: both arrays contain six entries, pair by index, and equal the literal manifest.
- Glow test: values exist in `values-night/`, are absent from default `values/`, and the helper returns no glow in light mode.
- Policy test: later parity code cannot add local glow alpha/radius constants or unapproved raw colors.
- No screenshot test is required because this story does not apply the resources to a screen.

### Previous Story Intelligence

- Story 1.1 defines 25 canonical color resources in `values/colors.xml` and `values-night/colors.xml`; preserve those keys and files.
- Extend Story 1.1's deterministic verifier and `check` integration instead of creating parallel validation infrastructure.
- Its raw-color gate explicitly reserves an allowlist for Story 1.2's literal tag/service palette. Restrict the allowance to the 14 exact resources in this story.
- Story 1.1 intentionally leaves glows/elevation to Story 1.2 and leaves theme binding to Story 1.3.

### Git Intelligence

- Recent commits add the preservation-validated SPEC, epics, sprint status, and UI parity companions; they contain planning/reference work, not an existing token implementation pattern.
- The canonical source order is the running ntfy-web app, checked-in companion documents, then screenshots.
- No relevant dependency or runtime architecture change has landed; implementation remains View/XML.

### Latest Technical Information

- Android resources should externalize dimensions, colors, arrays, fonts, and styles under stable `R` identifiers; `values-night/` is the platform mechanism for dark alternatives.
- Typed arrays can reference color resources and preserve palette order.
- Android's dark-theme guidance recommends theme/night-qualified resources rather than hardcoded light-only values.
- `RenderEffect` blur is API 31+, while this app supports API 26; it cannot be the sole glow mechanism.

Official references:

- https://developer.android.com/guide/topics/resources/providing-resources
- https://developer.android.com/guide/topics/resources/more-resources
- https://developer.android.com/develop/ui/views/theming/darktheme
- https://developer.android.com/reference/android/graphics/RenderEffect
- https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-12-Non-color-token-resources--literal-tag-palettes]
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-1-Themeable-visual-foundation-tokens--lightdark--a11y-primitives]
- [Source: docs/ui-parity/design-tokens.md#Radius-Tokens]
- [Source: docs/ui-parity/design-tokens.md#Shadow--Elevation-Tokens]
- [Source: docs/ui-parity/design-tokens.md#Glow-Effects-Dark-Only]
- [Source: docs/ui-parity/design-tokens.md#Typography-Tokens]
- [Source: docs/ui-parity/design-tokens.md#Spacing-Scale-4px-base]
- [Source: docs/ui-parity/design-tokens.md#Literal-colors-no-token]
- [Source: docs/ui-parity/components.md#CardTags--categorized-tag-row]
- [Source: _bmad-output/specs/spec-ui-parity/SPEC.md#Capabilities]
- [Source: _bmad-output/specs/spec-ui-parity/SPEC.md#Constraints]
- [Source: _bmad-output/specs/spec-ui-parity/brownfield.md#Stack]
- [Source: _bmad-output/implementation-artifacts/1-1-color-token-resources-light-dark.md]
- [Source: app/src/main/res/values/dimens.xml]
- [Source: app/src/main/res/values/themes.xml]
- [Source: app/src/main/res/values-night/themes.xml]
- [Source: app/build.gradle]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; base/team/user customization was resolved manually.
- Artifact and codebase analysis was split across parallel planning and repository investigations.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story intentionally defines resources and reusable contracts without changing current UI behavior.
- Ambiguous CSS-to-Android shadow/glow semantics were resolved into explicit, testable View-system mappings suitable for minSdk 26.

### File List

- `_bmad-output/implementation-artifacts/1-2-non-color-token-resources-literal-tag-palettes.md`
