package io.heckel.ntfy.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Notification
import kotlin.math.abs

/**
 * ItemTouchHelper callback for the feed RecyclerView.
 *
 * Left-swipe  (always):  coral backing → delete-confirm dialog
 * Right-swipe (unread):  emerald backing → markAsRead
 * Right-swipe (read):    gesture disabled — returns 0 from getSwipeDirs
 *
 * Backing colors are painted directly on the Canvas in onChildDraw;
 * no XML views are inflated or added to the hierarchy.
 */
class FeedSwipeCallback(
    private val notificationAt: (position: Int) -> Notification?,
    private val onSwipeLeft: (notification: Notification, position: Int) -> Unit,
    private val onSwipeRight: (notification: Notification) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val notification = notificationAt(viewHolder.absoluteAdapterPosition)
        return when {
            notification == null -> ItemTouchHelper.LEFT          // no data: only delete enabled
            notification.notificationId == 0 -> ItemTouchHelper.LEFT  // read: disable right-swipe
            else -> ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // unread: both directions
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        val thresholdPx = SWIPE_THRESHOLD_DP * viewHolder.itemView.resources.displayMetrics.density
        val width = viewHolder.itemView.width.toFloat()
        return if (width > 0f) thresholdPx / width else 0.4f
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.absoluteAdapterPosition
        val notification = notificationAt(position) ?: return
        if (direction == ItemTouchHelper.LEFT) {
            onSwipeLeft(notification, position)
        } else {
            onSwipeRight(notification)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (dX != 0f && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val colorRes = if (dX < 0) R.color.priority_max else R.color.accent_text
            backingPaint.color = ContextCompat.getColor(itemView.context, colorRes)

            val backingWidth = minOf(abs(dX), BACKING_MAX_DP * itemView.resources.displayMetrics.density)
            if (dX < 0) {
                // Left swipe: backing on the right edge
                c.drawRect(
                    itemView.right - backingWidth,
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat(),
                    backingPaint,
                )
            } else {
                // Right swipe: backing on the left edge
                c.drawRect(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    itemView.left + backingWidth,
                    itemView.bottom.toFloat(),
                    backingPaint,
                )
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    companion object {
        private const val SWIPE_THRESHOLD_DP = 72f
        private const val BACKING_MAX_DP = 96f
    }
}

fun dpToPx(dp: Float, resources: Resources): Float =
    dp * resources.displayMetrics.density
