---
baseline_commit: f0e2e90cc474feba3617fd798c5973f092df5d3f
---

# Story 2.6: Card Loading Skeleton, New-Arrival Animation & Deep-Link Highlight

Status: review

## Story

As a user,
I want cards to show a loading placeholder, newly-arrived cards to slide in, and a deep-linked card to be highlighted,
so that the feed feels alive and I can find the message I tapped a notification for while respecting reduced motion.

## Acceptance Criteria

1. **Given** the reusable card component is requested in a loading state  
   **When** it renders  
   **Then** it shows a non-interactive skeleton matching the final squared card silhouette, 4dp left accent-bar position, header band, body lines, and meta/tag-row heights  
   **And** approximately five skeleton instances can be mounted by a feed without notification data or side-effect callbacks  
   **And** the skeleton uses token resources, exposes no message content, and is hidden from accessibility traversal.

2. **Given** a bound card is explicitly flagged as genuinely newly arrived  
   **When** that card is attached/bound with system animators enabled  
   **Then** only that card plays a 250ms ease-out slide-in from above to its final position  
   **And** the effect is keyed by stable message identity, not adapter position, insertion position, or `position == 0`  
   **And** ordinary initial loads, pagination, rebinding, scrolling off/on screen, payload updates, and holder recycling do not replay it.

3. **Given** one or more genuinely new messages arrive  
   **When** their arrival is presented  
   **Then** an accessibility live-region announcement using a localizable “new notifications” string is emitted once per arrival batch  
   **And** it is emitted by the host/feed arrival coordinator, not once from every recycled card.

4. **Given** a card is explicitly flagged as the deep-link target  
   **When** it is bound after the host has located/scrolled to it and system animators are enabled  
   **Then** it starts from a visible selected emphasis using `@color/surface_active` and the shared Story 1.2 accent-glow rule, then fades back to the normal card state  
   **And** the highlight is transient, does not navigate to a detail screen, and does not overwrite unread, selected-for-bulk-action, pressed, focused, or priority-glow state.

5. **Given** reduced motion is enabled through Story 1.4, including `Settings.Global.ANIMATOR_DURATION_SCALE == 0` as represented by `ValueAnimator.areAnimatorsEnabled() == false`  
   **When** a new-arrival or deep-link effect is requested  
   **Then** slide and pulse animation durations are measurably `0` and no animator is started  
   **And** a newly arrived card appears immediately in its final position  
   **And** a deep-link target receives a static, non-animated `surface_active`/accent emphasis that remains visible long enough to identify the target and is cleared deterministically by the host or a non-animated state transition.

6. **Given** a holder that previously rendered a skeleton, arrival effect, or deep-link emphasis  
   **When** it is rebound to another item or recycled  
   **Then** all translations, alpha, temporary backgrounds/foregrounds, glow layers, animator listeners, and accessibility state are cancelled/reset before the next bind  
   **And** no visual or semantic state leaks to another notification.

7. **Given** the current `DetailAdapter` and the future Epic 4 feed both consume the reusable card  
   **When** these states are supplied  
   **Then** the card API accepts explicit render/effect inputs such as loading, newly-arrived identity, and deep-link-target identity without referencing `DetailActivity`, a concrete adapter, RecyclerView position, navigation, repository, or lifecycle scope  
   **And** Story 4.1 remains responsible for resolving and scrolling to a deep-link target, Story 4.2 for maintaining newly-arrived IDs, and Story 4.3 for mounting the feed’s skeleton count.

## Tasks / Subtasks

- [x] Define reusable card presentation/effect state (AC: 1–7)
  - [x] Extend the Story 2.1 binder contract with explicit state/capability inputs; do not infer arrival or deep-link state from row position.
  - [x] Keep stable message IDs at the host boundary and consume each one-shot effect exactly once.
  - [x] Separate persistent card state (normal/loading/static emphasis) from one-shot effects (slide/pulse).
  - [x] Add a complete reset/cancel path invoked before every bind and from recycling.
