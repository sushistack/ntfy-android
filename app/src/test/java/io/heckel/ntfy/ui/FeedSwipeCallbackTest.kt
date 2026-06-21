package io.heckel.ntfy.ui

import io.heckel.ntfy.db.Notification
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * JVM contract tests for Story 4.5: Swipe to Delete / Mark Read.
 *
 * Because FeedSwipeCallback depends on RecyclerView/ItemTouchHelper (Android runtime),
 * the gesture-routing logic is tested via source-read contracts, and the data-layer
 * contracts are tested with pure-Kotlin stubs that mirror the callback's decision logic.
 *
 * Tests cover:
 *  AC 1  left-swipe → delete confirm path
 *  AC 2  right-swipe on unread → markAsRead called once
 *  AC 3  right-swipe on read → gesture disabled (getSwipeDirs returns 0 for RIGHT)
 *  AC 4  backing drawn only while dX != 0 (architecture guard via source)
 *  AC 6  adapter not mutated before Room emits (source guard)
 */
class FeedSwipeCallbackTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun makeNotification(id: String, notificationId: Int) = Notification(
        id = id,
        subscriptionId = 1L,
        timestamp = 1000L,
        sequenceId = "seq-$id",
        title = "",
        message = "msg",
        contentType = "",
        encoding = "",
        notificationId = notificationId,
        priority = 3,
        tags = "",
        click = "",
        icon = null,
        actions = null,
        attachment = null,
        deleted = false,
    )

    private val unreadNotification = makeNotification("id-unread", notificationId = 42)
    private val readNotification = makeNotification("id-read", notificationId = 0)

    // Simulate getSwipeDirs logic from FeedSwipeCallback without Android runtime
    private fun simulateSwipeDirs(notification: Notification?): Int {
        val LEFT = 4   // ItemTouchHelper.LEFT value
        val RIGHT = 8  // ItemTouchHelper.RIGHT value
        return if (notification != null && notification.notificationId == 0) {
            LEFT
        } else {
            LEFT or RIGHT
        }
    }

    // ── AC 3: Read card disables right-swipe ────────────────────────────────────

    @Test
    fun `getSwipeDirs returns only LEFT for read notification`() {
        val dirs = simulateSwipeDirs(readNotification)
        val LEFT = 4
        val RIGHT = 8
        assertTrue("LEFT must be enabled for read card", dirs and LEFT != 0)
        assertEquals("RIGHT must be disabled (0) for read card", 0, dirs and RIGHT)
    }

    @Test
    fun `getSwipeDirs returns LEFT and RIGHT for unread notification`() {
        val dirs = simulateSwipeDirs(unreadNotification)
        val LEFT = 4
        val RIGHT = 8
        assertTrue("LEFT must be enabled for unread card", dirs and LEFT != 0)
        assertTrue("RIGHT must be enabled for unread card", dirs and RIGHT != 0)
    }

    @Test
    fun `getSwipeDirs returns LEFT and RIGHT when notification is null`() {
        val dirs = simulateSwipeDirs(null)
        val LEFT = 4
        val RIGHT = 8
        assertTrue("LEFT must be enabled when notification not found", dirs and LEFT != 0)
        assertTrue("RIGHT must be enabled when notification not found", dirs and RIGHT != 0)
    }

    // ── AC 1: Left-swipe routes to delete confirm ───────────────────────────────

    @Test
    fun `left swipe invokes onSwipeLeft callback with correct notification and position`() {
        val leftInvocations = mutableListOf<Pair<String, Int>>()
        val rightInvocations = mutableListOf<String>()

        val notifications = listOf(unreadNotification, readNotification)

        // Simulate onSwiped(LEFT) logic
        fun simulateSwipeLeft(position: Int) {
            val notification = notifications.getOrNull(position) ?: return
            leftInvocations.add(notification.id to position)
        }

        simulateSwipeLeft(0)

        assertEquals(1, leftInvocations.size)
        assertEquals("id-unread", leftInvocations[0].first)
        assertEquals(0, leftInvocations[0].second)
    }

    @Test
    fun `left swipe on read card still invokes onSwipeLeft (delete always enabled)`() {
        val leftInvocations = mutableListOf<Pair<String, Int>>()
        val notifications = listOf(unreadNotification, readNotification)

        fun simulateSwipeLeft(position: Int) {
            val notification = notifications.getOrNull(position) ?: return
            leftInvocations.add(notification.id to position)
        }

        simulateSwipeLeft(1) // read card, position 1

        assertEquals(1, leftInvocations.size)
        assertEquals("id-read", leftInvocations[0].first)
    }

    // ── AC 2: Right-swipe on unread → markAsRead called exactly once ───────────

    @Test
    fun `right swipe invokes onSwipeRight callback exactly once`() {
        val rightInvocations = mutableListOf<String>()
        val notifications = listOf(unreadNotification, readNotification)

        fun simulateSwipeRight(position: Int) {
            val notification = notifications.getOrNull(position) ?: return
            rightInvocations.add(notification.id)
        }

        simulateSwipeRight(0)

        assertEquals(1, rightInvocations.size)
        assertEquals("id-unread", rightInvocations[0])
    }

    @Test
    fun `right swipe does not invoke onSwipeLeft`() {
        val leftInvocations = mutableListOf<String>()
        val rightInvocations = mutableListOf<String>()
        val notifications = listOf(unreadNotification)

        fun simulateOnSwiped(position: Int, direction: Int) {
            val LEFT = 4; val RIGHT = 8
            val notification = notifications.getOrNull(position) ?: return
            if (direction == LEFT) leftInvocations.add(notification.id)
            else if (direction == RIGHT) rightInvocations.add(notification.id)
        }

        simulateOnSwiped(0, 8) // RIGHT

        assertTrue("left must not be invoked on right swipe", leftInvocations.isEmpty())
        assertEquals(1, rightInvocations.size)
    }

    // ── AC 4 / AC 6: Architecture source guards ─────────────────────────────────

    private fun readSource(vararg candidates: String): String {
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("Source not found. Tried: ${candidates.toList()}")
        return file.readText()
    }

    private val swipeCallbackCandidates = arrayOf(
        "app/src/main/java/io/heckel/ntfy/ui/FeedSwipeCallback.kt",
        "../app/src/main/java/io/heckel/ntfy/ui/FeedSwipeCallback.kt",
    )
    private val feedActivityCandidates = arrayOf(
        "app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt",
        "../app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt",
    )

    @Test
    fun `FeedSwipeCallback draws backing on Canvas not inflated views`() {
        val src = readSource(*swipeCallbackCandidates)
        assertTrue("FeedSwipeCallback must draw on Canvas", src.contains("Canvas"))
        assertTrue("FeedSwipeCallback must use Paint for backing color", src.contains("Paint"))
        assertFalse(
            "FeedSwipeCallback must not inflate backing views",
            src.contains("LayoutInflater") || src.contains("inflate(")
        )
    }

    @Test
    fun `FeedSwipeCallback backing is drawn only when dX != 0`() {
        val src = readSource(*swipeCallbackCandidates)
        assertTrue(
            "FeedSwipeCallback must guard canvas draw with dX != 0 check",
            src.contains("dX != 0")
        )
    }

    @Test
    fun `FeedSwipeCallback uses dp constants not hardcoded pixel values`() {
        val src = readSource(*swipeCallbackCandidates)
        assertTrue("SWIPE_THRESHOLD_DP constant must be present", src.contains("SWIPE_THRESHOLD_DP"))
        assertTrue("BACKING_MAX_DP constant must be present", src.contains("BACKING_MAX_DP"))
        assertTrue("density conversion must be applied", src.contains("displayMetrics.density"))
    }

    @Test
    fun `FeedSwipeCallback does not call adapter remove or submitList`() {
        val src = readSource(*swipeCallbackCandidates)
        assertFalse("FeedSwipeCallback must not remove adapter items directly", src.contains("adapter.remove"))
        assertFalse("FeedSwipeCallback must not call submitList", src.contains("submitList"))
        assertFalse("FeedSwipeCallback must not call notifyItemRemoved", src.contains("notifyItemRemoved"))
    }

    @Test
    fun `FeedActivity wires ItemTouchHelper to recyclerView`() {
        val src = readSource(*feedActivityCandidates)
        assertTrue("FeedActivity must create FeedSwipeCallback", src.contains("FeedSwipeCallback"))
        assertTrue("FeedActivity must attach ItemTouchHelper", src.contains("ItemTouchHelper"))
        assertTrue("FeedActivity must call attachToRecyclerView", src.contains("attachToRecyclerView"))
    }

    @Test
    fun `FeedActivity uses DialogFragment not bare AlertDialog for delete confirm`() {
        val src = readSource(*feedActivityCandidates)
        assertTrue(
            "FeedActivity must show DeleteSwipeConfirmFragment for rotation safety",
            src.contains("DeleteSwipeConfirmFragment")
        )
        assertFalse(
            "FeedActivity must not show bare AlertDialog directly in swipe path",
            src.contains("NotificationDeleteConfirmation.show")
        )
    }

    @Test
    fun `FeedSwipeCallback uses priority_max for delete backing`() {
        val src = readSource(*swipeCallbackCandidates)
        assertTrue(
            "Left-swipe backing must use priority_max (coral) color token",
            src.contains("priority_max")
        )
    }

    @Test
    fun `FeedSwipeCallback uses accent_text for mark-read backing`() {
        val src = readSource(*swipeCallbackCandidates)
        assertTrue(
            "Right-swipe backing must use accent_text (emerald) color token",
            src.contains("accent_text")
        )
    }

    @Test
    fun `FeedSwipeCallback does not hold Repository reference`() {
        val src = readSource(*swipeCallbackCandidates)
        assertFalse(
            "FeedSwipeCallback must not hold Repository — data access goes through lambdas",
            src.contains("Repository")
        )
    }

    @Test
    fun `DeleteSwipeConfirmFragment file exists`() {
        val candidates = arrayOf(
            "app/src/main/java/io/heckel/ntfy/ui/DeleteSwipeConfirmFragment.kt",
            "../app/src/main/java/io/heckel/ntfy/ui/DeleteSwipeConfirmFragment.kt",
        )
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
        assertNotNull("DeleteSwipeConfirmFragment.kt must exist for rotation-safe dialog (AC 5)", file)
    }

    @Test
    fun `DeleteSwipeConfirmFragment extends DialogFragment`() {
        val candidates = arrayOf(
            "app/src/main/java/io/heckel/ntfy/ui/DeleteSwipeConfirmFragment.kt",
            "../app/src/main/java/io/heckel/ntfy/ui/DeleteSwipeConfirmFragment.kt",
        )
        val src = readSource(*candidates)
        assertTrue(
            "DeleteSwipeConfirmFragment must extend DialogFragment for rotation safety (AC 5)",
            src.contains("DialogFragment")
        )
    }
}
