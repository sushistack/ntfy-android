# Story 3.4: `list` Block Renderer

Status: ready-for-dev

## Story

As a user,
I want ordered and bulleted lists,
so that step and result payloads render as real, fully visible lists.

## Acceptance Criteria

1. **Given** a parsed structured block with `type:"list"`, **when** `ordered` is exactly `true`, **then** every item renders in source order with a visible one-based decimal marker (`1.`, `2.`, `3.`, …).
2. **Given** a parsed structured block with `type:"list"`, **when** `ordered` is absent, false, null, or not a Boolean true value, **then** every item renders in source order with a visible bullet marker (`•`).
3. **Given** an `items` array containing JSON strings, numbers, booleans, nulls, objects, or arrays, **when** it is normalized for rendering, **then** each array entry is deterministically coerced to text rather than dropped or causing a crash; strings remain unquoted and non-string values use their compact JSON representation.
4. **Given** a missing, null, or non-array `items` field, **when** the list renderer is invoked, **then** it renders an empty body without crashing, synthetic placeholder rows, or stale content from a recycled card.
5. **Given** an empty array or an array containing empty-string items, **when** it renders, **then** the array cardinality and ordering are preserved: an empty array renders no rows, while an empty-string entry still renders its marker and an empty text value.
6. **Given** any list item count or item length, **when** the block renders, **then** all items and complete item text are present with no `maxLines`, ellipsize, clipping, compact/preview mode, "show more", or item-count cap.
7. **Given** the list block UI, **when** it is displayed in light or dark theme, **then** item text and markers use `body_sm` typography (`@dimen/text_body_sm` and `@dimen/leading_body_sm`) and `@color/muted`; spacing uses existing token dimensions and no raw color or ad-hoc pixel value is introduced.
8. **Given** a long item that wraps to multiple lines, **when** it renders, **then** continuation lines align with the item text rather than under the marker, and the marker remains visually associated with its item.
9. **Given** a card/body view reused for another notification or structured block, **when** the renderer binds again, **then** all prior list rows, markers, text, listeners, and visibility state are cleared before the new model is applied.
10. **Given** Story 3.1's structured-body dispatcher, **when** it receives a top-level list spec or a list block from Story 3.7 sections, **then** it delegates to the same reusable list renderer and mounts the result only inside `@id/card_body` (or the sections-owned block host); it does not modify `fragment_detail_item.xml`.
11. **Given** malformed list data or an unexpected rendering exception, **when** the complete body pipeline renders the notification, **then** the Story 3.1 safety boundary remains intact: the card never crashes and the raw message fallback can replace the structured body.
12. **Given** list items are rendered, **when** accessibility services inspect the block, **then** item order and marker text are exposed in reading order and each marker/item pair is understandable as one list entry; decorative container views do not add duplicate announcements.
13. **Given** implementation review, **when** dependencies and scope are inspected, **then** the renderer uses Kotlin + Views/XML only, adds no Compose or list/markdown rendering dependency, does not route structured-list content through Markwon, and does not implement chart, kv, markdown, sections orchestration, heuristic-kv, or card-shell changes.

## Tasks / Subtasks

- [ ] Define/extend the structured list model and normalization boundary (AC: 1–5, 11)
  - [ ] Reuse the `CardSpec`/block model and JSON parser established by Stories 3.0–3.1; do not parse the entire notification body again inside the view renderer.
  - [ ] Represent `ordered` with a default of false and preserve `items` as JSON values until deterministic string coercion.
  - [ ] Normalize strings without JSON quotes and normalize non-strings with compact JSON text; treat missing/non-array `items` as empty.
  - [ ] Keep normalization pure and independently unit-testable.
- [ ] Add the reusable View/XML list renderer (AC: 1–10, 12, 13)
  - [ ] Add `app/src/main/res/layout/view_card_list.xml` as the list block root; do not edit `fragment_detail_item.xml`.
  - [ ] Add a focused `ListBlockRenderer` (or the naming/package convention established by Story 3.1) that binds a normalized list model into a supplied block host.
  - [ ] Build marker and item text as separate columns/views so wrapped continuation lines align under item text.
  - [ ] Use decimal markers for ordered lists and `•` for unordered lists, preserving source order and one row per input item.
  - [ ] Clear the dynamic row host before every bind and reset empty/non-empty visibility explicitly.
  - [ ] Apply `text_body_sm`, `leading_body_sm`, `muted`, and shared spacing resources from Epic 1.
- [ ] Integrate with structured-body dispatch without duplicating orchestration (AC: 10, 11, 13)
  - [ ] Register/invoke the renderer through the Story 3.1 body dispatcher for top-level `type:"list"`.
  - [ ] Expose the same renderer contract for Story 3.7 to call inside a sections block host.
  - [ ] Preserve the dispatch-level try/catch/raw fallback; do not swallow failures in a way that leaves a partially rendered body.
  - [ ] Mount only under the existing `card_body` seam from Story 2.1 and preserve card click, attachments, actions, selection, tags, timestamp, and header behavior.
