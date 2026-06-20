# Story 3.6b: Markdown Link/Image Security

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want unsafe links and images neutralized,
so that a malicious payload cannot execute script or leak data through a notification card.

## Acceptance Criteria

1. **Given** markdown containing links, **when** it renders in a notification card, **then** only destinations whose parsed URI scheme is exactly `http`, `https`, or `mailto` (case-insensitive) are interactive.
2. **Given** a markdown link with any other scheme, including `javascript:` or `data:`, or with a missing/invalid scheme, **when** it renders, **then** its visible label remains as inert accent-colored text and has no clickable span or resolver invocation.
3. **Given** markdown containing an image, **when** its source has an `http` or `https` scheme, **then** the existing markdown image pipeline may render it; **when** its source has any other, missing, or invalid scheme, **then** no image span/request is created and the image is dropped.
4. The same link and image policy applies to every markdown entry point: normal markdown notification bodies and `markdown` blocks inside structured `sections`. Plain-text URL auto-linking must not bypass it.
5. Link taps preserve existing card interaction behavior: tapping a safe link opens only that link and does not also invoke the card mark-read/click callback; unsafe link text behaves as ordinary body text.
6. Safe/unsafe link and image cases come from the shared Story 3.0 golden corpus rather than a second hard-coded fixture set, and a security-focused test asserts rendered span/request behavior.
7. A malformed destination or markdown payload is caught and falls back to the raw message text without crashing the card or leaking recycled link/image state.

## Tasks / Subtasks

- [ ] Establish the shared destination policy (AC: 1–4)
  - [ ] Add one internal URI-classification helper used by both link and image rendering.
  - [ ] Parse schemes case-insensitively; allow links only for `http`, `https`, `mailto`, and images only for `http`, `https`.
  - [ ] Reject missing schemes, protocol-relative URLs, malformed destinations, `javascript:`, `data:`, `file:`, `content:`, `intent:`, and custom app schemes.
  - [ ] Keep policy code independent of an `Activity`; it must be reusable by the shared card-body renderer.
- [ ] Harden Markwon link rendering (AC: 1, 2, 4, 5)
  - [ ] Replace unrestricted `LinkResolverDef` behavior for card markdown with a resolver/span path that cannot launch a rejected destination.
  - [ ] Render rejected Markdown link labels with the accent link appearance but no clickable span.
  - [ ] Ensure `LinkifyPlugin` or `TextView.autoLinkMask` cannot create a second, less-restrictive clickable path.
  - [ ] Preserve `BetterLinkMovementMethod` behavior for safe links and prevent link taps from bubbling into card tap/mark-read.
- [ ] Harden Markwon image rendering (AC: 3, 4)
  - [ ] Validate the original image destination before Markwon/Picasso receives it.
  - [ ] Drop rejected images before any loader, resolver, network, file, or content request is started.
  - [ ] Preserve current safe remote-image sizing/rendering behavior from the Story 3.6a markdown renderer.
- [ ] Integrate all markdown entry points and recycling fallback (AC: 4, 7)
  - [ ] Route normal markdown bodies and `sections` markdown blocks through the same secured Markwon configuration.
  - [ ] Clear previous text, spans, image drawables/requests, movement state, and auto-link configuration before rebinding a recycled body view.
  - [ ] Wrap parsing/rendering at the body-renderer boundary and display the raw message string on failure.
- [ ] Add golden-corpus security tests (AC: 1–7)
  - [ ] Consume Story 3.0 vectors for safe `http`/`https`/`mailto` links and safe `http`/`https` images.
  - [ ] Cover mixed-case schemes and destinations containing whitespace/control-character obfuscation.
  - [ ] Cover `javascript:`, `data:`, `file:`, `content:`, `intent:`, custom schemes, protocol-relative, relative, empty, and malformed destinations.
  - [ ] Assert safe links have clickable spans; unsafe labels retain text/accent styling but have no clickable spans.
  - [ ] Assert unsafe images create no image span and no loader request; safe image vectors still use the configured image pipeline.
  - [ ] Assert plain-text auto-linking and a `sections` markdown block cannot bypass the policy.
  - [ ] Assert recycled safe → unsafe and unsafe → safe binds leave no stale spans/images.

