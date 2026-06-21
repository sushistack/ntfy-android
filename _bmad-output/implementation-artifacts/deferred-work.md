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

## Deferred from: code review of 2-4 and 2-6 (2026-06-21)

- `formatAbsoluteTimestamp` uncaught `DateTimeException` for extreme Long epoch values (> year ~292 million) — practical risk is near-zero given server-side validation; clamp or catch if untrusted data path changes in future.

- `ObjectAnimator.ofArgb(cardView, "cardBackgroundColor", …)` getter/setter type mismatch (CardView.getCardBackgroundColor() returns ColorStateList, not int) — currently non-crashing because 3-arg ofArgb skips the getter. Revisit if CardView library updates change the setter signature; workaround is a ValueAnimator with explicit update listener.

- Story 2.6 effects (NewArrival, DeepLinkPulse) and ArrivalAnnouncer not wired in DetailAdapter — intentional; Epic 4 Stories 4.1/4.2/4.3 own host-level wiring for feed arrival tracking and deep-link resolution.