- [ ] Add parity, boundary, recycling, and accessibility tests (AC: 1–13)
  - [ ] Test ordered and unordered marker sequences, including 0, 1, 9, 10, and more than 10 items.
  - [ ] Test absent/false/null/non-Boolean/true `ordered` values.
  - [ ] Test string, number, negative/decimal number, Boolean, null, object, array, empty string, Unicode, and multiline item coercion.
  - [ ] Test missing/null/non-array/empty `items` and verify no stale rows survive rebinding.
  - [ ] Test long and multiline items for full content and continuation-line alignment; assert no `maxLines` or ellipsize.
  - [ ] Test light/night token resolution and a static/resource gate for no raw colors/ad-hoc dimensions in the new list UI.
  - [ ] Test top-level dispatch and the reusable block-host entry point intended for sections.
  - [ ] Test that a forced renderer failure reaches the Story 3.1 raw-body fallback without crashing.
  - [ ] Run focused tests plus Play and F-Droid debug resource processing/assembly.

## Dev Notes

### Dependency and Sequencing Guardrail

- Story 3.4 depends on the contracts from Story 3.0 (golden corpus) and Story 3.1 (card detection, parsed block model, dispatch, and raw fallback). No local story files for them were available during this analysis, even though sprint tracking may advance independently. Implement 3.4 only after their actual merged names and APIs are known; extend them rather than creating a parallel parser/dispatcher.
- Story 3.2 (meter) and Story 3.3 (kv) are not functional prerequisites for list rendering, but they may establish the Epic 3 package, renderer interface, block-host pattern, and test conventions. Reuse those conventions if they land first.
- Story 3.7 must compose this exact renderer for list blocks inside `sections`; design the renderer around a supplied parent/block host rather than coupling it only to a complete notification card.

### Canonical List Contract

The authoritative payload shape is:

```json
{
  "type": "list",
  "ordered": false,
  "items": ["배포 시작", "이미지 빌드", "테스트 통과"]
}
```

Required semantics:

- `ordered:true` selects one-based decimal markers. Every other shape defaults to bullets.
- Preserve array order and cardinality.
- Render every item and every character. NFR5 prohibits truncation, preview mode, item caps, and "show more".
- "Coerced to strings" needs deterministic Android behavior. Use raw string content for JSON strings; use compact JSON serialization for numbers, booleans, null, objects, and arrays. Do not call `JsonElement.toString()` for strings because that includes JSON quotes.
- Missing or malformed `items` is an empty list at the renderer boundary. The outer Story 3.1 boundary still owns whole-message fallback for parsing/rendering exceptions.

### Rendering Design

- Use a lightweight vertical `ViewGroup` in `view_card_list.xml` and create one horizontal row per item. A row should have a fixed/wrap-content marker view and a `0dp`/weighted item text view so wrapping occurs only in the text column.
- Avoid one concatenated `TextView` containing the whole list: it makes wrapped indentation, per-entry accessibility, and deterministic recycling tests harder.
- Markers are literal presentation text (`"$index."` or `•`), not locale-formatted numerals. The web contract specifies decimal `1.` markers and parity is locale-independent.
- `body_sm` means `@dimen/text_body_sm` with `@dimen/leading_body_sm`; both marker and item use `@color/muted`. Use existing `spacing_1..spacing_3` resources for marker gap and row separation according to the established Epic 3 renderer pattern.
- Keep item text selectable/link-free unless the structured-message contract is explicitly changed. A `list` block is plain string content, not Markdown; do not pass items through Markwon, Linkify, or HTML parsing.

### Architecture Compliance

- Continue with the decided minimal-change stack: Kotlin, Views/XML, AppCompat/Material, ConstraintLayout or standard ViewGroups, and RecyclerView. Do not add Compose or a third-party list renderer.
- Structured parsing should use the JSON mechanism selected by Story 3.1. The project currently pins Gson 2.13.2; do not add kotlinx-serialization/Moshi merely for this renderer.
- The stable card shell/body seam is owned by Story 2.1. `fragment_detail_item.xml` must not be edited by this story; inflate the dedicated list layout into `@id/card_body`.
- The body dispatcher should remain adapter-agnostic and reusable by the current `DetailAdapter` and future Epic 4 feed.
- Preserve min SDK 26, compile/target SDK 36, Java/Kotlin 17, and both `play`/`fdroid` flavors.

### Files to Add

- `app/src/main/res/layout/view_card_list.xml`
- A focused renderer/model helper under the Epic 3 package established by Story 3.1, likely one of:
  - `app/src/main/java/io/heckel/ntfy/ui/message/ListBlockRenderer.kt`
  - `app/src/main/java/io/heckel/ntfy/ui/ListBlockRenderer.kt`
- Focused JVM/Robolectric or instrumentation tests in the test source set established by preceding Epic 3 stories.

### Files to Update

Update only the post-Story-3.1 artifacts that own these responsibilities:

- Structured card model/parser file
  - Current expected role: convert validated JSON to typed/normalized block data.
  - Change: expose list `ordered` and `items` without reparsing in the renderer.
  - Preserve: dual gate, known-type validation, source order, and safe failure.