## Dev Notes

### Developer Context

- Epic 3 owns only the `card_body` renderer. Do not change the card shell, header, metadata row, deletion, swipe behavior, or navigation.
- This story is a security hardening increment on top of Story 3.6a. If the Story 3.6a renderer or Story 3.0 corpus is not yet present in the branch, implement/merge those prerequisites first rather than inventing a parallel markdown stack or duplicate fixtures.
- Current production markdown is created by `MarkwonFactory.createForMessage(context)`. It installs `ImagesPlugin`, `LinkifyPlugin(Linkify.WEB_URLS)`, `MovementMethodPlugin`, and unrestricted `LinkResolverDef`.
- Current `DetailAdapter.DetailViewHolder.bind` separately uses `Linkify.WEB_URLS` for plain text and always installs `BetterLinkMovementMethod`. The future `MessageCardBinder`/body renderer must centralize this behavior so Markwon, plain fallback, and structured `sections` cannot drift.
- This control is client-side defense in depth, not a complete privacy boundary: an allowed remote HTTP(S) image necessarily causes a network request. Do not add cookies, auth headers, local URI access, or a new image library.

### Security Policy Contract

- Canonicalize by parsing the destination as a URI and comparing the scheme, not with substring/prefix matching.
- Scheme comparison is locale-independent and case-insensitive.
- Do not “repair” rejected input, prepend `https://`, or delegate missing schemes to Markwon's default resolver. Markwon 4.3+ defaults schemeless links to HTTPS; this story intentionally requires an explicit allowed scheme.
- Link allowlist: `http`, `https`, `mailto`.
- Image allowlist: `http`, `https`. `mailto` is never a valid image source.
- Reject all local/resource-capable schemes (`file`, `content`, `android.resource`) and launch-capable schemes (`intent`, custom deep links). This prevents local data access and app-intent execution beyond the two schemes explicitly required by NFR4.
- Rejected links keep their label only. Do not display or open the destination as a fallback, and do not leave a `URLSpan`, Markwon `LinkSpan`, or click listener attached.
- Rejected images are absent. Do not show a clickable placeholder, alt-text link, error drawable that retries the destination, or attachment preview.
- Filtering must happen before side effects. A resolver that merely refuses at click time is insufficient for images because loading can begin during rendering.

### Architecture Compliance

- Keep the existing Views/XML + AppCompat architecture. Do not introduce Compose.
- Reuse Markwon 4.6.2 already pinned in `app/build.gradle`; do not upgrade Markwon or add a sanitizer/network library in this story.
- Prefer a small pure Kotlin policy object/function plus narrowly configured Markwon extension points. Keep parsing logic testable without an Activity or live network.
- Preserve the shared body-renderer contract established by Stories 3.1 and 3.6a: structured dispatch → heuristic-kv → paragraph/raw, with raw fallback on exceptions.
- Preserve full-content rendering: no `maxLines`, ellipsis, compact mode, or “show more.”
- Use token resources for inert-link accent styling (`@color/accent_text`) and existing typography. No raw color or size literals.

### Files to Update

- UPDATE `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`
  - Current state: `createForMessage` installs unrestricted `LinkResolverDef`, `ImagesPlugin`, `LinkifyPlugin`, and `BetterLinkMovementMethod`.
  - Change: configure the shared card Markwon instance with the explicit link/image destination policy and inert-link presentation.
  - Preserve: Story 3.6a typography, soft breaks, headings, emphasis, lists, strikethrough, image sizing, and notification-specific factory behavior unless it is intentionally brought under the same policy.
- UPDATE the Story 3.6a card markdown renderer / Epic 3 body renderer (expected under `app/src/main/java/io/heckel/ntfy/ui/`)
  - Change: use the secured factory for top-level markdown and section markdown; reset recycled state; catch rendering failures.
  - Preserve: dispatch order, raw fallback, full-content rendering, card click and long-click contracts.
