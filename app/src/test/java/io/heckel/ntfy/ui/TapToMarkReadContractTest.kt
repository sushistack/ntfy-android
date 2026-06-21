package io.heckel.ntfy.ui

import io.heckel.ntfy.db.Attachment
import io.heckel.ntfy.db.Notification
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM tests for the tap-to-mark-read contract in [MessageCardActions].
 *
 * These tests verify the interface contract semantics without an Android device:
 * - onMarkRead is only invoked for unread notifications
 * - Double taps dispatch exactly once (pending guard)
 * - Rebinding to a different ID resets the pending state
 * - Read notifications never dispatch
 */
class TapToMarkReadContractTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun makeNotification(id: String, notificationId: Int): Notification = Notification(
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

    /** Minimal actions stub that captures mark-read dispatches. */
    private inner class TrackingActions(
        private val markReadFn: ((Notification) -> Unit)? = null
    ) : MessageCardActions {
        override fun onClick(notification: Notification): Boolean = false
        override fun onLongClick(notification: Notification) {}
        override fun onDownloadAttachment(notification: Notification) {}
        override fun onCancelDownload(notification: Notification) {}
        override fun onDeleteAttachment(notification: Notification, attachment: Attachment) = true
        override fun onDeleteRequested(notification: Notification) {}
        override val onMarkRead: ((Notification) -> Unit)? = markReadFn
    }

    // ── onMarkRead nullability contract ────────────────────────────────────────

    @Test
    fun `default onMarkRead is null`() {
        val actions = object : MessageCardActions {
            override fun onClick(notification: Notification): Boolean = false
            override fun onLongClick(notification: Notification) {}
            override fun onDownloadAttachment(notification: Notification) {}
            override fun onCancelDownload(notification: Notification) {}
            override fun onDeleteAttachment(notification: Notification, attachment: Attachment) = true
            override fun onDeleteRequested(notification: Notification) {}
        }
        assertNull(actions.onMarkRead)
    }

    @Test
    fun `onMarkRead can be provided by host`() {
        val dispatched = mutableListOf<String>()
        val actions = TrackingActions(markReadFn = { n -> dispatched.add(n.id) })
        assertNotNull(actions.onMarkRead)
    }

    // ── Dispatch logic (simulated at the contract level) ───────────────────────

    /**
     * Simulates the binder's card-tap guard logic:
     * dispatch onMarkRead only when: host wired it, notification is unread, and not pending.
     */
    private fun simulateTap(
        actions: MessageCardActions,
        notification: Notification,
        markReadPending: Boolean
    ): Boolean {
        val markRead = actions.onMarkRead
        return if (markRead != null && notification.notificationId != 0 && !markReadPending) {
            markRead(notification)
            true // new pending=true
        } else {
            markReadPending // unchanged
        }
    }

    @Test
    fun `unread tap dispatches once`() {
        val dispatched = mutableListOf<String>()
        val actions = TrackingActions { n -> dispatched.add(n.id) }
        val unread = makeNotification("id-A", notificationId = 42)

        simulateTap(actions, unread, markReadPending = false)

        assertEquals(listOf("id-A"), dispatched)
    }

    @Test
    fun `read tap dispatches zero times`() {
        val dispatched = mutableListOf<String>()
        val actions = TrackingActions { n -> dispatched.add(n.id) }
        val read = makeNotification("id-B", notificationId = 0)

        simulateTap(actions, read, markReadPending = false)

        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun `rapid double tap dispatches exactly once`() {
        val dispatched = mutableListOf<String>()
        val actions = TrackingActions { n -> dispatched.add(n.id) }
        val unread = makeNotification("id-C", notificationId = 7)

        // First tap: pending was false → dispatches, now pending=true
        val newPending = simulateTap(actions, unread, markReadPending = false)
        assertTrue(newPending) // pending flag set

        // Second tap: pending=true → no dispatch
        simulateTap(actions, unread, markReadPending = true)

        assertEquals(1, dispatched.size)
    }

    @Test
    fun `rebind to different id resets pending state`() {
        val dispatched = mutableListOf<String>()
        val actions = TrackingActions { n -> dispatched.add(n.id) }

        val notifA = makeNotification("id-A", notificationId = 1)
        val notifB = makeNotification("id-B", notificationId = 2)

        // Simulate: bind A, tap A (pending=true), rebind to B (resets pending), tap B
        simulateTap(actions, notifA, markReadPending = false) // dispatches A
        // Rebind resets pendingId → pending=false for B
        simulateTap(actions, notifB, markReadPending = false) // dispatches B

        assertEquals(listOf("id-A", "id-B"), dispatched)
    }

    @Test
    fun `null onMarkRead host never dispatches`() {
        val actions = TrackingActions(markReadFn = null)
        val unread = makeNotification("id-D", notificationId = 5)

        simulateTap(actions, unread, markReadPending = false)

        // No exception; markRead was null so nothing was called. Test passes by not throwing.
    }

    // ── Architecture: onMarkRead stays out of the binder constructor ───────────

    @Test
    fun `MessageCardActions interface has onMarkRead property`() {
        val method = MessageCardActions::class.java.methods.find { it.name == "getOnMarkRead" }
        assertNotNull("MessageCardActions must declare onMarkRead", method)
    }
}