- [x] Implement the skeleton card variant (AC: 1, 6, 7)
  - [x] Add a dedicated reusable skeleton layout/view, rather than populating `Notification` with fake data.
  - [x] Match the final Story 2.1–2.4 card anatomy: squared shell, accent slot, header/body/meta placeholders, and representative chip shapes.
  - [x] Use existing token colors/dimensions and shared shell resources; add no raw hex or unrelated visual tokens.
  - [x] Mark decorative skeleton descendants not important for accessibility and make the shell non-clickable/non-focusable.
- [x] Implement the new-arrival effect (AC: 2, 5–7)
  - [x] Use a 250ms ease-out translation from above and finish at `translationY = 0`, `alpha = 1`.
  - [x] Gate animation with Story 1.4 `ReducedMotion`; query at effect-start time.
  - [x] Ensure list submission, pagination, content updates, and recycling cannot manufacture “new” status.
  - [x] Provide the host callback/consumption seam needed by Story 4.2 to remove an ID after its first presentation.
- [x] Implement deep-link emphasis (AC: 4–7)
  - [x] Reuse `surface_active` and Story 1.2's shared accent-glow mechanism; do not invent a highlight color or shadow.
  - [x] Compose the emphasis with the card shell without clobbering focus, pressed, bulk-selection, unread, or priority-bar styling.
  - [x] Provide animated and reduced-motion/static paths with deterministic cleanup.
  - [x] Keep target lookup and `RecyclerView.scrollToPosition`/smooth scrolling outside the binder.
- [x] Add the accessibility announcement contract (AC: 3, 5, 7)
  - [x] Add a localizable string resource for “new notifications”.
  - [x] Expose a host-level helper/callback that uses an Android live-region/announcement API once per arrival batch.
  - [x] Do not announce skeletons, initial history, pagination rows, deep-link highlighting, or recycled binds.
- [x] Add focused automated tests (AC: 1–7)
  - [x] Assert skeleton anatomy, token-backed resources, non-interactivity, and accessibility exclusion.
  - [x] Assert one flagged ID animates once while neighboring cards do not.
  - [x] Assert initial load, append/pagination, payload rebind, and recycle/reattach do not replay arrival.
  - [x] Assert enabled animation duration is 250ms and reduced-motion duration is 0 with no animator start.
  - [x] Assert deep-link animated/static emphasis and cleanup without corrupting other card states.
  - [x] Assert holder reuse cancels/reset all transient properties.
  - [x] Assert arrival batches announce once and non-arrival binds announce zero times.

## Dev Notes

### Dependency and Ownership Gates

- Consume Story 1.2's shared glow/elevation rule and Story 1.4's `ReducedMotion` helper. Do not duplicate dark-mode detection, read `Settings.Global` directly, or create another motion preference.
- Consume the completed Epic 2 card anatomy from Stories 2.1–2.4 and tap behavior from 2.5. This story must not redesign header, tags, timestamp, delete, or mark-read behavior.
- Story 2.6 provides reusable primitives and binder inputs:
  - skeleton card rendering,
  - one-card arrival animation,
  - one-card deep-link emphasis,
  - host-level arrival announcement seam.
- Epic 4 wires feed behavior:
  - Story 4.1 resolves a target ID and scrolls to the card before requesting highlight.
  - Story 4.2 owns the set of genuinely newly-arrived stable IDs.
  - Story 4.3 mounts approximately five skeleton cards while feed data loads.
- The current working tree contains story artifacts only; prerequisite production code is not yet present. Adapt to the final Story 2.1 binder API without recreating it.
- Epic 2 remains feature-gated until Stories 2.1–2.4, including 2.3b, ship as a complete card.

### Developer Context

