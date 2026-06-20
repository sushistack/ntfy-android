# Story 1.1: Color Token Resources (Light + Dark)

Status: ready-for-dev

## Story

As a developer,
I want every color token in `design-tokens.md` as a snake_case Android color resource with correct light and dark values,
so that all UI is built against `@color/...` keys rather than raw hex.

## Acceptance Criteria

1. Given the canonical Color Tokens table in `docs/ui-parity/design-tokens.md`, when Android resources are inspected, then all 25 canonical color keys exist in both `app/src/main/res/values/colors.xml` and `app/src/main/res/values-night/colors.xml`, using the exact documented `android-key` names and theme-specific hex values.
2. The three accent sub-tokens `accent_text`, `accent_ui`, and `accent_on_surface` exist in both qualifiers and match the companion's accent decision table; no generic replacement token is introduced.
3. Destructive/error redesign UI is documented and implemented to reuse `priority_max` or `priority_urgent`; this story introduces no new `error`, `danger`, or destructive design token and does not remove or rename the app's existing Material `md_theme_error*` resources.
4. An automated token-parity check compares both Android color files to the canonical expected key/value manifest and fails on a missing key, duplicate key, unexpected canonical token, wrong qualifier, or divergent hex value.
5. An automated raw-color check covers newly added parity UI source/resource paths and fails on raw `#RGB`, `#ARGB`, `#RRGGBB`, or `#AARRGGBB` literals except the literal general-tag and service-tag palette values explicitly owned by Story 1.2. Existing legacy colors are treated as a documented baseline, not silently mass-converted in this story.
6. Both checks are exposed as Gradle verification tasks and wired into the standard `check` lifecycle so the same commands can be run locally and by CI. They pass alongside Android resource processing for both light and night resources.
7. Existing Material 3 colors and current app aliases remain intact so the present UI continues to compile and render; this story adds the parity token API but does not re-theme existing screens or change the active Light/Dark/System behavior.

## Tasks / Subtasks

- [ ] Add the canonical light color resources (AC: 1, 2, 3, 7)
  - [ ] Add a clearly delimited `ntfy-web parity color tokens` section to `app/src/main/res/values/colors.xml`.
  - [ ] Define exactly the 25 names and light values listed in the token manifest below.
  - [ ] Preserve all existing `md_theme_*`, `action_bar`, detail background, chip, and AppCompat override resources.
- [ ] Add the canonical dark color resources (AC: 1, 2, 3, 7)
  - [ ] Add the corresponding section to `app/src/main/res/values-night/colors.xml`.
  - [ ] Use the exact dark values below, including intentionally identical cross-theme values.
  - [ ] Do not add glow values here; glow/elevation representation belongs to Story 1.2.
- [ ] Add deterministic token-parity verification (AC: 1, 4, 6)
  - [ ] Keep the expected light/dark maps in one small verification implementation or manifest; do not scrape Markdown with a fragile regex at build time.
  - [ ] Parse Android XML structurally and normalize hex case before comparison.
  - [ ] Detect duplicate canonical names rather than accepting the last parsed value.
  - [ ] Register a Gradle task such as `verifyColorTokens` and make `check` depend on it.
- [ ] Add the no-raw-color verification gate (AC: 5, 6)
  - [ ] Register a Gradle task such as `verifyNoRawUiColors`.
  - [ ] Scope it to parity UI files introduced from this initiative, with an explicit baseline/allowlist mechanism for pre-existing legacy files.
  - [ ] Permit only the Story 1.2 literal palette values when those files are added; comments and documentation should not create false positives.
  - [ ] Make `check` depend on the task and ensure violations print file, line, and literal.
- [ ] Verify integration and regression safety (AC: 4, 5, 6, 7)
  - [ ] Run the focused verification tasks.
  - [ ] Run `./gradlew check` for the available variants, or the repository's equivalent aggregate verification.
  - [ ] Run Android resource processing/assembly for at least one Play and one F-Droid debug variant to catch qualifier or duplicate-resource errors.
  - [ ] Add automated negative fixtures/tests proving wrong hex, missing key, duplicate key, and forbidden raw hex each fail the verifier.

## Dev Notes

### Canonical Token Manifest

This table is the implementation contract. Names and values must remain byte-for-byte equivalent after case normalization.

| Android key | Light | Dark |
|---|---:|---:|
| `bg` | `#F3F4F6` | `#0C0D0F` |
| `surface` | `#FFFFFF` | `#16181B` |
| `surface_2` | `#EEF0F2` | `#1C1F23` |
| `surface_active` | `#EEF0F2` | `#1C1F23` |
| `border` | `#E4E6E9` | `#23262B` |
| `control_border` | `#767B80` | `#8B9197` |
| `text` | `#1C1E21` | `#E8EAED` |
| `muted` | `#6A7076` | `#8B9197` |
| `accent_text` | `#0E7A48` | `#42D392` |
| `accent_ui` | `#1A9E5F` | `#42D392` |
| `accent_on_surface` | `#0C1A12` | `#0C1A12` |
| `priority_high` | `#BF6C15` | `#F5A95C` |
| `priority_max` | `#E5484D` | `#FF6B6E` |
| `priority_urgent` | `#C7353A` | `#FF6B6E` |
| `priority_high_on_surface` | `#241403` | `#241403` |
| `priority_max_on_surface` | `#1A0E0E` | `#1A0E0E` |
| `meter_ok` | `#0E7A48` | `#42D392` |
| `meter_track` | `#E4E6E9` | `#262A2F` |
| `meter_warning` | `#BF6C15` | `#F5A95C` |
| `meter_critical` | `#E5484D` | `#FF6B6E` |
| `topic_chip_bg` | `#E1F2EA` | `#143A2D` |
| `topic_chip_text` | `#136B43` | `#7CE6B4` |
| `button_fill` | `#F4F5F6` | `#F4F5F6` |
| `button_fill_text` | `#15171A` | `#15171A` |
| `focus_ring` | `#1A9E5F` | `#42D392` |