- UPDATE `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt` only if the Epic 2 binder extraction has not yet replaced this path
  - Current state: markdown and plain text have separate linkification paths; message click listeners coexist with links.
  - Change: remove/bypass any unrestricted fallback path and delegate body rendering to the shared secured renderer.
  - Preserve: attachment/icon rendering, actions, selection, menu behavior, and non-body interactions.
- UPDATE the Story 3.0 golden corpus and its existing test runner; do not create a second security-fixture source.
- ADD focused unit/Robolectric tests in the existing Epic 3 test package. Use instrumentation only where span click propagation cannot be verified below the UI layer.

Exact filenames introduced by Stories 3.0/3.1/3.6a may differ. Extend those established files instead of creating competing `SecureMarkdownRenderer`, `SafeMarkwonFactory`, and fixture systems with overlapping ownership.

### Testing Requirements

- Test the policy helper as a pure matrix first; then test rendered output. String-only assertions are insufficient.
- For links, inspect spans/click targets and verify the resolver is invoked only for allowed destinations.
- For images, inject or fake the image-loading boundary and assert zero requests for rejected sources. Do not make tests depend on the public internet.
- Include both Markdown syntax (`[label](destination)`, `![alt](source)`) and plain text URL linkification because the current app supports both paths.
- Include multiple safe and unsafe destinations in one body to ensure filtering is per node, not all-or-nothing.
- Include recycled-view order tests and malformed/fuzz-like destinations; the card must render raw text and never throw.
- Verify safe link clicks do not trigger the parent card callback. Verify unsafe label taps follow ordinary card-body behavior without launching an external intent.
- Security completion evidence must show no clickable span for rejected links and no image-loader request for rejected images.

### Previous Story Intelligence

- Story 3.6a is the direct prerequisite but no implementation story file existed when this context was created. Its styles and renderer API must be treated as an incoming dependency, not guessed into a second implementation.
- Story 3.0 owns the golden corpus. Story 3.6b consumes and may extend its vectors but must not re-encode the matrix in a private test file.
- Epic 2 story files consistently require adapter-agnostic binding, complete recycled-state resets, Views/XML, token resources, and preservation of attachment/actions/click behavior. Carry those guardrails into the body renderer.

### Git Intelligence

- The latest commits add the canonical UI-parity companions and planning artifacts; there is no committed Epic 3 implementation pattern yet.
- The worktree already contains user-owned story artifacts and sprint-status changes. Modify only this story file and the matching sprint-status entry during story creation.

### Latest Technical Information

- The project is pinned to Markwon 4.6.2. Its changelog documents `LinkResolver` as a configurable independent component, image destination processing as image-specific, and `ImagesPlugin` scheme-handler configuration. Use those supported extension points rather than post-processing after side effects.
- Markwon 4.3 introduced default HTTPS handling for schemeless links. That behavior conflicts with this story's explicit-scheme allowlist and must be overridden or bypassed for card markdown.
- Markwon 4.6.2 added image down-scaling support; preserve the project's existing safe-image rendering path and do not replace it during security hardening.

### Project Structure Notes

- Production renderer/policy code belongs under `app/src/main/java/io/heckel/ntfy/{ui,util}/`, following the ownership established by the Epic 3 renderer and `MarkwonFactory`.
- Security vectors belong in the Story 3.0 corpus location; renderer tests belong beside the Epic 3 tests.
- No layout resource should be needed unless Story 3.6a's renderer requires a dedicated body view. This story changes destination behavior, not card anatomy.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.0, Story 3.6a, Story 3.6b]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-6, Constraints]
- [Source: `docs/ui-parity/message-format.md` — §5 Markdown rendering rules, §6 Fallback path]
- [Source: `docs/ui-parity/components.md` — §1 Notification Card / Body slot / Interaction]
- [Source: `docs/ui-parity/design-tokens.md` — Accent Sub-Token Decision Table]
- [Source: `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/build.gradle` — Markwon 4.6.2 dependencies]
- [Markwon official changelog](https://github.com/noties/Markwon/blob/master/CHANGELOG.md)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.

### File List

- `_bmad-output/implementation-artifacts/3-6b-markdown-link-image-security.md`

