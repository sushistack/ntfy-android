# Story 3.7: `sections` Block Renderer (Mixed, Ordered)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want mixed cards combining text, tables, lists, and charts,
so that full incident and CI reports render as one structured card.

## Acceptance Criteria

1. **Given** a valid top-level `sections` spec, **when** its `blocks` array contains `markdown`, `kv`, `list`, and `chart` blocks, **then** every supported block is rendered in source order in one vertical container, using the renderer already delivered by Stories 3.3–3.6b for that block type.
2. **Given** two adjacent rendered blocks, **when** the sections view is laid out, **then** the gap between them is exactly `@dimen/spacing_3` (12dp), with no leading gap before the first rendered block and no trailing gap after the last rendered block.
3. **Given** a block with `type: "markdown"` inside a top-level `sections` spec, **when** it renders, **then** its `text` is passed to the full token-styled and security-hardened Markdown renderer from Stories 3.6a/3.6b; `markdown` remains invalid as a top-level structured-card type.
4. **Given** `{ "type":"sections", "blocks":[supported, {"type":"sections","blocks":[...]}, supported] }`, **when** it renders, **then** the nested `sections` block renders nothing, no recursive render call is made, and both supported outer siblings still render in order.
5. **Given** an unknown, missing, malformed, or non-object child block entry among valid siblings, **when** it is dispatched, **then** that entry renders nothing and does not prevent later siblings from rendering.
6. **Given** a missing, null, non-array, or empty top-level `blocks` value, **when** the sections renderer runs, **then** it produces an empty body result without crashing; the Story 3.1 outer rendering boundary remains responsible for raw-message fallback if the overall render pipeline throws unexpectedly.
7. **Given** the same RecyclerView card view is rebound from one sections payload to another or to a different body type, **when** binding occurs, **then** all previously added section child views, margins, listeners, spans, image requests, and transient state are cleared before the new body is mounted, so no stale block leaks into the recycled card.
8. **Given** a long sections payload, **when** it contains long Markdown, every kv row, every list item, and a full chart, **then** all content is present with no `maxLines`, `ellipsize`, clipping, compact mode, “show more,” or body-level `+N more` affordance.
9. **Given** the sections renderer is implemented, **when** its production dependencies are inspected, **then** it uses the existing View/XML stack, Gson and renderer registry/dispatcher; it introduces neither Jetpack Compose nor a new Markdown, chart, JSON, or layout library.
10. **Given** the card shell from Story 2.1, **when** a sections body is mounted, **then** only the contents of `@id/card_body` are replaced; `fragment_detail_item.xml`, the card header, priority accent, meta row, attachments/actions, card click behavior, and adapter/activity boundaries are not reimplemented or coupled to the sections renderer.

## Tasks / Subtasks

- [ ] Define the non-recursive sections dispatch contract (AC: 1, 3–6, 9)
  - [ ] Extend the Epic 3 block model only as needed to represent a top-level `Sections` block and its raw ordered child entries.
  - [ ] Reuse the existing child-block parser/dispatcher from Stories 3.1 and 3.3–3.6; do not create parallel kv/list/chart/Markdown parsing rules.
  - [ ] Add an explicit child allowlist of `markdown`, `kv`, `list`, and `chart`.
  - [ ] Reject `sections` in child dispatch before calling any renderer so recursion is structurally impossible.
  - [ ] Treat unknown, missing-type, malformed, and non-object child entries as skipped entries, not fatal errors.

- [ ] Implement the View-system sections renderer (AC: 1–3, 7–10)
  - [ ] Add `view_card_sections.xml` as a body-only layout with a vertical child host; inflate it into `@id/card_body`.
  - [ ] Render supported children sequentially and preserve their exact JSON array order.
  - [ ] Apply `@dimen/spacing_3` only between successfully rendered, visible child blocks.
  - [ ] Delegate each child to the already-established renderer instance/registry so child behavior, styling, security, and fault tolerance remain identical to standalone rendering.
  - [ ] Ensure the renderer receives only `Context`/`LayoutInflater`/`ViewGroup`-level dependencies and has no `Activity`, adapter, repository, or navigation dependency.

- [ ] Make recycling and partial failure deterministic (AC: 4–7)
  - [ ] Clear the sections host before every bind and before returning an empty result.
  - [ ] Ensure skipped children do not leave spacer views or margins.
  - [ ] Isolate child dispatch so one unsupported/malformed child cannot abort supported siblings.
  - [ ] Preserve Story 3.1's outer try/catch/raw-fallback contract for unexpected renderer exceptions; do not swallow a systemic renderer failure and leave a half-stale body.

- [ ] Enforce full-content behavior (AC: 8)
  - [ ] Do not set `maxLines`, `ellipsize`, fixed body height, clipping, or collapsing controls on the sections host or delegated block roots.
  - [ ] Verify chart height remains owned by Story 3.5 and that Markdown/kv/list content wraps to its full measured height.