Note: the companion currently contains 25 rows in its Color Tokens table, despite the epic wording “every color token.” The manifest above intentionally contains those 25 canonical rows; verification must derive its expected count from this explicit list rather than a stale hard-coded prose count. If the companion changes, update the manifest and verifier together.

### Architecture and Scope Guardrails

- Keep the existing View/XML + AppCompat/Material 3 stack. Do not introduce Compose or a new color/theme library.
- Update only the two existing `colors.xml` files for production token resources. Android's `values/` and `values-night/` resolution supplies light/dark values under one stable `R.color` name.
- Do not rewrite existing `md_theme_*` resources or map the app theme to the new tokens yet. Story 1.3 owns theme selection/application; later UI stories consume these keys.
- Do not add literal tag/service colors in this story. Story 1.2 owns those palette resources and the dark-only glow/elevation representation.
- The canonical source order is: running ntfy-web app, then `docs/ui-parity/design-tokens.md`, then screenshots. Screenshots are not a source for hex extraction.
- Android accepts `#RGB`, `#ARGB`, `#RRGGBB`, and `#AARRGGBB`; the raw-color verifier must cover all forms and avoid matching resource references such as `@color/foo`.

### Existing Files to Preserve

- `app/src/main/res/values/colors.xml`: currently defines the complete light Material 3 palette plus `action_bar`, `detail_activity_background`, chip colors, and AppCompat status-guard overrides. Append a distinct canonical-token section; do not delete or rename existing entries.
- `app/src/main/res/values-night/colors.xml`: currently mirrors the Material palette for dark mode and defines dark aliases. Append the dark canonical-token section and preserve all current entries.
- `app/src/main/res/values/themes.xml` and `values-night/themes.xml`: read-only for this story. They currently bind `AppTheme` to `md_theme_*`; changing those bindings would expand scope into Story 1.3 and risk broad visual regressions.
- `app/build.gradle`: may be updated only to register/wire verification tasks. Preserve flavor behavior (`play`/`fdroid`) and existing Android/Kotlin configuration.

### Verification Design

- Prefer a small repository-owned verifier with deterministic fixtures over a shell-only grep chain. It should return non-zero and a useful diagnostic for every mismatch.
- Token parity must compare a set of canonical names, not every color in the legacy files; the files intentionally contain many unrelated Material colors.
- The raw-color gate cannot initially scan all existing UI/resource files without a baseline because this brownfield app already contains many raw hex values. Establish an explicit “new parity UI” path convention or checked-in baseline. New violations must not be hidden by broad directory exclusions.
- CI infrastructure is not currently present under `.github/workflows`. Wiring checks into Gradle's `check` lifecycle is the repository-local integration point; a future CI workflow can invoke `./gradlew check` without duplicating policy.

### Testing Requirements

- Positive test: both checked-in resource files match the full manifest.
- Negative tests/fixtures: missing token, wrong qualifier value, duplicate token, unknown extra canonical token, forbidden raw hex, and an allowed Story 1.2 palette literal.
- Resource build: process/assemble both store flavors in debug configuration to confirm resource merging and night qualifier validity.
- No screenshot/UI test is required because this story exposes resources without applying them.

### Project Structure Notes

- Production resource updates:
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values-night/colors.xml`
- Verification code/fixtures should live in a clearly named repository verification location consistent with the chosen Gradle task. Do not place build-policy code in app runtime packages.
- Expected build integration point: `app/build.gradle` unless a reusable root-level verification task is cleaner. Avoid adding external dependencies for XML parsing or grep.

### References

- [Source: docs/ui-parity/design-tokens.md#Color-Tokens]
- [Source: docs/ui-parity/design-tokens.md#Accent-Sub-Token-Decision-Table]
- [Source: docs/ui-parity/design-tokens.md#Glow-Effects-Dark-Only]
- [Source: docs/ui-parity/design-tokens.md#Literal-colors-no-token]
- [Source: _bmad-output/planning-artifacts/epics.md#Story-11-Color-token-resources-light--dark]
- [Source: _bmad-output/planning-artifacts/epics.md#Epic-1-Themeable-visual-foundation-tokens--lightdark--a11y-primitives]
- [Source: _bmad-output/specs/spec-ui-parity/SPEC.md#Capabilities]
- [Source: _bmad-output/specs/spec-ui-parity/SPEC.md#Constraints]
- [Source: _bmad-output/specs/spec-ui-parity/brownfield.md#Stack]
- [Source: app/src/main/res/values/colors.xml]
- [Source: app/src/main/res/values-night/colors.xml]
- [Source: app/src/main/res/values/themes.xml]
- [Source: app/src/main/res/values-night/themes.xml]
- [Source: app/build.gradle]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; customization was resolved manually from base/team/user TOML.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- No previous Epic 1 story exists; previous-story intelligence is not applicable.
- Recent commits establish the SPEC/companion documents as the source of truth and contain no prior implementation pattern for token resources.
- No external web research was needed: this story is governed by checked-in, preservation-validated token values and uses stable Android resource mechanisms already present in the project.

### File List

- `_bmad-output/implementation-artifacts/1-1-color-token-resources-light-dark.md`
