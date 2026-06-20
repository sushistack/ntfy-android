# Story 2.3a: Card Header Static Content (Badge, Title, Unread Dot)

Status: ready-for-dev

## Story

As a user,
I want each card header to show priority, title, and unread state,
so that I can identify a notification at a glance without a detail screen.

## Acceptance Criteria

1. **Given** a card with priority 1–5  
   **When** its header renders  
   **Then** exactly one priority badge is always visible with these localized labels and token colors:
   - P1 → `notification_card_badge_min`, `@color/surface_2` background, `@color/muted` text
   - P2 → `notification_card_badge_low`, `@color/surface_2` background, `@color/muted` text
   - P3 → `notification_card_badge_normal`, `@color/surface_2` background, `@color/text` text
   - P4 → `notification_card_badge_high`, `@color/priority_high` background, `@color/priority_high_on_surface` text
   - P5 → `notification_card_badge_max`, `@color/priority_max` background, `@color/priority_max_on_surface` text  
   **And** the badge uses uppercase rendered text, extra-bold weight, `@dimen/text_caption`, `@dimen/radius_badge`, and horizontal/vertical padding equivalent to `spacing_2`/2dp.

2. **Given** an absent or invalid priority normalized by the existing priority path  
   **When** the header binds  
   **Then** it renders the P3 Normal badge; no new priority enum or fallback rule is introduced.

3. **Given** a notification with a non-blank title  
   **When** the header renders  
   **Then** the title displays the existing decoded/formatted title value, uses primary `@color/text`, body-size semibold styling, occupies the flexible middle of the header, and is constrained to one line with end ellipsis.

4. **Given** a notification whose title is blank  
   **When** the header renders  
   **Then** the decoded message-body string is used as the title fallback, including the existing titleless emoji-prefix behavior where applicable, and it is one-line end-ellipsized  
   **And** the body content remains present in `@id/card_body`; this fallback does not consume, hide, or truncate the actual body.

5. **Given** a persisted notification  
   **When** the header binds  
   **Then** the unread dot is visible only when the app's existing unread sentinel is true (`notification.notificationId != 0`, the current Android equivalent of web `new == 1`)  
   **And** it is 8dp × 8dp, circular, decorative/non-focusable, and filled with `@color/accent_ui`.

6. **Given** dark mode and an unread notification  
   **When** the dot renders  
   **Then** it uses Story 1.2's shared glow rule with `glow_accent_dot`  
   **And** light mode and read notifications have no accent-dot glow.

7. **Given** a recycled holder previously bound to another priority or unread state  
   **When** it is rebound  
   **Then** badge label, badge colors, title/fallback, dot visibility, and dot glow are reset deterministically with no stale state.

8. **Given** the redesigned static header  
   **When** it is inspected or navigated with accessibility services  
   **Then** the legacy priority icon and old per-card `⋯` overflow control are absent from the header and accessibility traversal  
   **And** this story adds no bell, X-delete button, delete behavior, tag/timestamp row, or tap-to-mark-read behavior.

9. **Given** Epic 2's staged implementation  
   **When** this story is merged  
   **Then** existing body rendering, links, icon, attachments, action buttons, selection, whole-card click/long-click, and Story 2.2's accent bar remain functional  
   **And** the redesigned card remains release-gated until Stories 2.1–2.4, including 2.3b, ship together.

## Tasks / Subtasks

- [ ] Add the static header contract to the Story 2.1 shell/binder seam (AC: 1–9)
  - [ ] Rework only the header portion of `fragment_detail_item.xml`; preserve `detail_item_card`, `card_priority_accent`, `card_body`, and the body/attachment/action subtree.
  - [ ] Add stable header IDs for the badge, title, and unread dot; keep the header vertically centered with token spacing.
  - [ ] Remove `detail_item_priority_image` and `detail_item_menu_button` from the layout and all binder lookups/listeners.
  - [ ] Do not add the Story 2.3b X-delete control early.
- [ ] Implement deterministic badge binding in `MessageCardBinder` (AC: 1, 2, 7)
  - [ ] Reuse `PRIORITY_MIN..PRIORITY_MAX` and the existing normalization path.
  - [ ] Map every priority to its exact string/background/text resources.
  - [ ] Apply uppercase at presentation time using locale-aware Android text transformation, while keeping localized resource values naturally cased for translators.
  - [ ] Reset text, colors, and drawable state on every bind.
- [ ] Implement title and fallback binding (AC: 3, 4, 7)
  - [ ] Reuse `decodeMessage`, `formatMessage`, and `formatTitle` semantics rather than duplicating Base64 or emoji logic.
  - [ ] Use the formatted title when non-blank and the formatted/decoded body string when titleless.
  - [ ] Keep body rendering unchanged and independently bound.
