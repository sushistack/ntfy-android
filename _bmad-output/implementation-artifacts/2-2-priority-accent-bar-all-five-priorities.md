# Story 2.2: Priority Accent Bar (All Five Priorities)

Status: ready-for-dev

## Story

As a user,
I want a colored left bar on each card matching its priority,
so that I can gauge urgency at a glance, identically to web.

## Acceptance Criteria

1. **Given** a card of priority 1–5  
   **When** it renders  
   **Then** a 4dp full-height left bar shows:
   - P1 Min → `@color/muted`
   - P2 Low → `@color/muted`
   - P3 Normal → `@color/text`
   - P4 High → `@color/priority_high`
   - P5 Max/Urgent → `@color/priority_max`
   **And** each mapping is asserted by resolved token equality, not visual inspection.

2. **Given** dark mode  
   **When** a P4 or P5 card renders  
   **Then** the bar uses Story 1.2's shared glow rule with `glow_priority_high` or `glow_priority_max`, respectively  
   **And** the measurable shadow/outline output resolves to the matching token.

3. **Given** dark mode  
   **When** a P1, P2, or P3 card renders  
   **Then** the accent bar has no priority glow.

4. **Given** light mode  
   **When** any priority renders  
   **Then** no priority glow is applied.

5. **Given** a recycled holder previously bound to P4 or P5  
   **When** it is rebound to P1, P2, or P3, or rendered in light mode  
   **Then** prior glow state is explicitly cleared and the new color is correct.

6. **Given** an invalid or absent upstream priority  
   **When** it is normalized and bound  
   **Then** existing normalization treats it as P3 and the bar uses `@color/text`.

## Tasks / Subtasks

- [ ] Bind Story 2.1's accent placeholder from `MessageCardBinder` (AC: 1–6)
  - [ ] Reuse the five existing `PRIORITY_*` constants; add no duplicate enum/constants.
  - [ ] Resolve colors exclusively from required token resources.
  - [ ] Keep color and glow selection independent.
  - [ ] Reset color and glow on every bind.
- [ ] Apply the shared dark-only glow rule (AC: 2–5)
  - [ ] Reuse Story 1.2's helper/resource contract.
  - [ ] Apply glow only to dark P4/P5.
  - [ ] Explicitly remove glow for P1–P3 and all light-mode binds.
- [ ] Preserve layout and behavior (AC: 1–6)
  - [ ] Keep the bar 4dp wide, on the physical left edge, and full height inside the clipped card.
  - [ ] Keep it decorative, non-clickable, non-focusable, and outside accessibility traversal.
  - [ ] Keep this logic out of `DetailActivity`, adapters, and body renderers.
- [ ] Add focused automated tests (AC: 1–6)
  - [ ] Assert all five priority/token mappings.
  - [ ] Assert dark P4/P5 glow and no dark P1–P3 glow.
  - [ ] Assert no light-mode glow for all priorities.
  - [ ] Assert P5→P1 and P4→P3 recycling clears glow.
  - [ ] Assert invalid/null normalization produces P3 styling.

## Dev Notes

### Dependency Gate

- This story consumes, but must not absorb or duplicate:
  - Story 1.1: `muted`, `text`, `priority_high`, and `priority_max` light/night resources.
  - Story 1.2: `glow_priority_high`, `glow_priority_max`, and the shared glow rule.
  - Story 2.1: adapter-agnostic `MessageCardBinder` and the 4dp placeholder in `fragment_detail_item.xml`.
- These prerequisites were not implemented in the working tree when this story was created. Merge/implement them first. Adapt to their final APIs without recreating them.
- Epic 2 is release-gated: do not expose the redesigned card before Stories 2.1–2.4, including 2.3b, ship together.

### Developer Context

