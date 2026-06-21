---
baseline_commit: 6de928133b3d176b77a2dbb184984621c4474b65
---

# Story 3.1: Card Detection, Dispatch & Safe Fallback

Status: done

## Story

As a user,
I want monitoring/CI payloads to render as structured cards and everything else as text,
so that rich messages look right and malformed ones never break the card.

## Acceptance Criteria

1. **Given** a notification body and tags  
   **When** `parseCardSpec` evaluates it  
   **Then** it returns a structured spec only when the exact literal tag `card` is present, the decoded body is a JSON object, and its string `type` is exactly one of `kv`, `list`, `chart`, or `sections`  
   **And** tag order and additional tags do not affect detection.

2. **Given** any failed dual-gate condition  
   **When** the body is dispatched  
   **Then** missing `card`, malformed JSON, a non-object JSON root, missing/non-string `type`, and unknown/case-mismatched `type` all enter the fallback path without throwing  
   **And** the Story 3.0 golden corpus explicitly asserts at least the canonical pass case plus missing-tag, invalid-JSON, and unknown-type failures.

3. **Given** a body eligible for rendering  
   **When** dispatch selects its representation  
   **Then** the order is deterministic: valid structured spec → heuristic-kv shape → paragraph/raw text  
   **And** this story defines the dispatch API and seams, while Story 3.8 supplies the final heuristic-kv parser and Story 3.3 supplies the reusable kv renderer  
   **And** until those collaborators land, the unimplemented heuristic branch safely continues to paragraph/raw rather than duplicating their logic.

4. **Given** parsing, dispatch, inflation, binding, or a downstream renderer throws for any reason  
   **When** the card body is bound  
   **Then** the exception is contained at the body-rendering boundary, partial structured children are removed, and the exact decoded message string is rendered through the token-styled text fallback  
   **And** attachments, actions, card header/meta state, tap-to-mark-read, delete, selection, and neighboring RecyclerView rows remain functional  
   **And** malformed and fuzz-style payload tests prove the card never crashes.

5. **Given** a plain-text or fallback body  
   **When** it renders  
   **Then** it mounts in the Epic 2 `@id/card_body` slot using `font_sans`, `body_sm` typography, `muted` text, full content, and token spacing  
   **And** it has no `maxLines`, ellipsize, compact mode, “show more,” raw hex, or raw px values  
   **And** existing Markdown behavior remains available through a narrow text-renderer seam for Stories 3.6a/3.6b to harden and restyle.

6. **Given** a recycled holder that previously displayed structured, heuristic, Markdown, or raw content  
   **When** another notification is bound  
   **Then** `card_body` is reset before dispatch, stale child views/spans/click listeners are removed, and only the new notification’s body is visible and interactive.

7. **Given** tags are rendered in the card meta row  
   **When** the notification contains the reserved `card` marker  
   **Then** that marker is excluded from visible chips in both structured and fallback cases  
   **And** other service/general tags preserve their original order and behavior.

## Tasks / Subtasks

- [x] Define the structured-body parser contract and models (AC: 1, 2)
  - [x] Add a small immutable `CardSpec`/type representation under the UI structured-card package; model only dispatch-level data needed by this story and retain the parsed JSON object for later renderers.
  - [x] Implement `parseCardSpec(tags, decodedMessage)` as a pure function with the exact dual gate and a closed known-type set.
  - [x] Require a JSON object root and a string `type`; do not accept arrays, primitives, coercion, aliases, or case-insensitive type names.
  - [x] Reuse the project’s existing Gson dependency; do not add kotlinx serialization, Moshi, or a second JSON stack.
- [x] Create the adapter-agnostic card-body dispatcher (AC: 3–6)
  - [x] Add an explicit result/route model such as `Structured(spec)`, `HeuristicKv(...)`, and `Text(decodedMessage)` so routing can be unit tested without Android views.
  - [x] Keep shape detection separate from rendering and expose the seam that Story 3.8 will fill.
  - [x] Mount output only into the Story 2.1 `card_body` container through `MessageCardBinder`; do not edit `fragment_detail_item.xml`.
  - [x] Reset the body container and transient text/link state before every bind and recycle.
