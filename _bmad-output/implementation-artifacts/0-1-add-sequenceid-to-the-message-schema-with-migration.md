# Story 0.1: Add Server Ordering Sequence to the Message Schema with Migration

Status: ready-for-dev

## Story

As a developer,
I want the Room notification entity to store a nullable server ordering sequence with a safe migration,
so that a later feed query can order by an authoritative sequence rather than wall-clock time without breaking existing notification update semantics.

## Acceptance Criteria

1. **Given** an existing populated version-18 database  
   **When** the app opens it after this change  
   **Then** Room migrates it to version 19 without destructive fallback or loss/change of any existing `Subscription`, `Notification`, user, certificate, header, or log data.

2. **Given** the existing `Notification.sequenceId: String` column  
   **When** the new ordering field is added  
   **Then** the existing column remains unchanged because it is an opaque ntfy sequence identifier used to update, clear, delete, group, back up, and derive Android notification IDs  
   **And** the new nullable `Long` property/column is named `serverSequence` to avoid a semantic and schema collision.

3. **Given** a legacy notification row migrated from schema 18  
   **When** the migration completes  
   **Then** `serverSequence` is `NULL`  
   **And** every pre-existing column value is preserved exactly.

4. **Given** the version-18 exported Room schema and a populated test database  
   **When** `MIGRATION_18_19` is run with `MigrationTestHelper`  
   **Then** Room validates the resulting schema against version 19  
   **And** the test asserts representative legacy values remain intact and `serverSequence IS NULL`.

5. **Given** application startup  
   **When** Room builds `AppDatabase`  
   **Then** `MIGRATION_18_19` is registered explicitly  
   **And** the migration is accessible to the instrumentation test without duplicating its SQL.

6. **Given** this story is limited to schema foundation  
   **When** it is complete  
   **Then** no receive path, parser, DAO ordering, repository behavior, UI behavior, backup format, or notification update/delete behavior consumes `serverSequence`.

7. **Given** Room schema export is already configured  
   **When** the project is built after the version bump  
   **Then** `app/schemas/io.heckel.ntfy.db.Database/19.json` is generated and committed.

## Tasks / Subtasks

- [ ] Add the separate nullable ordering field (AC: 2, 3, 6)
  - [ ] Add `@ColumnInfo(name = "serverSequence") val serverSequence: Long?` to `io.heckel.ntfy.db.Notification`.
  - [ ] Add the parameter to the Room-compatible secondary constructor.
  - [ ] Update all direct `Notification(...)` construction sites to pass `serverSequence = null`; do not infer it from `timestamp`, `id`, or the existing string `sequenceId`.
  - [ ] Preserve `Notification.sequenceId: String` and every consumer unchanged.

- [ ] Add and register the migration (AC: 1, 3, 5)
  - [ ] Increment `@Database(version = 18)` to `19`.
  - [ ] Add `MIGRATION_18_19` using `ALTER TABLE Notification ADD COLUMN serverSequence INTEGER`.
  - [ ] Register it in `Database.getInstance()`.
  - [ ] Expose migration visibility narrowly enough for `androidTest` to reference the production object; do not copy migration SQL into the test.

- [ ] Add migration-test infrastructure (AC: 4)
  - [ ] Add `androidTestImplementation "androidx.room:room-testing:2.8.4"`.
  - [ ] Add `app/schemas` to the Android instrumentation-test assets source set if the current Room/testing setup requires it.
  - [ ] Create an instrumentation migration test under `app/src/androidTest/java/io/heckel/ntfy/db/`.
  - [ ] Create a version-18 database with representative populated rows, migrate through the production migration, and validate version 19.
  - [ ] Assert row count, composite primary key values, representative nullable/embedded fields, existing string `sequenceId`, and `serverSequence == null`.

- [ ] Generate and verify the schema artifact (AC: 7)
  - [ ] Run the relevant KSP/assemble task to generate schema 19.
  - [ ] Confirm the new column has SQLite `INTEGER` affinity and is nullable with no default.
  - [ ] Commit `19.json`; do not edit generated schema JSON by hand.

- [ ] Run regression checks (AC: 1, 6)
  - [ ] Run the migration instrumentation test on an emulator/device.
  - [ ] Run existing unit/instrumentation checks available for the touched variant.
  - [ ] Compile both `play` and `fdroid` variants affected by shared `main` code.

## Dev Notes

### Critical Existing-Code Reality

The planning artifact's original wording says to add `sequenceId (nullable Long)`, but that name and meaning already exist in production:

- `Notification.sequenceId` is a non-null `String` Room column.
- Schema 18 added it in `MIGRATION_17_18`, backfilling legacy rows from `Notification.id`.
- `Message.sequenceId` maps the official JSON field `sequence_id`, whose documented type is string.
- The value is used for update/clear/delete grouping, notification-ID derivation, Firebase handling, polling behavior, repository methods, and backup/restore.

Therefore, replacing or changing the type/nullability of the existing column is prohibited. It would be a destructive semantic migration and would break current behavior. This story uses the distinct name `serverSequence` for the requested nullable numeric ordering value.

There is also an upstream contract gap: official ntfy documentation defines `sequence_id` as an opaque string for notification update sequences, not a monotonic numeric feed-order key. Story 0.2 must identify a real authoritative numeric source before populating `serverSequence`; it must not parse the existing `sequence_id`, message ID, or timestamp into this field. If no such source exists, Epic 0's ordering contract must be corrected before Story 0.2 implementation.

### Current State and Required Changes

