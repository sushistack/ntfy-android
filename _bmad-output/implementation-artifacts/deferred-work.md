# Deferred Work

## Deferred from: code review of 0-1 and 0-2 (2026-06-21)

- `19.json` schema not committed — `MigrationTestHelper.runMigrationsAndValidate` requires the target schema JSON in test assets. Generate via `./gradlew :app:kspFdroidDebugKotlin` (or any assemble variant) on a machine with the Android SDK, then commit `app/schemas/io.heckel.ntfy.db.Database/19.json`. Violates Story 0-1 AC 4 and AC 7 until done.

- `ORDER BY sequenceId DESC` is lexicographic TEXT sorting — if the ntfy server ever emits bare numeric sequence IDs (e.g. `"9"`, `"10"`), `"9"` sorts after `"10"`, breaking recency order. Current tests use same-digit-length padded strings that happen to sort correctly. Requires a protocol decision: confirm server always emits opaque non-numeric identifiers, or change the ordering strategy (e.g. zero-pad, `CAST`, or switch to `serverSequence Long` once populated).

- `fallbackToDestructiveMigration(true)` coexists with all explicit migrations — pre-existing. If a future migration is missing, Room silently drops all user data instead of crashing. Consider removing or scoping to dev-only builds after all migration paths are verified.

## Deferred from: code review of 1-3-light-dark-system-theme-switch and 1-4-reduced-motion-accessibility-primitives (2026-06-21)

- `markAsRead(id: String)` method added to Repository by a parallel story session (Epic 2 work) — not part of Stories 1-3/1-4 scope. Verify type consistency with DAO signature and cover with a test in the originating story's review.

## Deferred from: code review of 2-1-adapter-agnostic-card-shell-body-slot (2026-06-21)

- P5: `toggleSelection` 마지막 아이템 해제 시 `notifyItemChanged` 미호출 — `DetailActivity.endActionModeAndRedraw()`가 전체 rebind로 보정하므로 실제 시각적 버그 없음. DetailActivity 구조 변경 시 재검토 필요.

- P7: `GlobalScope.launch` in `onDeleteAttachment` — lifecycle-unaware. 기존 코드 패턴. Activity가 파괴된 후 DB write가 계속 실행될 수 있음. lifecycleScope로 교체 시 DetailAdapter 시그니처 변경 없이 가능하므로 별도 cleanup 스토리에서 처리 권장.

## Deferred from: code review of 3-8-heuristic-kv-fallback-untagged-key-value (2026-06-21)

- `CardBodyBinder.kt:128` — bodyContainer=null fallback path passes `joinToString` reconstruction of KvSpec rows instead of original `decodedBody`. Blank lines are stripped and key/value whitespace is normalized, so the displayed text may differ slightly from the raw message. Affects only the no-container degraded path; fix by passing `decodedBody` directly.

- `CardBodyBinder.kt:89/99/108/125` — All structured-renderer branches (KV, LIST, CHART, HeuristicKv) do an early `return` after delegating to the block renderer, bypassing the `attachListeners(messageView, …)` call at the bottom of `renderRoute`. `messageView` is GONE so its listeners are unused, but if a card shell ever routes tap events through the body view this will silently fail. Pre-existing across all structured branches; evaluate when card interaction model is finalized.

- `SectionsBlockRenderer.kt:111` — `maxLines = Int.MAX_VALUE` is set explicitly on the markdown child TextView despite the surrounding comment saying "no maxLines". Functionally correct (Android default is `Int.MAX_VALUE`), but the explicit assignment contradicts the comment and may mislead future reviewers. Remove the assignment or update the comment.

- AC 9 fault-tolerance (CardBodyBinder): the JVM test `throwing heuristic detector causes Text fallback` only verifies that `CardBodyDispatcher` re-throws; it does not verify that `CardBodyBinder`'s try/catch catches the exception and displays raw text. Requires Robolectric or instrumented test.

- AC 10 no-truncation (heuristic-kv path): there is no view-layer test asserting that heuristic-kv rows are fully visible without truncation. Inherited from Story 3.3's `KvBlockRenderer` coverage, but not re-verified for the heuristic dispatch path. Add instrumented test when AC 10 coverage is swept across Epic 3.

## Deferred from: code review of 2-4 and 2-6 (2026-06-21)

- `formatAbsoluteTimestamp` uncaught `DateTimeException` for extreme Long epoch values (> year ~292 million) — practical risk is near-zero given server-side validation; clamp or catch if untrusted data path changes in future.

- `ObjectAnimator.ofArgb(cardView, "cardBackgroundColor", …)` getter/setter type mismatch (CardView.getCardBackgroundColor() returns ColorStateList, not int) — currently non-crashing because 3-arg ofArgb skips the getter. Revisit if CardView library updates change the setter signature; workaround is a ValueAnimator with explicit update listener.

- Story 2.6 effects (NewArrival, DeepLinkPulse) and ArrivalAnnouncer not wired in DetailAdapter — intentional; Epic 4 Stories 4.1/4.2/4.3 own host-level wiring for feed arrival tracking and deep-link resolution.

## Deferred from: code review of 4-6 through 4-9 (2026-06-21)

- `DrawerSubscriptionAdapter.setActiveSubscriptionId` 미호출 (4-6/4-7) — Story 4.1 완료 후 feed navigation이 통합될 때 active-row 하이라이트 함께 연결 필요.

- Drawer row tap → `DetailActivity` 실행 (4-7 AC6 위반) — transitional stub; Story 4.1/4.6 feed shell이 주 UI가 될 때 per-topic feed navigation으로 교체 예정.

- `discardOptimistic` 확인 다이얼로그 문구 "Delete this notification?" 오용 (4-9) — outbox pending 메시지는 미발송 아웃박스 아이템이므로 전용 string 리소스(`optimistic_discard_dialog_message` 등) 추가 권장.

- `observeOutbox` 패턴: `lifecycleScope.launch { flow.collect { } }` 사용 중 (4-9) — `lifecycle-runtime-ktx` dependency 추가 후 `repeatOnLifecycle(STARTED)`로 개선 권장. 현재는 프로젝트 표준 패턴과 일치.

- PopupMenu divider 항목이 Android 표준 group separator 없이 구현됨 (4-7) — `<group>` 태그 기반 divider로 개선 시 메뉴 외관이 향상됨. 기능에는 영향 없음.

- Priority chip 배경 raw hex arithmetic (4-8) — `0x1A000000` 등 직접 계산 대신 전용 token(예: `@color/muted_10`) 추출 권장. 기능은 정확함.