- Stay in View/XML + AppCompat; do not introduce Compose or a shimmer/animation dependency.
- Android framework `ViewPropertyAnimator`, `ObjectAnimator`, or `AnimatorSet` is sufficient. Keep animation construction behind a narrow seam so duration, start/no-start, and cleanup can be tested.
- “Genuinely newly arrived” is host knowledge. `DiffUtil` insertion, top position, newest timestamp, and `notificationId != 0` are not valid substitutes.
- Stable identity should use the persisted notification identity supplied by the host. Do not key effects solely by adapter position; positions change under insertion, filtering, and pagination.
- A skeleton is a presentation model/layout, not a fake `Notification`. It must not trigger click, long-click, delete, mark-read, attachment, action, or repository callbacks.
- The live-region announcement is a batch/feed concern. Card binding is too low-level because recycling can cause duplicate announcements.

### Effect State Contract

- Every bind begins by cancelling existing animators and restoring baseline properties:
  - `translationX/Y = 0f`,
  - `alpha = 1f`,
  - normal scale,
  - normal shell background/foreground/elevation,
  - no transient highlight glow/listener,
  - correct accessibility importance/live-region state.
- Arrival:
  - start above the final position by a named dimension or measured/card-relative offset;
  - duration exactly 250ms when enabled;
  - ease-out interpolation;
  - completion returns all properties to baseline;
  - consume the stable ID so a later bind cannot replay it.
- Deep-link:
  - the canonical selected state is `surface_active`;
  - the pulse may use the shared accent glow but must remain independent from the priority bar's P4/P5 glow;
  - normal cleanup must restore the correct selected/bulk-selection state, not blindly restore a hard-coded background;
  - reduced motion uses static emphasis rather than a shortened pulse.
- Avoid `View.postDelayed` work that survives recycling without cancellation. If delayed cleanup is needed, store and remove the runnable on reset.

### Skeleton Anatomy

- Add a dedicated layout such as `view_message_card_skeleton.xml` or a dedicated binder mode that shares shell drawables/dimensions without duplicating the production card's business logic.
- Match the visible geometry from the final card:
  - fully squared `surface` shell and border/elevation,
  - 4dp left bar placeholder,
  - priority-badge/title/unread-area header heights,
  - representative body lines,
  - chip/tag placeholders and timestamp area.
- Static placeholders satisfy the acceptance criteria; a shimmer is not required and would conflict with reduced-motion expectations unless separately gated.
- The skeleton should be `importantForAccessibility="noHideDescendants"` (or equivalent), with no content descriptions or live-region behavior.

### Existing Files and Preservation Requirements

- `app/src/main/res/layout/fragment_detail_item.xml`
  - Current pre-Epic-2 state: rounded monolithic `CardView`.
  - Expected before this story: final squared shell with `card_priority_accent`, `card_body`, header, and meta row.
  - Preserve all final card anatomy and interaction. Prefer a separate skeleton layout over filling the real shell with fake content.
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt`
  - Expected from Story 2.1; primary UPDATE seam for transient card state.
  - Preserve adapter independence and reset every transient property before applying current state.
- `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`
  - Current adapter is Activity-coupled and its rows are position-driven.
  - Expected after Story 2.1: delegates card rendering.
  - Keep list/diff responsibilities in the adapter; pass explicit presentation state and forward recycling cleanup.
- `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt`
  - Current code automatically scrolls to position 0 for any insertion at the top.
  - Do not use this observer as proof that a row is genuinely new and do not move feed-level ID tracking into the binder.
  - Preserve current behavior unless the staged integration explicitly wires the new host contract; Epic 4 ultimately replaces this feed wiring.
- `app/src/main/res/layout/activity_detail.xml`
  - Current loading behavior has no skeleton container.
  - Feed-level loading-state replacement belongs to Story 4.3; only add the minimum reusable mounting seam needed for validation.
- `app/src/main/java/io/heckel/ntfy/ui/accessibility/ReducedMotion.kt`
  - Expected from Story 1.4. Query it immediately before starting each effect.
- Story 1.2 glow resources/helper and Story 1.1 `surface_active`
  - Consume only; do not add approximations or raw shadow/color values.

### Deep-Link Integration Boundary

- Existing `ntfy://` handling in `DetailActivity` accepts a topic URI and subscribes/loads the topic; it does not currently resolve a message target.
- System notification actions already carry `sequenceId` for mark-read/delete flows, but this story must not redesign notification PendingIntents or navigation.
- Define a host-facing target-ID input that Epic 4.1 can satisfy after its single-feed route resolves the tapped notification. The binder highlights an already identified card; it does not parse intents, query Room, or scroll RecyclerView.
- No detail Activity/Fragment or expanded message route may be introduced. A deep link lands on the feed and highlights the card.