- [ ] Implement unread-dot binding (AC: 5–7)
  - [ ] Derive unread state from `notificationId != 0`; do not add a `new` database column in this story.
  - [ ] Reuse `@color/accent_ui`, the shared dark-only glow helper, and its `glow_accent_dot` resource.
  - [ ] Explicitly clear visibility and glow for read/light/recycled states.
- [ ] Add localization resources (AC: 1)
  - [ ] Add the five canonical keys to default English strings: Min, Low, Normal, High, Urgent.
  - [ ] Add Korean translations because Korean is an explicitly supported product locale; leave other locale files to the established Weblate pipeline unless repository policy requires placeholders.
  - [ ] Do not reuse `common_priority_*_name`; those longer phrases are not the card badge contract.
- [ ] Add focused regression tests (AC: 1–9)
  - [ ] Table-test all five badge mappings plus invalid-priority → P3.
  - [ ] Test titled, titleless, Base64-decoded, emoji-prefixed, long-title, and empty-message fallback cases.
  - [ ] Test unread/read visibility, day/night glow, and unread→read plus P5→P1 recycling.
  - [ ] Assert title `maxLines == 1` and `ellipsize == END`, badge dimensions/style resources, and an 8dp circular dot.
  - [ ] Add a layout/static contract assertion that legacy priority/menu IDs are gone while shell/body IDs remain.
  - [ ] Run focused tests and Play/F-Droid debug resource processing/assembly.

## Dev Notes

### Dependency Gate

- Consume the implemented outputs of Stories 1.1, 1.2, 2.1, and 2.2. At story-creation time their artifacts are `ready-for-dev`, but the working tree still contains the legacy layout/adapter and no `MessageCardBinder`; implement/merge prerequisites first and adapt to their final APIs.
- Story 1.1 owns badge/unread color tokens. Story 1.2 owns typography, spacing, `radius_badge`, `glow_accent_dot`, and the shared glow mechanism. Story 2.1 owns the shell and binder. Story 2.2 owns only the left accent bar.
- Do not recreate missing prerequisite resources or fold prerequisite implementation into this story.

### Current State and Required Changes

- `fragment_detail_item.xml` currently places date, legacy priority icon, 10dp unread dot, title, and `⋯` menu in a loose ConstraintLayout. After 2.1 it should instead expose the reusable shell/body seam. This story changes the header subtree only.
- `DetailAdapter.DetailViewHolder.bind()` currently:
  - shows unread when `notificationId != 0`;
  - hides the title entirely when `title == ""`;
  - uses legacy priority icons and conditionally hides P3;
  - builds an attachment/copy overflow popup on `detail_item_menu_button`.
- The final implementation belongs in `MessageCardBinder`, not a new monolithic adapter holder. Remove obsolete view references without moving unrelated attachment/action behavior.
- Removing the old card overflow means attachment/file interactions must remain available through their existing attachment-box interaction. If `copy contents` or another legacy overflow-only action would become unreachable, preserve it through the binder's existing explicit interaction surface or document it for the owning redesign story; do not silently delete non-header capabilities.

### Unread Semantics

- The Android model has no `new` Boolean. `Notification.notificationId != 0` is the persisted unread/new sentinel used by current row dots, subscription counts, and DAO read operations.
- `markAllAsRead()` and `markAsReadBySequenceId()` clear `notificationId` to zero. Story 2.5 changes card-tap behavior; this story only reflects state.
- Do not add or migrate a database field, infer unread from timestamps, or use `deleted`.

### Title Semantics

- For a non-empty title, preserve `formatTitle(notification)` behavior, including emoji shortcodes rendered before the title.
- For titleless notifications, the visible header fallback should use the decoded/formatted message string (`formatMessage(notification)`), matching current emoji-prefix behavior and Base64 fault tolerance.
- The fallback is a header summary only. The complete plain/Markdown body still renders in `card_body`; do not strip Markdown, alter link handling, or introduce structured-body parsing here.
- Treat blank according to the persisted contract. If prerequisite code normalizes whitespace-only titles, follow that established behavior consistently and test it.

### Badge and Header Layout

- Header anatomy is badge → flexible title → unread dot. Story 2.3b later appends X-delete.
- Use Views/XML and project token resources. Do not add Compose, a chip library, or a new component dependency.
- Badge labels must remain localizable. Do not hard-code English or derive labels from enum names.
- Badge presentation: caption size, uppercase, extra-bold, 6dp badge radius, 8dp horizontal and 2dp vertical padding.
- Title presentation: body size, semibold, primary text, one line, end ellipsis. Ensure the future X button has room without rewriting title constraints.
- Dot presentation: 8dp circle. It is decorative because card state is already conveyed by surrounding UI; it must not become an independent click/focus target.

### Preservation and Scope Guardrails