- Structured body dispatcher/renderer registry
  - Current expected role: choose kv/list/chart/sections or fallback rendering.
  - Change: delegate list blocks to the reusable list renderer.
  - Preserve: dispatch order, raw fallback, body-host reset, and sibling renderer boundaries.
- Test fixture/corpus files from Story 3.0, if their schema includes renderer cases
  - Add list normalization/rendering vectors without duplicating parity values in production tests.

Do not update `fragment_detail_item.xml`, `DetailAdapter.kt`, `MarkwonFactory.kt`, Gradle dependencies, or unrelated card/meta/header resources unless a prerequisite story deliberately placed the dispatcher there; if so, make the smallest integration-only edit.

### Existing Code and Behaviors to Preserve

- The checked-in branch still has the legacy monolithic `DetailAdapter.DetailViewHolder` and `fragment_detail_item.xml`; planned Story 2.1 moves body rendering into `MessageCardBinder` and creates `@id/card_body`. Do not implement against today's message `TextView` as a permanent shortcut.
- Existing plain/Markdown text, link behavior, attachments, notification icons, action buttons, selection, click/long-click, tags, and timestamp must remain intact outside a structured list body.
- `MarkwonFactory` currently customizes Markdown `ListItem` spans. That is for Markdown content only and is not the structured `type:"list"` renderer. Reusing it would blur parser contracts and make non-string JSON coercion/future sections composition harder.
- Dynamic children are a RecyclerView recycling risk. Always clear the row host and all view state before rendering the next model.

### Testing Requirements

- Prefer pure tests for JSON-value coercion and marker generation, then view tests for layout/recycling/accessibility.
- Include a 12+ item ordered list to catch single-digit marker-width assumptions and prove no cap.
- Include a very long unbroken string, a wrapping sentence, embedded newline text, Korean text, emoji, and bidirectional text. The requirement is preservation and no crash; do not invent special bidi transformation.
- Assert marker/item reading order and avoid making both row and child views independently announce duplicate content. Either expose one composed row description or allow marker then item in natural child order, according to the project's accessibility test pattern.
- Test reuse from a long ordered list to an empty unordered list, then to a one-item list. This catches stale rows, stale marker mode, and stale visibility.
- A renderer-level empty model may render nothing; a dispatcher-level exception must cause the complete raw-message fallback. Test both layers separately.

### Previous Story Intelligence

- No Story 3.3 artifact exists yet, so there are no implementation/review learnings from the immediately preceding story to incorporate.
- Story 2.1 establishes the permanent rules that Epic 3 owns dedicated `view_card_<type>.xml` layouts, mounts them inside `card_body`, and never reopens the shared shell.
- Story 2.4 reinforces full dynamic-child reset on every bind and keeping card-body behavior independent from interactive card/meta controls.

### Git Intelligence

- The latest relevant commits (`5e3972d6`, `a4d9b073`) add the SPEC, epics, and UI-parity companion documents only; they do not establish an implemented structured-renderer pattern.
- Current history therefore supports a narrow extension after prerequisite stories land, not guessing their APIs now or adding a second body architecture.
- No dependency update is indicated by recent history.

### Latest Technical Information

- External web research is unnecessary for this story. The checked-in June 2026 parity contract and pinned Android build are authoritative, and the story adds no external API or library.
- Relevant pinned stack includes AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0, Material 1.13.0, Gson 2.13.2, and Markwon 4.6.2. Reuse the existing stack; Markwon is specifically not the structured-list implementation.

### Project Structure Notes

- Keep structured message code in the focused UI/message package established by Story 3.1.
- Keep the block layout at `app/src/main/res/layout/view_card_list.xml`.
- Keep canonical typography/color/spacing values in resources established by Epic 1.
- There is no project-level `project-context.md`; the SPEC kernel, brownfield notes, epics, and `docs/ui-parity` companions are the project context.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.4, NFR5 ownership and ordering]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-5, CAP-6, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — View/XML stack and current card row]
- [Source: `docs/ui-parity/message-format.md` — §1 structured-card gate, §2.2 list, §2.4 sections, §7 full-content rule]
- [Source: `docs/ui-parity/components.md` — §1 Body slot and Removed compact/show-more behavior]
- [Source: `docs/ui-parity/design-tokens.md` — Typography Tokens and Spacing Scale]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md` — shell/body ownership and renderer file pattern]
- [Source: `_bmad-output/implementation-artifacts/2-4-categorized-tag-row-timestamp.md` — dynamic-view recycling guardrail]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — current body binding and RecyclerView reuse]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml` — current pre-body-slot row]
- [Source: `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt` — Markdown-only list handling]
- [Source: `app/build.gradle` — pinned stack and dependencies]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Customization resolver fallback used because the available `python3` lacks Python 3.11 `tomllib`; base/team/user customization was resolved manually.
- No team or user override file was present; base workflow persistent facts found no `project-context.md`.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story 3.4 is ready for development after Story 3.1 establishes the parser/dispatcher contract.
- The story pins deterministic JSON coercion, full-content rendering, wrapped-marker alignment, body-host ownership, recycling reset, and reusable sections composition.

### File List

- `_bmad-output/implementation-artifacts/3-4-list-block-renderer.md`
