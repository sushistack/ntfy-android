package io.heckel.ntfy.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {

    private lateinit var db: Database
    private lateinit var dao: NotificationDao

    private val subscriptionId = 1L

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Database::class.java
        ).allowMainThreadQueries().build()
        dao = db.notificationDao()

        // Insert a subscription so the FK-less Notification rows have a valid subscriptionId context
        db.subscriptionDao().add(
            Subscription(
                id = subscriptionId,
                baseUrl = "https://ntfy.sh",
                topic = "test",
                instant = false,
                mutedUntil = 0L,
                minPriority = 0,
                autoDelete = -1L,
                insistent = -1,
                lastNotificationId = null,
                icon = null,
                upAppId = null,
                upConnectorToken = null,
                displayName = null,
                dedicatedChannels = false
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // Helper to create a minimal active Notification with explicit sequenceId and timestamp
    private fun makeNotification(
        id: String,
        sequenceId: String,
        timestamp: Long,
        deleted: Boolean = false,
        message: String = "msg"
    ) = Notification(
        id = id,
        subscriptionId = subscriptionId,
        timestamp = timestamp,
        sequenceId = sequenceId,
        title = "",
        message = message,
        contentType = "",
        encoding = "",
        notificationId = 0,
        priority = 3,
        tags = "",
        click = "",
        icon = null,
        actions = null,
        attachment = null,
        deleted = deleted
    )

    @Test
    fun listFlow_ordersBy_sequenceId_desc_wins_over_timestamp() = runBlocking {
        // Row A: lower sequence, newer timestamp  → should appear LAST
        // Row B: higher sequence, older timestamp → should appear FIRST
        // Row C: middle sequence, middle timestamp
        val rowA = makeNotification("id-A", sequenceId = "seq-100", timestamp = 3000L)
        val rowB = makeNotification("id-B", sequenceId = "seq-300", timestamp = 1000L)
        val rowC = makeNotification("id-C", sequenceId = "seq-200", timestamp = 2000L)

        dao.add(rowA)
        dao.add(rowB)
        dao.add(rowC)

        val result = dao.listFlow(subscriptionId).first()

        assertEquals(3, result.size)
        assertEquals("id-B", result[0].id) // seq-300 highest
        assertEquals("id-C", result[1].id) // seq-200 middle
        assertEquals("id-A", result[2].id) // seq-100 lowest
    }

    @Test
    fun listFlow_tieBreak_by_timestamp_desc_then_id_desc() = runBlocking {
        // All three rows have the same sequenceId (legacy/fallback scenario)
        val rowX = makeNotification("id-X", sequenceId = "same-seq", timestamp = 1000L)
        val rowY = makeNotification("id-Y", sequenceId = "same-seq", timestamp = 3000L)
        val rowZ = makeNotification("id-Z", sequenceId = "same-seq", timestamp = 2000L)

        dao.add(rowX)
        dao.add(rowY)
        dao.add(rowZ)

        val result = dao.listFlow(subscriptionId).first()

        assertEquals(3, result.size)
        assertEquals("id-Y", result[0].id) // timestamp 3000 → highest
        assertEquals("id-Z", result[1].id) // timestamp 2000
        assertEquals("id-X", result[2].id) // timestamp 1000 → lowest
    }

    @Test
    fun listFlow_excludes_deleted_rows() = runBlocking {
        val active = makeNotification("id-active", sequenceId = "seq-1", timestamp = 1000L)
        val deleted = makeNotification("id-deleted", sequenceId = "seq-2", timestamp = 2000L, deleted = true)

        dao.add(active)
        dao.add(deleted)

        val result = dao.listFlow(subscriptionId).first()

        assertEquals(1, result.size)
        assertEquals("id-active", result[0].id)
        assertFalse(result.any { it.id == "id-deleted" })
    }

    @Test
    fun listFlowFiltered_applies_same_ordering_contract() = runBlocking {
        // Row A matches query but has lower sequence
        // Row B matches query and has higher sequence
        val rowA = makeNotification("id-A", sequenceId = "seq-10", timestamp = 9000L, message = "hello world")
        val rowB = makeNotification("id-B", sequenceId = "seq-20", timestamp = 1000L, message = "hello world")

        dao.add(rowA)
        dao.add(rowB)

        val result = dao.listFlowFiltered(subscriptionId, "hello").first()

        assertEquals(2, result.size)
        assertEquals("id-B", result[0].id) // seq-20 wins despite older timestamp
        assertEquals("id-A", result[1].id)
    }

    @Test
    fun listFlowFiltered_excludes_deleted_rows() = runBlocking {
        val active = makeNotification("id-active", sequenceId = "seq-1", timestamp = 1000L, message = "find me")
        val deleted = makeNotification("id-deleted", sequenceId = "seq-2", timestamp = 2000L, message = "find me", deleted = true)

        dao.add(active)
        dao.add(deleted)

        val result = dao.listFlowFiltered(subscriptionId, "find me").first()

        assertEquals(1, result.size)
        assertEquals("id-active", result[0].id)
    }

    @Test
    fun listFlow_tieBreak_equal_sequence_and_timestamp_orders_by_id_desc() = runBlocking {
        // Same sequence and same timestamp → fall back to id DESC
        val rowA = makeNotification("id-AAA", sequenceId = "same", timestamp = 5000L)
        val rowB = makeNotification("id-ZZZ", sequenceId = "same", timestamp = 5000L)

        dao.add(rowA)
        dao.add(rowB)

        val result = dao.listFlow(subscriptionId).first()

        assertEquals(2, result.size)
        assertEquals("id-ZZZ", result[0].id) // lexicographically greater id comes first
        assertEquals("id-AAA", result[1].id)
    }

    // ── markAsRead(id) ──────────────────────────────────────────────────────────

    @Test
    fun markAsRead_sets_notificationId_to_zero_for_unread_row() {
        val unread = makeNotification("id-unread", sequenceId = "seq-1", timestamp = 1000L)
            .copy(notificationId = 42)
        dao.add(unread)

        dao.markAsRead("id-unread")

        assertEquals(0, dao.get("id-unread")!!.notificationId)
    }

    @Test
    fun markAsRead_does_not_touch_already_read_row() {
        val alreadyRead = makeNotification("id-read", sequenceId = "seq-1", timestamp = 1000L)
            .copy(notificationId = 0)
        dao.add(alreadyRead)

        dao.markAsRead("id-read")

        assertEquals(0, dao.get("id-read")!!.notificationId)
    }

    @Test
    fun markAsRead_does_not_affect_other_rows_with_same_sequence_id() {
        val target = makeNotification("id-target", sequenceId = "shared-seq", timestamp = 1000L)
            .copy(notificationId = 10)
        val other = makeNotification("id-other", sequenceId = "shared-seq", timestamp = 2000L)
            .copy(notificationId = 20)
        dao.add(target)
        dao.add(other)

        dao.markAsRead("id-target")

        assertEquals(0, dao.get("id-target")!!.notificationId)
        assertEquals(20, dao.get("id-other")!!.notificationId) // unchanged
    }

    @Test
    fun markAsRead_does_not_affect_row_with_different_id() {
        val target = makeNotification("id-A", sequenceId = "seq-A", timestamp = 1000L)
            .copy(notificationId = 5)
        val unrelated = makeNotification("id-B", sequenceId = "seq-B", timestamp = 2000L)
            .copy(notificationId = 7)
        dao.add(target)
        dao.add(unrelated)

        dao.markAsRead("id-A")

        assertEquals(0, dao.get("id-A")!!.notificationId)
        assertEquals(7, dao.get("id-B")!!.notificationId) // unchanged
    }
}