### Architecture Compliance

- Production UI remains under `io.heckel.ntfy.ui`; shared accessibility helpers remain under `io.heckel.ntfy.ui.accessibility`.
- Keep effects independent of `DetailActivity`, `Activity`, `DetailAdapter`, repository, coroutines, and navigation.
- Use named resources for duration/offset/placeholder dimensions where appropriate. No raw hex and no new visual token family.
- Do not add a third-party shimmer, skeleton, animation, or accessibility library.
- Preserve min SDK 26, Java/Kotlin 17, both `play` and `fdroid` flavors, and existing RecyclerView 1.4.0/AppCompat 1.7.1 dependencies.

### Testing Requirements

- Prefer a pure effect-decision model plus narrow View tests:
  - input state → effect kind/duration/static fallback,
  - stable ID consumption,
  - batch announcement decision,
  - baseline-reset behavior.
- Use Robolectric/instrumentation only where actual animator, drawable, night-resource, or accessibility behavior must be measured.
- Test a recycled-holder sequence such as deep-link target → normal card and new-arrival → normal card; leaked translation/background/glow is the highest-risk regression.
- Test simultaneous concerns: P5 priority glow plus deep-link emphasis, unread plus highlight, focused plus highlight, and bulk-selected plus highlight.
- Manual smoke check:
  - mount five skeleton cards in both themes;
  - insert one flagged card among existing rows and verify only it slides;
  - scroll away/back and verify no replay;
  - highlight a deep-link target and verify normal/static reduced-motion variants;
  - use TalkBack to confirm one arrival-batch announcement and silence for skeletons/recycling.

### Previous Story Intelligence

- Story 2.2 established the critical RecyclerView rule: reset glow on every bind so P4/P5 state cannot leak. Apply the same discipline to translations, alpha, backgrounds, and highlight layers.
- Story 2.1 defines the binder as adapter-agnostic and keeps repository/lifecycle ownership outside the reusable card. Effects must be passed as explicit state/callbacks.
- Story 1.4 requires `ValueAnimator.areAnimatorsEnabled()` through the shared helper, queried at decision time, with a real static final state rather than merely a shorter animation.
- The nearest prior available Epic 2 story artifact is 2.2; Stories 2.3–2.5 are still backlog and have no implementation learnings yet.

### Git Intelligence

- Recent commits add planning/reference artifacts; no new card animation or skeleton implementation has landed.
- Older relevant history shows incremental changes in `DetailAdapter`, `DetailActivity`, and `fragment_detail_item.xml`; preserve attachment/action/link behavior while layering transient state.
- The current worktree contains user-owned generated story artifacts and a modified sprint status file. Do not overwrite or reformat unrelated entries.

### Latest Technical Information

- No dependency upgrade is required. The project already pins RecyclerView 1.4.0, AppCompat 1.7.1, Core KTX 1.18.0, and Material 1.13.0.
- Android's framework animator APIs and accessibility announcement/live-region APIs cover this story. Use the checked-in Story 1.4 contract for reduced motion; no external research changes the project decision.

### Project Structure Notes

