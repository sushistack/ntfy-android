# Story 3.6a: Markdown Renderer (Styles)

Status: ready-for-dev

## Story

As a user,
I want formatted Markdown text,
so that rich message bodies render with correct, readable card typography.

## Acceptance Criteria

1. **Given** a Markdown string rendered in the card body  
   **When** it contains paragraphs, `#`/`##`/`###` headings, strong emphasis, emphasis, inline code, fenced or indented code blocks, blockquotes, unordered lists, or ordered lists  
   **Then** every supported node renders through the shared card Markdown renderer with the intent defined by `message-format.md §5`  
   **And** paragraphs/lists use `body_sm` and muted text, strong text is semibold and primary, emphasis is italic and muted, and headings remain card-scale (`h1` = subtitle semibold, `h2` = semibold, `h3` = medium) rather than display-sized.

2. **Given** inline code, a code block, or a blockquote  
   **When** it renders  
   **Then** code uses the project mono family with `surface_2` background and token-backed rounding  
   **And** code blocks add token-backed padding and preserve long lines without clipping content, using horizontal scrolling or an equivalent full-content presentation  
   **And** blockquotes use a `border`-colored left rule, token-backed inset/padding, and muted text.

3. **Given** unordered and ordered Markdown lists  
   **When** they render  
   **Then** unordered items use disc/bullet markers and ordered items preserve decimal numbering in source order  
   **And** nested or consecutive list items remain readable and do not collapse into the existing single bullet-only behavior  
   **And** all items render without truncation.

4. **Given** any Markdown card body, including a long multi-paragraph or long-list body  
   **When** it renders  
   **Then** the complete body is present with no `maxLines`, `ellipsize`, clamp, compact mode, “show more,” or preview affordance  
   **And** a focused long-body test verifies the final rendered output contains the first, middle, and last content markers.

5. **Given** malformed, adversarial, or unexpectedly shaped Markdown input that causes parsing, span construction, or view application to throw  
   **When** the body renderer handles it  
   **Then** the failure is caught at the Markdown-renderer boundary and the original raw message string is shown as token-styled body text  
   **And** the card, sibling content, and RecyclerView do not crash.

6. **Given** the Markdown renderer is used for the untagged paragraph fallback and, later, a `sections` Markdown block  
   **When** either caller renders the same Markdown text  
   **Then** both use one reusable renderer/style configuration rather than duplicating node mappings  
   **And** the renderer accepts a `Context`/target view or similarly host-neutral inputs without depending on `DetailActivity`, a concrete adapter, repository, lifecycle scope, navigation, or Compose.

7. **Given** a recycled card body previously displayed Markdown  
   **When** it is rebound to plain/raw text, another Markdown body, or a future structured block  
   **Then** old spans, movement methods, text, temporary child views, scroll state, and renderer-specific state are reset before the new body is applied  
   **And** no prior formatting or content leaks into the next notification.

8. **Given** Story 3.6a scope  
   **When** it is complete  
   **Then** it does not implement or weaken Story 3.6b link/image scheme security  
   **And** existing link/image behavior is preserved behind a replaceable policy seam, with only `http`/`https`/`mailto` live-link enforcement and unsafe-image dropping considered complete after 3.6b  
   **And** no dependency version is upgraded and no WebView or Compose implementation is introduced.

## Tasks / Subtasks

- [ ] Define one reusable card Markdown renderer contract (AC: 1, 5–8)
  - [ ] Extract or evolve `MarkwonFactory.createForMessage(...)` into a card-focused renderer/configuration reusable by paragraph fallback and Story 3.7 Markdown sections.
  - [ ] Keep construction context-only and host-neutral; do not require an `Activity`.
  - [ ] Expose an explicit render/reset/fallback boundary so callers do not duplicate `try/catch`, raw fallback, or cleanup.
  - [ ] Preserve a narrow link/image policy seam for Story 3.6b; do not claim security completion in this story.
