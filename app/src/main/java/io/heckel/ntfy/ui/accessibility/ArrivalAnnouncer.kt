package io.heckel.ntfy.ui.accessibility

import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import io.heckel.ntfy.R

/**
 * Emits a single Android accessibility live-region announcement for a batch of
 * newly arrived notifications.
 *
 * **Ownership rule:** this helper belongs at the host/feed level.  Card binding
 * is too low-level — a recycled holder rebind would produce spurious duplicate
 * announcements.  The host (e.g. DetailActivity, future Feed fragment) calls
 * [announceArrival] once per insertion batch after [androidx.recyclerview.widget.ListAdapter]
 * has committed the diff and the feed has scrolled to the top-most new card.
 *
 * **What NOT to announce:**
 * - Skeleton / loading placeholders
 * - Initial history loads
 * - Pagination rows
 * - Deep-link highlight binds
 * - RecyclerView rebinds / scroll-off-screen / scroll-back
 *
 * @param anchorView  any visible View in the same window used to dispatch the
 *                    AccessibilityEvent (typically the RecyclerView or its parent).
 */
object ArrivalAnnouncer {

    /**
     * Announces that [count] new notifications arrived in this batch.
     *
     * Must be called on the main thread.
     *
     * @param anchorView  the view used to dispatch the announcement
     * @param count       number of genuinely new notifications in this batch (must be ≥ 1)
     */
    fun announceArrival(anchorView: View, count: Int) {
        require(count >= 1) { "announceArrival called with count=$count — must be ≥ 1" }

        val context = anchorView.context
        val text = context.resources.getQuantityString(
            R.plurals.feed_new_notifications_arrival,
            count,
            count,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            anchorView.announceForAccessibility(text)
        } else {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                className = anchorView.javaClass.name
                packageName = context.packageName
                this.text.add(text)
            }
            anchorView.parent?.requestSendAccessibilityEvent(anchorView, event)
        }
    }

    /**
     * Pure decision helper: returns true when an arrival announcement should be
     * emitted for a given batch.
     *
     * Callers use this before calling [announceArrival] to avoid dispatching when
     * the batch is an initial load or a pagination append (not a genuine arrival).
     *
     * @param genuinelyNewIds  IDs the host has verified are newly arrived (not
     *                         already in the list before the diff)
     */
    fun shouldAnnounce(genuinelyNewIds: Set<String>): Boolean = genuinelyNewIds.isNotEmpty()
}