- [ ] Add focused tests (AC: 1–10)
  - [ ] Unit-test child dispatch order with mixed `markdown → kv → list → chart` input.
  - [ ] Test nested `sections`, unknown type, missing type, primitive/null entries, and malformed child shapes; assert supported siblings before and after them remain rendered.
  - [ ] Test missing/null/non-array/empty `blocks` without a crash.
  - [ ] Layout-test `spacing_3` between rendered children and no gap for skipped entries or at container edges.
  - [ ] Recycling-test a many-block payload followed by a shorter payload, empty sections, and a non-sections body; assert no stale child views/state.
  - [ ] Long-body test every row/item/block remains present and no descendant uses truncation or a compact/show-more affordance.
  - [ ] Add/extend an architecture guard asserting no Compose or Activity/adapter coupling and no edit dependency on `fragment_detail_item.xml`.

## Dev Notes

### Developer Context

Story 3.7 is the Epic 3 composition layer. It does not own parsing the top-level card gate, Markdown behavior, kv/list/chart rendering, meters, or chart drawing. Its job is deliberately narrow: iterate the ordered child array, admit four child types, delegate to the existing renderers, and compose the resulting views with token spacing.

The canonical payload shape is:

```json
{
  "type": "sections",
  "blocks": [
    { "type": "markdown", "text": "## Build failed" },
    { "type": "kv", "rows": [{ "key": "Stage", "value": "test", "status": "error" }] },
    { "type": "list", "ordered": true, "items": ["Compile", "Test"] },
    { "type": "chart", "kind": "bar", "data": [{ "label": "test", "value": 252 }] }
  ]
}
```

`markdown` is a child-only discriminator. Do not add it to the Story 3.1 top-level known-type gate, which must remain `{kv,list,chart,sections}`.

### Dependency and Sequencing Guardrails

- Required implementation prerequisites: Stories 2.1, 3.1, 3.3, 3.4, 3.5, 3.6a, and 3.6b. Story 3.2 is consumed transitively by kv rendering.
- At story-creation time, the working tree still has the legacy `DetailAdapter`/`fragment_detail_item.xml`; `MessageCardBinder`, `card_body`, and all Epic 3 renderer files are planned but not implemented. Implement/merge prerequisites first, then adapt this story to their final names and APIs.
- No Story 3.6 implementation artifact exists yet, so there are no previous-story completion notes or review corrections to inherit. The canonical protocol and established Epic 2 body-slot contract are the source of truth.
- Keep a single renderer graph. A useful final shape is a `MessageBodyRenderer`/block registry that can render a parsed block into a supplied `ViewGroup`; `SectionsBlockRenderer` receives that child dispatcher with sections recursion disabled. Adapt names to the interfaces established by earlier Epic 3 stories.

### Technical Requirements

- Existing stack: Kotlin/JVM 17, Android Views/XML, minSdk 26, compile/target SDK 36.
- Existing JSON dependency: Gson 2.13.2. Reuse it; do not add kotlinx-serialization/Moshi merely for sections.
- Existing Markdown dependency: Markwon 4.6.2. Reuse the hardened renderer produced by Stories 3.6a/3.6b rather than calling the legacy `MarkwonFactory` directly and bypassing its security policy.
- Child dispatch must be intentionally non-recursive. Do not solve this with a generic self-calling `render(block)` path that happens to stop later; exclude `sections` from the child registry/API.
- Preserve JSON array order. Do not group by type, sort, parallelize UI creation, or reuse a map iteration order.
- A skipped block contributes neither a view nor spacing. Compute inter-block spacing from successfully rendered children, not raw array indexes.
- Empty sections should leave `card_body` clean. Unexpected exceptions still flow to Story 3.1's raw-message fallback so the user sees useful content rather than a broken/partial card.
- Dynamic views must be reset on every bind. RecyclerView reuse is a primary regression risk.
- Use resource dimensions/colors/text appearances from Stories 1.1/1.2. The only sections-owned spacing is `@dimen/spacing_3`; do not hard-code `12dp` in Kotlin or duplicate a dimension.

### Architecture Compliance

- The project decision is minimal-change Views/XML. Do not introduce Compose.
- `fragment_detail_item.xml` is the reusable shell owned by Story 2.1. Story 3.7 must not edit it; mount `view_card_sections.xml` into its stable `@id/card_body`.
- `MessageCardBinder` remains adapter- and Activity-agnostic. The sections renderer is a body concern and must not access `DetailActivity`, `DetailAdapter`, `Repository`, coroutines, navigation, card actions, or shell controls.
- Reuse standalone renderers rather than inflating lookalike rows or duplicating their parsing/style logic. A sections kv/list/chart/Markdown block must behave exactly like the corresponding standalone renderer.
- Preserve current non-body capabilities supplied by the final card binder: attachments, icons, actions, selection, links, whole-card click/long-click handling, delete, unread behavior, priority/header/meta rendering, and recycling resets.

### Files to Add or Update

Expected final paths; adjust only if earlier Epic 3 stories establish a consistent focused package:

- NEW `app/src/main/java/io/heckel/ntfy/ui/message/SectionsBlockRenderer.kt`
  - Own ordered composition, child allowlisting, non-recursion, spacing, and reset behavior.
- NEW `app/src/main/res/layout/view_card_sections.xml`
  - Body-only vertical host; no card shell/header/meta content.
- UPDATE the Epic 3 parsed block model/parser file
  - Represent top-level sections and ordered raw/typed children without adding top-level Markdown.
- UPDATE the Epic 3 `MessageBodyRenderer`/renderer registry
  - Register top-level sections and expose a child dispatcher limited to markdown/kv/list/chart.
- UPDATE `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Only if the established body-renderer seam requires registration/injection; do not add sections-specific binding branches to the adapter.
- ADD/UPDATE tests beside the Epic 3 parser/renderer tests under `app/src/test/...` and, where Android layout inflation is required by the established test strategy, `app/src/androidTest/...`.

Do not update:

- `app/src/main/res/layout/fragment_detail_item.xml`
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` except an unavoidable generic renderer-registration seam established by prerequisites
- database, delivery, navigation, feed, theme-selection, Gradle dependency, or localization files

### Current UPDATE-File Baseline

Because prerequisite stories are not implemented in this checkout, the current files show the pre-target baseline:

- `DetailAdapter.kt` currently performs Markdown/plain binding directly in its nested holder and owns nearly all row state. Story 2.1 is expected to extract this to `MessageCardBinder`; do not implement sections in the legacy holder.
- `fragment_detail_item.xml` is currently a rounded `CardView` with a single `detail_item_message_text` and no `card_body`. Story 2.1 replaces this shell contract; Story 3.7 must consume, not reopen, that result.
- `MarkwonFactory.kt` currently uses Markwon with broad web linkification and `LinkResolverDef`. Stories 3.6a/3.6b must harden and token-style this path before sections delegates Markdown to it.
- `app/build.gradle` already supplies Gson and Markwon. No dependency change is needed for this story.

### Testing Requirements

- Prefer pure unit tests for JSON child classification/order and a focused View/layout test for inflation, margins, and recycling.
- Use stable fake/spy renderers for each child type so the sections tests prove dispatch order and exactly-once delegation without retesting every child renderer's internals.
- Include the canonical mixed CI example from `message-format.md` as an integration fixture.
- Required edge matrix:
  - all four supported child types in order;
  - repeated types;
  - first/middle/last skipped block;
  - nested sections with valid siblings;
  - unknown/missing/null/non-object entries;
  - missing/null/non-array/empty `blocks`;
  - long Markdown, large kv/list content, and chart together;
  - rebind long → short → empty → different body type.
- Assert child count/order using semantic renderer markers or stable view IDs, not screenshot-only comparison.
- Assert no `maxLines`/ellipsize/collapse control in the sections subtree and no spacer for skipped children.
- Run the existing unit/instrumentation suites for both product flavors as supported by the project, plus Android lint/static architecture guards.

### Library / Framework Requirements

No new library is justified. This story composes existing Views and delegates to existing renderers. The current checked-in versions relevant to the story are Gson 2.13.2, Markwon 4.6.2, AppCompat 1.7.1, ConstraintLayout 2.2.1, RecyclerView 1.4.0, and Material 1.13.0. Pin behavior to the repository, not to a speculative upgrade.

No external web research changes the implementation contract: the preservation-validated local SPEC and companions are explicitly canonical, and the story introduces no new API or dependency. If the running ntfy-web implementation becomes available and disagrees with these companions, the running web behavior wins and the fixture should be updated before implementation.

### Project Structure Notes

- Keep structured rendering in a focused `ui/message` package if Stories 3.0–3.6 establish it; otherwise remain consistently under `io.heckel.ntfy.ui`.
- Layout names follow the Epic 2 contract: `view_card_<type>.xml`, therefore `view_card_sections.xml`.
- Avoid a generic nested `RecyclerView`; sections are a small vertical composition within an existing RecyclerView row. A simple vertical `LinearLayout`/equivalent child host avoids nested scrolling and preserves full measured height.
- Avoid one XML include per potential child type in the sections layout. Inflate only the blocks present in the payload through the shared renderers.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.7, NFR5]
- [Source: `docs/ui-parity/message-format.md` — §1 structured-card gate, §2.4 sections, §5 Markdown, §7 full content]
- [Source: `docs/ui-parity/components.md` — §1 Body slot and Removed in this redesign]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-5, CAP-6, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Stack and current card-row baseline]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md` — stable `card_body`, renderer layout naming, adapter-agnostic binder]
- [Source: `app/build.gradle` — SDK/toolchain and dependency versions]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` — legacy body binding and recycling baseline]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml` — pre-Story-2.1 shell baseline]
- [Source: `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt` — current Markdown integration baseline]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story created against the planned post-Epic-2/Epic-3 architecture; prerequisite implementation files are not yet present in the working tree.

### File List

- `_bmad-output/implementation-artifacts/3-7-sections-block-renderer-mixed-ordered.md`