- [ ] Implement token-backed Markdown typography (AC: 1)
  - [ ] Map paragraph, heading levels 1–3, strong, emphasis, inline code, code block, blockquote, unordered list, and ordered list nodes to the canonical token intent.
  - [ ] Replace current relative display-like heading multipliers with card-scale type dimensions/line heights.
  - [ ] Use project font resources/fallback chains for sans and mono; do not bundle a second typography system.
  - [ ] Ensure paragraph/list base color and strong/emphasis overrides resolve correctly in light and night themes.
- [ ] Implement block treatments and complete-content behavior (AC: 2–4)
  - [ ] Add custom spans/drawables or a focused body layout only where Markwon's theme API cannot express `surface_2`, rounded code backgrounds, block padding, or the blockquote rule.
  - [ ] Choose and test a full-content long-code presentation. If a nested horizontal scroll container is used, keep vertical card/feed scrolling functional and reset its scroll position on bind.
  - [ ] Preserve ordered list numbering from `CoreProps.LIST_ITEM_TYPE` / `ORDERED_LIST_ITEM_NUMBER`; do not replace all `ListItem` nodes with `BulletSpan`.
  - [ ] Add no `maxLines`, `ellipsize`, clipping, or show-more state to the Markdown target.
- [ ] Integrate with the Epic 3 body slot without reopening the shell (AC: 5–8)
  - [ ] Add a dedicated body layout/view such as `view_card_markdown.xml` if needed and inflate it into `@id/card_body`.
  - [ ] Do not edit `fragment_detail_item.xml`; Story 2.1 owns the card shell and body-slot contract.
  - [ ] Route the paragraph/raw branch from Story 3.1 through this renderer, while keeping structured dispatch and heuristic-kv ownership in their respective stories.
  - [ ] Provide the same renderer entry point for Story 3.7's `{type:"markdown", text:...}` blocks.
  - [ ] Clear the body host and renderer state before each bind and before applying raw fallback.
- [ ] Add focused automated coverage (AC: 1–8)
  - [ ] Add a representative Markdown fixture containing every style node and assert the expected span/typeface/color/background/list semantics.
  - [ ] Assert h1/h2/h3 remain card-scale and use token resources in both day and night configurations.
  - [ ] Assert unordered bullets and ordered `1.`, `2.`, `3.` numbering, including consecutive/nested cases supported by Markwon.
  - [ ] Assert inline code, fenced code, blockquote rule/padding, and long-line full-content behavior.
  - [ ] Assert a long body/list has no truncation and includes first/middle/last markers.
  - [ ] Inject a throwing parser/renderer or equivalent test seam and assert raw-text fallback without a crash.
  - [ ] Assert Markdown → raw/plain → Markdown and Markdown → structured-view recycling sequences leave no stale spans, children, or scroll state.
  - [ ] Add a scope guard proving no Compose/WebView/new Markdown dependency and no `fragment_detail_item.xml` modification.

## Dev Notes

### Dependency and Ownership Gates

- This story consumes the body-dispatch/fallback seam from Story 3.1 and the `@id/card_body` shell seam from Story 2.1. If those production changes are not merged yet, implement against their documented contracts instead of recreating card detection or shell ownership.
- Story 3.6a owns Markdown presentation: typography, list semantics, code/blockquote treatment, complete-content behavior, fault fallback, reuse, and recycling safety.
- Story 3.6b owns URL policy and image security: only `http`/`https`/`mailto` links become live and unsafe-scheme images are dropped. Keep those concerns separable so 3.6b can harden them without rewriting styles.
- Story 3.7 consumes the renderer for Markdown blocks inside ordered sections. Do not build sections dispatch or nested-block behavior here.
- Story 3.8 consumes the same paragraph renderer when heuristic-kv detection does not match. Do not implement heuristic shape detection here.
- Story 3.0's security fixture corpus is referenced by 3.6b, not re-encoded as a claim of completed security in 3.6a.
- No earlier Epic 3 story artifact exists in `implementation-artifacts`; there is therefore no implemented Story 3.5 learning to inherit. Use the Epic 2 body-slot contract and current code as the concrete baseline.