- Preserve shell click/focus behavior and Story 2.2 accent binding.
- Preserve body, Markdown/plain links, notification icon, attachment image/file controls, dynamic actions, selection, and click/long-click behavior.
- Remove only the legacy header priority icon and old per-card overflow control required by the target design. There is no legacy per-card bell in the current row.
- Do not implement X-delete/confirm (2.3b), tags/timestamp (2.4), tap-to-read/no-navigation (2.5), skeleton/animation/highlight (2.6), swipe actions (Epic 4), or structured rendering (Epic 3).
- No raw hex, ad-hoc text sizes, or duplicate glow/radius resources.

### File Structure Requirements

- UPDATE `app/src/main/res/layout/fragment_detail_item.xml`
  - Change: static header structure and legacy header-control removal.
  - Preserve: squared shell, accent placeholder, `card_body`, body and interaction contracts from 2.1/2.2.
- UPDATE `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Change: badge/title/unread binding and complete recycled-state reset.
- UPDATE `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` only as required by the final 2.1 extraction
  - Remove obsolete legacy view logic; retain adapter/list/selection responsibilities.
- UPDATE `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ko/strings.xml`
  - Add the five badge keys; preserve all existing translations.
- UPDATE/ADD focused tests under `app/src/test/java/io/heckel/ntfy/ui/` and, only if necessary, `app/src/androidTest/java/io/heckel/ntfy/ui/`.
- Do not modify the database schema, DAO, repository, Activity navigation, Epic 3 body layouts, or dependency versions.

### Testing Requirements

- Prefer a pure `priority -> BadgePresentation` mapping seam and a small title-selection seam so most behavior is JVM-testable.
- Include RecyclerView rebind tests; stale badge color or dot glow is the highest direct regression risk.
- Resolve day/night resources and compare against named tokens, not screenshots or literal colors.
- Verify accessibility traversal contains the card/header text but no separate decorative dot, removed priority icon, or removed overflow button.
- Manual smoke matrix: P1–P5 × light/dark × read/unread; titled/titleless; long localized strings; scroll enough to force recycling.

### Previous Story Intelligence

- Story 2.2 establishes that priority styling belongs in `MessageCardBinder`, must use existing constants/normalization, and must reset visual state on every bind.
- Story 2.2 preserves legacy priority icons only until this story; 2.3a is their explicit replacement point.
- Story 2.1 makes the binder adapter/activity-agnostic and protects attachments/actions/body behavior. Keep header work inside that boundary.
- Both predecessor stories record that prerequisites are planned but not yet implemented in the current working tree. Sequence implementation rather than coding against the legacy holder as the target architecture.

### Git Intelligence

- Recent commits add only UI-parity documentation and planning artifacts; no redesigned-card implementation has landed.
- Historical row commits show accumulated link, icon, attachment, GIF, and dynamic-action behavior. Treat those as regression-sensitive when restructuring the header.
- The worktree contains user-owned uncommitted story/status artifacts. Preserve unrelated changes and formatting.

### Latest Technical Information

- No web research or dependency upgrade is required. This story uses the repository-pinned View stack (AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0, Material 1.13.0) and project-owned token contracts.
- Checked-in product companions are authoritative for badge anatomy and token mapping; implementation must not substitute current Material defaults.

### Project Structure Notes

- Keep production card code under `app/src/main/java/io/heckel/ntfy/ui/`.
- Keep the shared shell at `app/src/main/res/layout/fragment_detail_item.xml`.
- Do not create a second card/header layout unless the completed 2.1 architecture explicitly establishes an included reusable header component.
- No project-level `project-context.md` was found; the SPEC kernel, companions, epics, predecessor stories, and current code are the canonical context.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 2, Story 2.3a, merge gate]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-3, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack, card hotspot]
- [Source: `docs/ui-parity/components.md` §1 Header band and §2 PriorityBadge]
- [Source: `docs/ui-parity/design-tokens.md` — colors, radius, typography, spacing, glow]
- [Source: `docs/ui-parity/CHANGELOG-redesign-2026-06.md` — removed overflow, all-priority badges]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `_bmad-output/implementation-artifacts/2-2-priority-accent-bar-all-five-priorities.md`]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — current bind behavior]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` — Notification and read-state DAO]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Util.kt` — formatting/decoding helpers]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Constants.kt` — priority constants]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; base/team/user TOML was resolved manually.
- Artifact, predecessor-story, git-history, and repository analysis were run in parallel subprocesses.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Explicitly mapped web `new == 1` to the existing Android `notificationId != 0` sentinel without schema expansion.
- Protected body/attachment/action behavior while making 2.3a the explicit removal point for legacy priority/menu header controls.

### File List

- `_bmad-output/implementation-artifacts/2-3a-card-header-static-content-badge-title-unread-dot.md`
