# Deferred Work

## Deferred from: code review of 0-1 and 0-2 (2026-06-21)

- `19.json` schema not committed — `MigrationTestHelper.runMigrationsAndValidate` requires the target schema JSON in test assets. Generate via `./gradlew :app:kspFdroidDebugKotlin` (or any assemble variant) on a machine with the Android SDK, then commit `app/schemas/io.heckel.ntfy.db.Database/19.json`. Violates Story 0-1 AC 4 and AC 7 until done.

- `ORDER BY sequenceId DESC` is lexicographic TEXT sorting — if the ntfy server ever emits bare numeric sequence IDs (e.g. `"9"`, `"10"`), `"9"` sorts after `"10"`, breaking recency order. Current tests use same-digit-length padded strings that happen to sort correctly. Requires a protocol decision: confirm server always emits opaque non-numeric identifiers, or change the ordering strategy (e.g. zero-pad, `CAST`, or switch to `serverSequence Long` once populated).

- `fallbackToDestructiveMigration(true)` coexists with all explicit migrations — pre-existing. If a future migration is missing, Room silently drops all user data instead of crashing. Consider removing or scoping to dev-only builds after all migration paths are verified.