### Current Implementation State

- `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`
  - `createForMessage(context)` already builds Markwon 4.6.2 with `CorePlugin`, soft-break newline behavior, `BetterLinkMovementMethod`, images, linkify, strikethrough, a link resolver, and custom heading/emphasis/list spans.
  - Current headings use relative multipliers `1.7/1.5/1.2/...`; these are not the token-defined card heading contract and can make in-card headings too display-like.
  - Current `ListItem` mapping always returns `BulletSpan`, erasing ordered-list numbering. Replace this with list-type-aware behavior or retain Markwon's default ordered-list span and customize without destroying semantics.
  - Theme configuration currently controls only link color/underline. Code background/text, blockquote treatment, paragraph/list color, font family, dimensions, and line-height intent remain unimplemented.
  - `ImagesPlugin` and `LinkifyPlugin(Linkify.WEB_URLS)` are active. Preserve compatibility but isolate them for Story 3.6b; Android `Linkify.WEB_URLS` alone is not the final protocol whitelist.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current binding calls `markwon.setMarkdown(messageView, ...)` only when `Notification.isMarkdown()` is true; otherwise it uses `Linkify.WEB_URLS`.
  - It applies `BetterLinkMovementMethod` and card click/long-click listeners directly to the message view.
  - Epic 3's paragraph fallback is broader than the legacy content-type flag: untagged paragraph text and `sections` Markdown blocks must be able to use the same renderer regardless of the old `contentType` branch.
  - Do not move repository, activity, coroutine, or navigation behavior into the Markdown renderer.
- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current pre-Epic-2 layout contains `detail_item_message_text` inside a monolithic card.
  - Expected prerequisite state is a reusable shell with `@id/card_body`. This story must mount its own body content there and must not modify the shell XML.
  - The current message `TextView` has no explicit max-lines clamp, which must remain true.
- `app/build.gradle`
  - Markwon core/image/image-picasso/linkify/tables/strikethrough are pinned to `4.6.2`.
  - Existing View/XML stack: min SDK 26, target/compile SDK 36, Java/Kotlin 17, AppCompat 1.7.1, Material 1.13.0. Preserve both `play` and `fdroid` flavors.

### Required Renderer Boundary

A suitable contract is a small reusable component, for example:

```kotlin
interface CardMarkdownRenderer {
    fun render(target: TextView, markdown: String)
    fun renderRawFallback(target: TextView, raw: String)
    fun reset(target: TextView)
}
```

The exact API may follow the final Story 3.1 body-renderer abstraction, but preserve these properties:

- one configuration/node-style mapping for paragraph fallback and section Markdown;
- context/resource access without `Activity`;
- rendering exceptions caught at this boundary;
- fallback always receives the original, unmodified input;
- reset is safe and idempotent for RecyclerView reuse;
- link/image policy can be replaced or tightened by Story 3.6b without duplicating typography.

If code blocks require a compound view for horizontal scrolling, the renderer may target a dedicated `ViewGroup` instead of only a `TextView`; keep the public responsibility the same.

### Token and Style Mapping

Use the token resources introduced by Epic 1. Names below are the canonical Android keys from `design-tokens.md`; adapt only to the final resource type chosen by Story 1.2:

- Paragraph and lists: `text_body_sm` (14sp intent), `leading_body_sm` (20sp/dp line-height intent), `@color/muted`, project `font_sans`.
- `h1`: `text_subtitle` + `leading_subtitle`, semibold, primary `@color/text`.
- `h2`: card-safe semibold using the nearest canonical body/subtitle token; do not invent an oversized intermediate display token.
- `h3`: card-safe medium using canonical body sizing.
- Strong: semibold and primary text. Do not assume `Typeface.BOLD` exactly matches the final font's semibold weight if the resource family exposes a semibold face.
- Emphasis: italic and muted.
- Inline/block code: project `font_mono`, `@color/surface_2`, token-backed radius (`radius_sm` is the documented code intent unless a prerequisite story provides a more specific shared code radius).
- Blockquote: `@color/border` left rule plus canonical spacing dimensions.
- Avoid literal colors and ad-hoc pixel calculations. Existing `8 * density` bullet-gap code should move to a named spacing dimension or a Markwon theme value backed by resources.