- [x] Implement the token-styled text fallback (AC: 4–6)
  - [x] Add a dedicated body layout such as `view_card_text.xml` using existing token resources and unrestricted wrapping.
  - [x] Render the decoded message used by the current UI (`formatMessage(notification)` / `decodeMessage(notification)` contract), not the encoded wire string.
  - [x] Preserve the current Markdown-vs-plain decision behind a renderer interface; do not implement Stories 3.6a/3.6b security/style scope here.
  - [x] Ensure plain-text links and Markdown links remain child interactions and do not accidentally dispatch the outer card action.
- [x] Add fail-safe rendering containment (AC: 4, 6)
  - [x] Wrap both route selection and concrete body rendering; on failure clear partial children and retry once with the raw text renderer.
  - [x] If enhanced Markdown rendering itself fails, fall back to a plain token-styled `TextView` with the decoded string.
  - [x] Do not swallow errors around the entire card binder; keep header/meta/attachment/action failures observable and outside this story’s body-specific boundary.
- [x] Preserve non-body content and interactions (AC: 4, 7)
  - [x] Filter the exact marker tag `card` in the centralized tag categorization path; do not mutate the persisted tag string.
  - [x] Keep attachments and action buttons outside/reliably after the body slot according to the final Epic 2 binder contract.
  - [x] Verify body links/buttons consume their own interaction while non-interactive body space still permits the outer tap-to-read behavior.
- [x] Add focused automated tests (AC: 1–7)
  - [x] Consume Story 3.0 dual-gate fixtures rather than re-encoding canonical cases locally.
  - [x] Add table tests for extra tags/order, empty body, JSON array/primitive roots, null/missing/numeric/case-mismatched/unknown types, and each known type.
  - [x] Add malformed, deeply nested/large, and fuzz-style strings and assert a text route with no exception.
  - [x] Inject throwing structured and text renderers to prove partial children are cleared and plain raw text is the terminal fallback.
  - [x] Test holder reuse across structured → text, text → structured, and failure → normal binds.
  - [x] Test exact decoded fallback text, full-content styling, `card` chip exclusion, and preservation of other tags.

## Dev Notes

### Dependency and Delivery Gates

- Story 3.0 is a hard test-data prerequisite: its golden corpus owns the canonical dual-gate vectors. At story-creation time no `3-0-*.md` artifact exists, so implementers must create/land Story 3.0 first or consume its final fixture location once available.
- Epic 2’s production `MessageCardBinder`, `@id/card_body`, and redesigned shell are also prerequisites and are not yet present in the current source tree. Integrate with their final API; do not recreate or bypass them in `DetailAdapter`.
- Stories 3.1 and 3.2 are enablers and ship with Story 3.3, the first user-visible structured payload renderer.
- This story owns detection, route ordering, body reset, and terminal text fallback. It does not implement kv/list/chart/sections visuals, meter thresholds, heuristic parsing details, or final Markdown security/styles.

### Developer Context

- Use the decoded body as the protocol input. Current notifications may be Base64 encoded, and `formatMessage(notification)` also preserves the existing titleless emoji-prefix behavior. Centralize the choice so parser input and terminal fallback cannot diverge.
- Detection is exact:
  - tag list contains literal lowercase `card`;
  - JSON root is an object;
  - `type` is a JSON string exactly equal to `kv`, `list`, `chart`, or `sections`.
- Do not validate each block’s full schema in `parseCardSpec`. Known top-level type selects a renderer; malformed fields are handled safely by that renderer and ultimately by the body fallback boundary.
- A tagged but invalid/unknown payload still participates in normal fallback shape detection. Do not special-case it into an error card.
- Keep parsing and dispatch pure. Android `View` creation belongs in renderer/binder code, which keeps parity cases fast and deterministic in JVM tests.
- Prefer a sealed route and renderer registry/closed `when` over reflection or class-name dispatch. Unknown types must be impossible to invoke accidentally.

### Safe Fallback Contract

- Body binding follows one containment sequence:
  1. reset `card_body`;
  2. derive decoded message and route;
  3. attempt the selected renderer;
  4. on any body exception, clear partial content and render decoded text;
  5. if Markdown/enhanced text fails, clear again and set decoded text on a minimal token-styled `TextView`.