#### `app/src/main/java/io/heckel/ntfy/db/Database.kt`

- Current state: contains all Room entities, database version 18, migrations 1→18, DAOs, and `Notification.sequenceId: String`.
- Change: add `Notification.serverSequence: Long?`, bump to 19, add/register `MIGRATION_18_19`.
- Preserve:
  - Composite `Notification` primary key `(id, subscriptionId)`.
  - Existing `sequenceId TEXT NOT NULL`.
  - Existing DAO SQL and timestamp ordering in this story.
  - Every migration from 1→18.
  - Existing entity names, table names, indices, and embedded-column shapes.

#### Direct `Notification` construction sites

Room entity constructor changes will affect parser, Firebase, backup restore, and tests/fixtures. Add `serverSequence = null` mechanically. Do not alter data flow yet.

Likely sites include:

- `app/src/main/java/io/heckel/ntfy/msg/NotificationParser.kt`
- `app/src/play/java/io/heckel/ntfy/firebase/FirebaseService.kt`
- `app/src/main/java/io/heckel/ntfy/backup/Backuper.kt`

Kotlin `copy(...)` calls need no changes because the new property is retained automatically.

### Migration Design

Use a manual migration consistent with the project:

```sql
ALTER TABLE Notification ADD COLUMN serverSequence INTEGER
```

Do not specify `NOT NULL`, a synthetic default, or a backfill. SQLite leaves the column `NULL` for all legacy rows, which is the required compatibility behavior.

The project currently calls `fallbackToDestructiveMigration(true)`. This story does not remove that broader behavior, but the explicit 18→19 migration and migration test must prove the normal upgrade path is non-destructive.

### Testing Requirements

- Use AndroidX Room `MigrationTestHelper`, not a hand-built approximation of Room validation.
- Use the committed version-18 schema in `app/schemas/io.heckel.ntfy.db.Database/18.json`.
- Seed enough columns to catch accidental loss or remapping, including:
  - `Notification.id`, `subscriptionId`, `timestamp`
  - existing `sequenceId`
  - title/message/content fields
  - nullable embedded icon/attachment fields
  - deleted/priority state
- Run the production `MIGRATION_18_19`.
- Validate schema 19 and query migrated rows directly to assert preservation and null default.
- Keep this as an instrumentation test because it exercises Android SQLite/Room migration behavior.

### Library / Framework Requirements

- Kotlin 2.2.10, Java 17, Room 2.8.4, KSP, compile/target SDK 36.
- Reuse the existing Room/KSP schema export setup in `app/build.gradle`.
- Add only Room's matching `room-testing:2.8.4` test artifact; no new migration library is needed.

### Scope Boundaries

In scope:

- Room entity shape.
- Database version and explicit migration.
- Constructor compilation fixes with `null`.
- Exported schema 19.
- Migration instrumentation test and minimal test dependency/source-set wiring.

Out of scope:

- Parsing/populating an ordering value.
- Changing official ntfy `sequence_id` handling.
- DAO sort order or feed queries.
- Backfilling a synthetic ordering value.
- UI changes.
- Backup-format changes.
- Renaming/removing the existing `sequenceId`.

### Project Structure Notes

- Keep the entity and migration in the existing monolithic `Database.kt`; do not introduce a new persistence layer in this story.
- Put migration instrumentation tests in the matching `io.heckel.ntfy.db` package.
- Keep generated schemas under the existing canonical folder.
- No previous story exists for Epic 0; current repository and schema history are the source of truth.

### Git Intelligence

The latest relevant commit (`5e3972d6`) introduced the planning artifacts only; it did not change runtime code. Recent history does not establish a new implementation pattern beyond the existing Room migration chain. Preserve the project's manual incremental migration style.

### Latest Technical Information

- Room 2.8.4 is already pinned by the project.
- Android's current Room migration guidance uses exported schemas plus `MigrationTestHelper` and a matching `room-testing` dependency to create an old-version database and run/validate the production migration.
- Official ntfy subscribe API documentation defines `sequence_id` as a string used for updating/deleting notifications, confirming it must not be repurposed as a nullable numeric order column.

### References

- [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 0 / Story 0.1]
- [Source: `_bmad-output/specs/spec-ui-parity/SPEC.md` — CAP-9 and feed-ordering constraint]
- [Source: `_bmad-output/specs/spec-ui-parity/brownfield.md` — data-model verification note]
- [Source: `docs/ui-parity/screens-layout.md` — Feed order]
- [Source: `app/src/main/java/io/heckel/ntfy/db/Database.kt` — `Notification`, database version, migrations, DAOs]
- [Source: `app/src/main/java/io/heckel/ntfy/msg/Message.kt` — `sequence_id` JSON mapping]
- [Source: `app/src/main/java/io/heckel/ntfy/msg/NotificationParser.kt` — existing sequence fallback and persistence]
- [Source: `app/src/main/java/io/heckel/ntfy/backup/Backuper.kt` — sequence backup/restore]
- [Source: `app/src/play/java/io/heckel/ntfy/firebase/FirebaseService.kt` — Firebase sequence handling]
- [Source: `app/build.gradle` — Room version and schema export]
- [Android Room migration guide](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [ntfy subscribe API](https://docs.ntfy.sh/subscribe/api/)
- [ntfy sequence update semantics](https://docs.ntfy.sh/publish/#updating-notifications)

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- Planning conflict resolved defensively: preserved existing opaque string `sequenceId` and specified a separate nullable numeric `serverSequence` column.
- Story 0.2 carries a required protocol-source validation before any ordering value is populated.

### File List