### Full-Content and Scrolling Guardrails

- NFR5 is explicit: Markdown is the complete card body. Do not optimize by clamping lines, truncating `Spanned` output, limiting list children, or adding an expansion affordance.
- Markwon renders native spans into `TextView`; this is preferred over WebView for selectable/accessible native text and dependency reuse.
- The specification asks code blocks to support horizontal scrolling. A normal `TextView` will wrap long code by default, while a nested `HorizontalScrollView` can compete with RecyclerView gestures. Choose a deliberate implementation and test:
  - long code remains fully available;
  - vertical feed scroll still works;
  - horizontal state resets when recycled;
  - accessibility can traverse/read the code.
- Do not put the entire Markdown body in a horizontal scroller merely to satisfy code blocks.

### Fault Tolerance

- Wrap parsing, span creation, and target application—not only JSON dispatch—in the renderer's error boundary.
- On failure:
  - clear partial/stale rendered content;
  - show the exact raw Markdown string;
  - apply normal token-styled body text;
  - keep links/images inert unless the completed Story 3.6b policy explicitly permits them;
  - do not suppress sibling section blocks when Story 3.7 calls this renderer.
- Avoid broad exception swallowing without a test seam. Log at the project's normal diagnostic level without including secrets or remote content beyond what is already safe to log.

### RecyclerView Preservation Requirements

- Reset before bind, not only after an exception.
- At minimum reset text/spans, movement method, `autoLinkMask`, listeners owned by the renderer, selection, nested code-scroll position, child views if compound rendering is used, and any transient accessibility state.
- Preserve whole-card click/mark-read behavior and clickable-span routing. The renderer must not manufacture a second card action or navigate to a detail view.
- If link clicks consume touch events, preserve the existing non-link message click forwarding contract until Story 2.5/final binder behavior defines the replacement.

### Architecture Compliance

- Remain in Views/XML and native Android spans; no Jetpack Compose and no WebView.
- Reuse Markwon 4.6.2; do not add another Markdown parser/renderer or upgrade dependencies inside this story.
- Keep shared renderer code under `io.heckel.ntfy.ui`/a focused `ui.message` package or evolve `io.heckel.ntfy.util.MarkwonFactory` consistently with the existing project.
- Body-specific layouts belong in `app/src/main/res/layout/view_card_<type>.xml`; `fragment_detail_item.xml` remains untouched.
- Use token resources in `values/` and `values-night/`; do not branch manually on dark mode.
- Preserve existing soft-break behavior and strikethrough unless a canonical contract explicitly removes them. They are compatibility features outside this story's required style-node list.

### Testing Requirements

- Prefer JVM/Robolectric tests for span/configuration semantics and resource resolution; use instrumentation only for behavior Robolectric cannot reliably validate (gesture interaction, actual horizontal scrolling, or font rendering).
- Inspect the produced `Spanned` content instead of relying only on screenshots:
  - heading level → expected relative/absolute size and weight;
  - strong/emphasis → expected typeface and color spans;
  - inline/block code → mono/background treatment;
  - blockquote → rule/margin treatment;
  - ordered vs unordered list spans/labels.
- Add day/night resource tests for text, muted, surface_2, and border colors.
- Add a deliberately throwing fake renderer/plugin to prove exact raw fallback and cleanup.
- Run focused tests plus Play and F-Droid debug resource processing/assembly.
- Manual smoke check: render the canonical style fixture in light/dark, verify headings stay within card scale, ordered numbering is correct, long code remains reachable, the final long-list item is visible, and scrolling/recycling does not leak state.