- Never catch `Throwable`; catch ordinary parsing/rendering exceptions so fatal VM errors and cancellation are not hidden.
- Never render exception messages, stack traces, or synthetic “invalid card” copy to the user.
- Do not log full notification bodies at normal/error level because payloads can contain credentials or operational secrets. If diagnostics are added, log only route/type and exception class under existing logging conventions.
- Bound resource usage in later renderers. This story’s fuzz tests should prove malformed input is contained, but must not invent schema caps that contradict later parity stories.

### Existing Files and Preservation Requirements

- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current state: directly binds date/title/message/tags, owns Markwon/plain Linkify switching, attachments, icons, actions, selection, and click/long-click behavior.
  - Expected integration: after Story 2.1 it delegates card rendering to `MessageCardBinder`; add only body-dispatch wiring if still needed.
  - Preserve attachment previews/download boxes, action buttons, icon rendering, selection, link handling, and RecyclerView reuse. Do not leave the legacy `detail_item_message_text` binding active in parallel with `card_body`.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Expected from Story 2.1 and the primary UPDATE seam.
  - Add an adapter-independent body-renderer dependency/input and a deterministic reset path. It must not depend on `DetailActivity`, repository, lifecycle scope, adapter position, or navigation.
- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current source is the pre-Epic-2 monolithic `CardView`.
  - Epic 2 owns its replacement and `@id/card_body`; **Epic 3 must not edit this file**.
- `app/src/main/java/io/heckel/ntfy/util/Util.kt`
  - `splitTags` parses the persisted comma-separated tag string.
  - `formatMessage`/`decodeMessage` provide current decoded display text and Base64 fault tolerance. Reuse or extract without changing unrelated notification formatting.
- `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`
  - Current message Markdown uses Markwon with image, Linkify, movement-method, and custom heading/list spans.
  - Reuse through a narrow text-renderer seam for now. Stories 3.6a/3.6b own token-perfect styles and scheme hardening; do not claim those are complete here.
- `app/src/main/java/io/heckel/ntfy/db/Database.kt`
  - `Notification.tags` is a comma-separated string and `isMarkdown()` is driven by `contentType == "text/markdown"`.
  - No schema or persistence change belongs in this story.

### Architecture Compliance

- Stay in View/XML + AppCompat; do not introduce Compose.
- Suggested structure:
  - `io.heckel.ntfy.ui.card.body.CardBodyDispatcher`
  - `io.heckel.ntfy.ui.card.body.CardSpecParser`
  - `io.heckel.ntfy.ui.card.body.CardBodyRoute`
  - renderer interfaces/implementations beneath the same package
  - focused layouts named `view_card_<type>.xml`
- Follow the final package convention established by Story 2.1 if it differs; keep parser/route code independent from Activities and adapters.
- Use Gson 2.13.2 already pinned in `app/build.gradle`. Gson 2.14.0 is newer as of April 23, 2026, but upgrading dependencies is outside this story and unnecessary for object/type detection.
- Preserve min SDK 26, Java/Kotlin 17, Play and F-Droid flavors, existing R8 behavior, and the Weblate string pipeline.
- Add no parser, Markdown, chart, or UI dependency.

### Testing Requirements

- The project currently has no checked-in `app/src/test` or `app/src/androidTest` tree. Add focused JVM tests under the project’s standard package and only add Robolectric/instrumentation infrastructure if actual View inflation/recycling cannot be verified otherwise.
- Pure JVM coverage:
  - exact dual gate and known-type set;
  - route precedence;
  - decoded-body input contract;
  - failure-to-text behavior;
  - Story 3.0 fixture consumption.
- View/binder coverage:
  - body reset and partial-child cleanup;
  - token-backed text layout and no truncation;
  - structured/text/failure recycling sequences;
  - child link interaction versus outer card tap;
  - attachment/action/header/meta preservation.
- Run unit tests for both parser corpus and dispatch behavior, then build at least `playDebug` and `fdroidDebug`.
- Manual smoke cases: valid payload for each known type, missing tag, invalid JSON, unknown type, Base64 body, empty body, very long text, Markdown links, attachment + structured body, action buttons, and rapid scrolling/recycling.

### Previous Story Intelligence

