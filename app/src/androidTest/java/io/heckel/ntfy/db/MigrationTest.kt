package io.heckel.ntfy.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        Database::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate18To19_preservesDataAndAddsNullServerSequence() {
        // Create version-18 database with representative rows
        helper.createDatabase(testDb, 18).use { db ->
            // Insert a subscription (required for FK-like integrity)
            db.execSQL(
                """INSERT INTO Subscription (id, baseUrl, topic, instant, mutedUntil, minPriority,
                   autoDelete, insistent, lastNotificationId, icon, upAppId, upConnectorToken,
                   displayName, dedicatedChannels)
                   VALUES (1, 'https://ntfy.sh', 'test', 0, 0, 0, -1, -1, NULL, NULL, NULL, NULL, NULL, 0)"""
            )
            // Insert a notification with all representative fields populated
            db.execSQL(
                """INSERT INTO Notification (id, subscriptionId, timestamp, sequenceId, title,
                   message, contentType, encoding, notificationId, priority, tags, click,
                   actions, deleted, icon_url, icon_contentUri,
                   attachment_name, attachment_type, attachment_size, attachment_expires,
                   attachment_url, attachment_contentUri, attachment_progress)
                   VALUES ('msg-abc123', 1, 1700000000, 'seq-abc123', 'Test Title',
                   'Test message body', '', '', 42, 3, 'tag1,tag2', '',
                   NULL, 0, NULL, NULL,
                   NULL, NULL, NULL, NULL,
                   NULL, NULL, NULL)"""
            )
            // Insert a notification with nullable embedded fields as null (minimal row)
            db.execSQL(
                """INSERT INTO Notification (id, subscriptionId, timestamp, sequenceId, title,
                   message, contentType, encoding, notificationId, priority, tags, click,
                   actions, deleted, icon_url, icon_contentUri,
                   attachment_name, attachment_type, attachment_size, attachment_expires,
                   attachment_url, attachment_contentUri, attachment_progress)
                   VALUES ('msg-min001', 1, 1700000001, 'seq-min001', '', 'Minimal', '', '',
                   0, 3, '', '', NULL, 1, NULL, NULL,
                   NULL, NULL, NULL, NULL, NULL, NULL, NULL)"""
            )
        }

        // Run migration and validate schema against version 19
        helper.runMigrationsAndValidate(testDb, 19, true, Database.MIGRATION_18_19).use { db ->
            // Verify row count preserved
            val cursor = db.query("SELECT COUNT(*) FROM Notification")
            cursor.moveToFirst()
            assertEquals("Row count must be preserved after migration", 2, cursor.getInt(0))
            cursor.close()

            // Verify full representative row: composite PK, sequenceId, and serverSequence IS NULL
            val row = db.query(
                "SELECT id, subscriptionId, timestamp, sequenceId, title, message, priority, tags, deleted, serverSequence FROM Notification WHERE id = 'msg-abc123'"
            )
            row.moveToFirst()
            assertEquals("msg-abc123", row.getString(row.getColumnIndexOrThrow("id")))
            assertEquals(1L, row.getLong(row.getColumnIndexOrThrow("subscriptionId")))
            assertEquals(1700000000L, row.getLong(row.getColumnIndexOrThrow("timestamp")))
            assertEquals("seq-abc123", row.getString(row.getColumnIndexOrThrow("sequenceId")))
            assertEquals("Test Title", row.getString(row.getColumnIndexOrThrow("title")))
            assertEquals("Test message body", row.getString(row.getColumnIndexOrThrow("message")))
            assertEquals(3, row.getInt(row.getColumnIndexOrThrow("priority")))
            assertEquals("tag1,tag2", row.getString(row.getColumnIndexOrThrow("tags")))
            assertEquals(0, row.getInt(row.getColumnIndexOrThrow("deleted")))
            // serverSequence must be NULL for all legacy rows
            assertNull(
                "serverSequence must be NULL for legacy migrated rows",
                if (row.isNull(row.getColumnIndexOrThrow("serverSequence"))) null else row.getLong(row.getColumnIndexOrThrow("serverSequence"))
            )
            row.close()

            // Verify minimal row as well
            val minRow = db.query(
                "SELECT id, subscriptionId, sequenceId, deleted, serverSequence FROM Notification WHERE id = 'msg-min001'"
            )
            minRow.moveToFirst()
            assertEquals("msg-min001", minRow.getString(minRow.getColumnIndexOrThrow("id")))
            assertEquals("seq-min001", minRow.getString(minRow.getColumnIndexOrThrow("sequenceId")))
            assertEquals(1, minRow.getInt(minRow.getColumnIndexOrThrow("deleted")))
            assertNull(
                "serverSequence must be NULL for legacy migrated rows",
                if (minRow.isNull(minRow.getColumnIndexOrThrow("serverSequence"))) null else minRow.getLong(minRow.getColumnIndexOrThrow("serverSequence"))
            )
            minRow.close()
        }
    }
}