### Previous Story and Git Intelligence

- Story 2.1 establishes the non-negotiable adapter-agnostic binder and `card_body` ownership boundary. Reuse it; do not reopen `fragment_detail_item.xml`.
- Story 2.1 also requires current Markdown/plain links, attachments, actions, selection, and click behavior to survive extraction. Style work must preserve those integrations.
- Recent commits only add the SPEC, epics, companions, and sprint artifacts; no Epic 3 renderer implementation has landed.
- Relevant older history includes soft-break alignment with web, image/GIF support, link handling, and crash prevention for oversized image content. This favors extending the existing Markwon configuration with tested reset/fallback behavior rather than replacing it.
- The working tree contains user-owned uncommitted story artifacts. Do not alter or reformat unrelated files.

### Latest Technical Information

- The project's pinned Markwon `4.6.2` is also the latest upstream release visible as of 2026-06-21. No upgrade or migration is required.
- Markwon's official v4 documentation confirms `CorePlugin` supplies visitors/factories for headings, emphasis, blockquotes, code, lists, paragraphs, links, and images, and that custom `MarkwonSpansFactory`/`MarkwonTheme` plugins are the supported extension points.
- Prefer `appendFactory`/`prependFactory` where multiple spans must compose with defaults; the official span-factory documentation marks older generic factory-addition ordering APIs as deprecated.
- Markwon renders Android-native `Spannable` content without a WebView, matching this project's minimal-change architecture.

### Project Structure Notes

- Expected additions or focused updates:
  - `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`, or a dedicated `CardMarkdownRenderer.kt` plus a slim factory wrapper.
  - `app/src/main/res/layout/view_card_markdown.xml` only if the final body renderer needs a dedicated target/compound code-block treatment.
  - token-backed style/span helper classes under the same focused UI/util package.
  - focused tests under `app/src/test/...` and, only where necessary, `app/src/androidTest/...`.
- Expected integration update after Story 3.1/2.1 prerequisites:
  - the reusable body renderer/binder that owns `@id/card_body`.
- Explicitly do not update:
  - `app/src/main/res/layout/fragment_detail_item.xml`;
  - database, DAO, receive path, notification service, manifest, navigation, feed pagination, chart/meter/kv/list renderer code;
  - dependency versions.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.6a, NFR5 ownership, dependency flow]
- [Source: `docs/ui-parity/message-format.md` §5–§7 — Markdown styles, fallback path, full-content contract]
- [Source: `docs/ui-parity/components.md` §1 — card body slot and full-content requirement]
- [Source: `docs/ui-parity/design-tokens.md` — color, typography, line-height, radius, and spacing tokens]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-5/CAP-6, constraints, non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — Views/XML minimal-change architecture]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/build.gradle`]
- [Source: Markwon official repository — https://github.com/noties/Markwon]
- [Source: Markwon v4 CorePlugin documentation — https://noties.io/Markwon/docs/v4/core/core-plugin.html]
- [Source: Markwon v4 Spans Factory documentation — https://noties.io/Markwon/docs/v4/core/spans-factory.html]
- [Source: Markwon v4 Theme documentation — https://noties.io/Markwon/docs/v4/core/theme.html]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- The customization resolver required Python 3.11; customization was manually resolved from the base TOML with no team/user overrides.
- No project-level `project-context.md`, PRD, architecture, or UX file was present. The preservation-validated SPEC kernel, brownfield companion, epics, and UI-parity companions were used as the canonical inputs.
- Epic 3 has no earlier generated story file, so prior-story implementation intelligence was unavailable; current production Markdown code and Story 2.1 supplied the concrete baseline.

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Story scope separates Markdown styling/fault tolerance from Story 3.6b link/image security.
- Existing Markwon 4.6.2 is reused; ordered-list semantics, card-scale headings, token-backed block styles, full-content rendering, and recycling safety are explicit implementation gates.

### File List

- `_bmad-output/implementation-artifacts/3-6a-markdown-renderer-styles.md`