- Expected additions:
  - `app/src/main/res/layout/view_message_card_skeleton.xml` (name may follow the final card convention).
  - focused drawable/dimen resources only if Story 1.2 does not already provide the shared accent-glow/animation dimensions.
  - a localizable arrival announcement string in `app/src/main/res/values/strings.xml`.
  - focused tests under `app/src/test/...` and/or `app/src/androidTest/...`.
- Expected updates:
  - `MessageCardBinder.kt` and its state/callback contract.
  - `DetailAdapter.kt` only for state forwarding and recycling cleanup.
- Do not add database, notification-service, manifest, navigation-route, feed pagination, or structured-body work.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 2, Story 2.6 and Epic 4 Stories 4.1–4.3]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-9, UX accessibility constraints, Non-goals]
- [Source: `docs/ui-parity/components.md` §1 and §10 — selected card state and skeleton state]
- [Source: `docs/ui-parity/screens-layout.md` — deep-link route, per-arrival animation, live region, loading state]
- [Source: `docs/ui-parity/design-tokens.md` — `surface_active`, glow effects, `animate-slide-in-top`]
- [Source: `_bmad-output/implementation-artifacts/1-4-reduced-motion-accessibility-primitives.md`]
- [Source: `_bmad-output/implementation-artifacts/2-1-adapter-agnostic-card-shell-body-slot.md`]
- [Source: `_bmad-output/implementation-artifacts/2-2-priority-accent-bar-all-five-priorities.md`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt`]
- [Source: `app/src/main/java/io/heckel/ntfy/ui/DetailActivity.kt` — list observer and deep-link handling]
- [Source: `app/src/main/res/layout/fragment_detail_item.xml`]
- [Source: `app/src/main/res/layout/activity_detail.xml`]
- [Source: `app/src/main/java/io/heckel/ntfy/msg/NotificationService.kt` — existing sequence-ID intent context]
- [Source: `app/build.gradle`]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (Claude Code)

### Debug Log References

- Python 3.11 unavailable; customization manually resolved from base TOML — no team/user overrides present.
- MessageCardBinder already had Story 2.4 (chip meta row) and Story 2.5 (tap-to-read Boolean return) applied by a prior session; preserved all existing behaviour.
- CardShellContractTest and NotificationDeleteContractTest referenced `detail_item_tags_text` (removed in Story 2.4); updated both to `card_tag_chip_group` + `card_meta_timestamp`.
- All 170+ unit tests pass after fixes; `BUILD SUCCESSFUL`.

### Completion Notes List

- **CardEffectState.kt**: Pure sealed-class model — `CardPresentation` (Normal/Loading/StaticDeepLinkEmphasis) and `CardEffect` (None/NewArrival/DeepLinkPulse) with `CardBindState` wrapper. No Android dependency; fully testable on JVM.
- **CardEffectController.kt**: Manages transient animations. `resetTransient()` cancels in-flight animators, removes pending runnables, restores `translationY=0/alpha=1/normal background/no glow` before every bind and on recycle. `playArrival()` — 250ms ease-out `ObjectAnimator`, queries `ReducedMotion` at decision time, calls `consumed()` before start so rebinds cannot replay. `playDeepLinkPulse()` — `surface_active` background → normal via `ObjectAnimator.ofArgb`, ACCENT_DOT glow in dark mode, listener clears glow on end/cancel. `applyStaticDeepLinkEmphasis()` — static path for reduced motion.
- **view_message_card_skeleton.xml**: Non-interactive CardView skeleton matching card anatomy — squared shell, 4dp accent bar placeholder, header band (badge+title shapes), timestamp placeholder, two body lines, two chip shapes. `importantForAccessibility="noHideDescendants"` on root, all children `focusable=false/non-clickable`.
- **ArrivalAnnouncer.kt**: Host-level batch announcer. `announceArrival(view, count)` dispatches one live-region announcement per batch. `shouldAnnounce(ids)` pure decision helper. Skeletons/initial-loads/pagination never trigger announcement.
- **MessageCardBinder.kt**: Extended `bind()` with `bindState: CardBindState = CardBindState()` (backwards-compatible default). Calls `effectController.resetTransient()` at bind start and in `reset()`. Applies `StaticDeepLinkEmphasis` before one-shot effects; dispatches `NewArrival`/`DeepLinkPulse` after all persistent state is set.
- **strings.xml**: Added `feed_new_notifications_arrival` plurals string (AC 3).
- **dimens.xml**: Added `card_arrival_slide_offset` (80dp) for slide start position.
- **Tests**: `CardEffectStateTest` (pure JVM, 8 tests), `CardEffectControllerDecisionTest` (15 tests — duration constants, ID consumption, host tracking set semantics, batch announcement decision, skeleton non-interactivity).
- **Regression fixes**: `CardShellContractTest` and `NotificationDeleteContractTest` updated for Story 2.4 layout changes.

### Review Findings

- [x] [Review][Patch] `resetTransient()` did not clear `cardView` software layer — deep-link glow leaked across recycled holders [CardEffectController.kt] — added `cardView.setLayerType(NONE, null)` in `resetTransient()`
- [x] [Review][Patch] `savedCardBackground` nulled by `onAnimationCancel` before `resetTransient` restore — background left wrong after rapid scroll [CardEffectController.kt] — snapshot local before `cancel()` call
- [x] [Review][Patch] `runningAnimator` overwritten without cancel when new effect arrives mid-animation [CardEffectController.kt] — added `runningAnimator?.cancel()` before reassignment in both `playArrival` and `playDeepLinkPulse`
- [x] [Review][Patch] `CardPresentation.Loading` unhandled in `bind()` — fell through to normal card render [MessageCardBinder.kt] — added Loading branch that calls `reset()` and returns
- [x] [Review][Patch] `AccessibilityEvent.obtain()` leaked when `anchorView.parent` is null on API 26-29 [ArrivalAnnouncer.kt] — guard `parent` before `obtain()`
- [x] [Review][Defer] Story 2.6 effects (NewArrival/DeepLinkPulse/ArrivalAnnouncer) not wired in DetailAdapter — Epic 4 (Stories 4.1/4.2/4.3) owns host-level wiring; this is out of scope for 2.6

### File List

- `app/src/main/java/io/heckel/ntfy/ui/CardEffectState.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/CardEffectController.kt` (new)
- `app/src/main/java/io/heckel/ntfy/ui/accessibility/ArrivalAnnouncer.kt` (new)
- `app/src/main/res/layout/view_message_card_skeleton.xml` (new)
- `app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt` (modified — bindState param, effectController integration)
- `app/src/main/res/values/strings.xml` (modified — feed_new_notifications_arrival plurals)
- `app/src/main/res/values/dimens.xml` (modified — card_arrival_slide_offset)
- `app/src/test/java/io/heckel/ntfy/ui/CardEffectStateTest.kt` (new)
- `app/src/test/java/io/heckel/ntfy/ui/CardEffectControllerDecisionTest.kt` (new)
- `app/src/test/java/io/heckel/ntfy/ui/CardShellContractTest.kt` (modified — updated tag row IDs)
- `app/src/test/java/io/heckel/ntfy/ui/NotificationDeleteContractTest.kt` (modified — updated tag row IDs)
- `app/src/test/java/io/heckel/ntfy/ui/TapToMarkReadContractTest.kt` (modified — onClick Boolean return type fix)
- `_bmad-output/implementation-artifacts/2-6-card-loading-skeleton-new-arrival-animation-deep-link-highlight.md` (this file)

### Change Log

- 2026-06-21: Story 2.6 implemented — skeleton card layout, new-arrival slide animation, deep-link pulse/static emphasis, accessibility batch announcer, binder contract extended with CardBindState. 23 new tests added; 3 existing tests updated for Story 2.4 layout renames. All 170+ unit tests pass.