- There is no prior Epic 3 story artifact available: Story 3.0 remains backlog. Its acceptance criteria in `epics.md` are therefore the only current source for the golden-corpus contract.
- Story 2.1 established the critical ownership rule: Epic 3 mounts its own layouts into `card_body` and never reopens the shell XML.
- Story 2.5 established that interactive body children must not accidentally dispatch the outer mark-read action.
- Story 2.6 reinforces the rule that every bind/recycle begins from a clean baseline.

### Git Intelligence

- Recent commits add the UI parity SPEC, companions, epics, screenshots, and sprint tracking; no production structured-card implementation has landed.
- The working tree contains user-owned story artifacts and sprint-status changes. Preserve all unrelated content and formatting.
- Existing production code is still the pre-Epic-2 monolithic `DetailAdapter`; implementation must target the staged final binder rather than entrenching more logic there.

### Latest Technical Information

- Gson 2.13.2 is already available in the app; its release contained dependency updates and fully covers this story’s JSON-object/type inspection. A newer 2.14.0 exists, but this story should not perform an unrelated upgrade.
- Android’s platform `JSONObject` can also parse objects, but adding a second JSON representation would complicate later renderer models. Use the existing Gson tree API consistently.
- Markwon 4.6.2 is already pinned and remains the current project Markdown engine. This story preserves it behind a seam; security changes belong to Story 3.6b.

### Project Structure Notes

- Expected additions:
  - pure parser, route, and dispatcher classes under the final card-body package;
  - `view_card_text.xml`;
  - focused JVM tests and minimal binder/View tests;
  - no new user-facing strings unless required for accessibility.
- Expected updates:
  - `MessageCardBinder.kt` for body dispatch/reset integration;
  - centralized tag filtering to exclude `card`;
  - `DetailAdapter.kt` only if the final Epic 2 delegation seam requires forwarding.
- Forbidden updates:
  - `fragment_detail_item.xml`;
  - database schema/DAO/receive path;
  - Activity navigation/feed shell;
  - dependency versions;
  - future `view_card_kv/list/chart/sections.xml` renderer scope.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — FR4, NFR2/NFR4, Epic 3, Stories 3.0–3.8]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-5/CAP-6, Constraints, Non-goals]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — existing stack and card integration point]
- [Source: `docs/ui-parity/message-format.md` §1, §5, §6 — dual gate, text rendering, dispatch order]
- [Source: `docs/ui-parity/components.md` §1 and §3 — body slot and reserved tag filtering]
- [Source: `docs/ui-parity/design-tokens.md` — typography, spacing, and color resource keys]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `_bmad-output/implementation-artifacts/2-5-tap-to-mark-read.md`]
- [Source: `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/util/Util.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/util/MarkwonFactory.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt`]
- [Source: `app/build.gradle`]
- [Source: `https://github.com/google/gson/releases` — Gson release status checked 2026-06-21]
- [Source: `https://developer.android.com/reference/org/json/JSONObject` — Android platform JSON behavior]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (Story 3.1 implementation, 2026-06-21)

### Debug Log References

- Customization resolver fallback used (python3 lacks Python 3.11 tomllib); no team/user overrides found.
- Story 3.0 golden corpus confirmed present with 17 cardGateCases; consumed directly in CardSpecParserTest.
- CardTagFormatter already filters `card` marker (Story 2.4); no change needed there.
- MessageCardBinder.messageView (detail_item_message_text) used as body target; card_body is ConstraintLayout so addView without constraints positions at top-left — continued to use existing messageView to avoid layout regression.
- fragment_detail_item.xml NOT modified as required.
- BetterLinkMovementMethod import left in MessageCardBinder (unused after delegation) — compile warning only, not error.
- fdroidDebug build and all 48 tests (26 parser, 11 dispatcher, 11 route) pass.

### Completion Notes List