- Use the existing View/XML + AppCompat stack; do not introduce Compose.
- Priority styling belongs in `MessageCardBinder`, reused by the current list and future Epic 4 feed.
- Incoming values already normalize through `toPriority()` and persisted `Notification.priority` defaults to 3.
- Current legacy icons appear for P1/P2/P4/P5 and are hidden for P3. Add the bar without removing this behavior; Story 2.3a owns the header replacement.
- The bar is decorative and must not alter the card's click/focus target.

### Architecture Compliance

- Reuse `io.heckel.ntfy.util.Constants.kt`; do not create another priority model.
- Prefer resource qualifiers/shared helpers over duplicate manual dark-mode detection.
- Use exact tokens: no raw hex, Material approximations, new glow tokens, or private shadow values.
- Do not simulate glow by changing the fill color; fill and glow are separate testable outputs.
- Story 2.1 owns shell XML. Normally this story updates the binder, not the layout structure.
- Epic 3 fills `@id/card_body`; do not edit future `view_card_<type>.xml` layouts.

### Existing Files and Preservation Requirements

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Pre-2.1: legacy rounded card with no accent placeholder or `card_body`.
  - Expected after 2.1: squared shell, `card_body`, and full-height 4dp placeholder.
  - Preserve clipping, full card height, tap/focus behavior, attachments, actions, and shell constraints.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Pre-2.1: Activity-coupled holder; `renderPriority()` selects legacy icons.
  - Expected after 2.1: delegates reusable binding to `MessageCardBinder`.
  - Preserve markdown/links, attachments, actions, selection, click/long-click, and legacy icons.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Expected from 2.1 and the primary UPDATE file for this story.
  - Add deterministic priority → color/glow binding with complete state reset.
- `app/src/main/java/io/heckel/ntfy/util/Constants.kt`
  - Read/reuse only.
- `app/src/main/java/io/heckel/ntfy/util/Util.kt`
  - Preserve `toPriority()` fallback to `PRIORITY_DEFAULT`.
- `app/src/main/res/values/colors.xml` and `values-night/colors.xml`
  - Consume prerequisite tokens; do not add divergent substitutes.
- `app/build.gradle`
  - Change only if prerequisite work has not established the minimal test dependencies.

### Testing Requirements

- Prefer a pure mapping seam so priority and glow choices can be unit-tested without screenshots.
- Test resolved resources in day/night configurations against the named tokens.
- Include holder-rebind coverage; stale P4/P5 glow leaking onto P1–P3 is the critical RecyclerView regression.
- If the shared glow needs a View/drawable environment, add narrow instrumentation or Robolectric coverage rather than manual-only validation.
- Manual smoke check: display P1–P5 together in both themes, verify 4dp/full-height bars, verify only dark P4/P5 glow, then scroll enough to force recycling.

### Project Structure Notes

- Primary update: `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`.
- Tests: `app/src/test/java/io/heckel/ntfy/ui/`, plus `app/src/androidTest/java/io/heckel/ntfy/ui/` only if required.
- No new production dependency is expected.
- No database, delivery, navigation, body-renderer, or localization work belongs here.

### Git Intelligence

- Recent commits contain planning/reference artifacts; no Epic 2 implementation pattern has landed.
- Preserve unrelated working-tree changes and generated stories.
- Do not treat the legacy adapter as the target architecture while Story 2.1 remains pending.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 2, Stories 2.1–2.2 and release gate]
- [Source: `docs/ui-parity/components.md` §1 — priority accent bar]
- [Source: `docs/ui-parity/design-tokens.md` — colors and dark-only glows]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` CAP-2 and Constraints]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — stack and card hotspot]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Constants.kt` — priority constants]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Util.kt` — normalization]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — existing binding]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml` — pre-2.1 layout]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- The customization resolver required Python 3.11; customization was manually resolved from `customize.toml` with no team/user overrides.
- Parallel artifact and repository analysis completed.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Explicit prerequisite and RecyclerView recycling guardrails included.

### File List

- `_bmad-output/implementation-artifacts/2-2-priority-accent-bar-all-five-priorities.md`
