package io.heckel.ntfy.ui

import io.heckel.ntfy.db.Attachment
import io.heckel.ntfy.db.Notification

/**
 * Host-side callbacks for MessageCardBinder interactions.
 * Keeps the binder free of Activity/Repository/CoroutineScope references.
 */
interface MessageCardActions {
    /**
     * Returns true if the click was consumed by selection/action-mode logic so the binder
     * knows to skip the tap-to-read dispatch (AC 5 of Story 2-5).
     */
    fun onClick(notification: Notification): Boolean
    fun onLongClick(notification: Notification)
    fun onDownloadAttachment(notification: Notification)
    fun onCancelDownload(notification: Notification)
    fun onDeleteAttachment(notification: Notification, attachment: Attachment): Boolean

    /** Called when the X button is tapped; host owns confirmation and deletion. */
    fun onDeleteRequested(notification: Notification)

    /**
     * Called when the non-interactive card surface is tapped for an unread notification.
     * Null means this host does not support tap-to-read (e.g., pending/optimistic cards).
     */
    val onMarkRead: ((notification: Notification) -> Unit)?
        get() = null

    // ── Optimistic-send callbacks (Story 4.9) ──────────────────────────────────

    /** Called when the Retry button is tapped on an error-state optimistic card. */
    fun onRetryRequested(localId: String) {}

    /**
     * Called when the X button is tapped on a pending/error optimistic card.
     * Host shows confirmation dialog; on confirm removes from outbox and cancels job.
     */
    fun onDiscardRequested(localId: String) {}
}