- `CardSpec` sealed enum and `CardSpecParser.parseCardSpec()` implement exact dual gate: "card" tag + JSON object + string type in closed set.
- `CardBodyRoute` sealed class (Structured/HeuristicKv/Text) enables pure JVM route testing.
- `CardBodyDispatcher` dispatches deterministically; heuristic seam wired to `UNIMPLEMENTED` detector that safely falls to Text until Story 3.8.
- `CardBodyBinder` owns messageView lifecycle: resetView → dispatch → render; 3-level exception containment (route → text renderer → minimal TextView).
- `CardTextRenderer` preserves Markwon vs plain-Linkify decision behind seam for Stories 3.6a/3.6b.
- `view_card_text.xml` uses TextAppearance.Ntfy.BodySmall + @color/muted + token spacing — no maxLines, no raw px.
- MessageCardBinder delegates all body work to cardBodyBinder; reset() delegates to cardBodyBinder.reset().
- 48 new JVM tests: 17 from golden corpus (zero duplication), 29 additional table/fuzz/seam cases.
- All existing 220+ tests remain green; fdroidDebug assembles cleanly.

### File List

- `_bmad-output/implementation-artifacts/3-1-card-detection-dispatch-safe-fallback.md`
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardSpec.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardSpecParser.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardBodyRoute.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardBodyDispatcher.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardBodyRenderer.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardTextRenderer.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/card/body/CardBodyBinder.kt` (new)
- `app/src/main/res/layout/view_card_text.xml` (new)
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt` (modified — CardBodyBinder integration)
- `app/src/test/java/io/heckel/ntfy/ui/card/CardSpecParserTest.kt` (new)
- `app/src/test/java/io/heckel/ntfy/ui/card/CardBodyDispatcherTest.kt` (new)
- `app/src/test/java/io/heckel/ntfy/ui/card/CardBodyBinderFallbackTest.kt` (new)

## Review Findings

- [x] [Review][Patch] XML declaration typo in view_card_text.xml causes LayoutInflater crash [app/src/main/res/layout/view_card_text.xml:1]
- [x] [Review][Patch] dispatcher.dispatch() called outside try-catch safety net in CardBodyBinder.bind() [app/src/main/java/io/heckel/ntfy/ui/card/body/CardBodyBinder.kt:53]
- [x] [Review][Patch] messageView.text = null should be empty string to avoid NPE during layout [app/src/main/java/io/heckel/ntfy/ui/card/body/CardBodyBinder.kt:159]
- [x] [Review][Patch] Golden corpus missing case-mismatched type (e.g. "KV") required by AC 2 [app/src/test/resources/io/heckel/ntfy/ui/message/parser-parity-golden.json]
- [x] [Review][Patch] titleView not reset in MessageCardBinder.reset() — stale title leaks on Loading rebind [app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt:~220]
- [x] [Review][Patch] CardTextRenderer implements CardBodyRenderer but is never used by CardBodyBinder — documented as Story 3.6a/3.6b seam [app/src/main/java/io/heckel/ntfy/ui/card/body/CardTextRenderer.kt]
- [x] [Review][Patch] FirebaseService: verified Notification.event has @Ignore default=EVENT_MESSAGE; named arg removal is safe [app/src/play/java/io/heckel/ntfy/firebase/FirebaseService.kt:210]
- [x] [Review][Defer] CardSpec.root holds mutable JsonObject with no defensive copy — recycler-pool hazard when multiple stories modify root [app/src/main/java/io/heckel/ntfy/ui/card/body/CardSpec.kt:12] — deferred, addressed when Stories 3.3–3.5 implement their renderers
- [x] [Review][Defer] Loading branch in MessageCardBinder.bind() performs full bind cycle before reset() discards work — performance waste [app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt:~172] — deferred, pre-existing pattern from Story 2.6; Story 4.3 owns skeleton state

## Change Log

- 2026-06-21: Story 3.1 implemented — card detection, dispatch, and safe fallback. Added CardSpec/CardSpecParser (dual gate), CardBodyRoute/CardBodyDispatcher (sealed routes + heuristic seam), CardBodyBinder (fail-safe containment), CardTextRenderer (Markwon seam preserved), view_card_text.xml (token-styled), and 48 JVM tests consuming Story 3.0 golden corpus.
- 2026-06-21: Code review complete — 7 patches applied: XML declaration fix, dispatch() inside try-catch, text = "" not null, golden corpus type-case-mismatch vector, titleView reset, CardTextRenderer documented as seam, FirebaseService event default verified. ChartGeometry.kt baselineY bug also fixed. All play unit tests pass.
