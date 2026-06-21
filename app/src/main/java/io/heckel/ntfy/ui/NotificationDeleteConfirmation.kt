package io.heckel.ntfy.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.heckel.ntfy.R

/**
 * Reusable host-owned presenter for per-notification delete confirmation.
 *
 * Accepts a lifecycle-safe host Context and a one-shot confirmation callback.
 * Has no knowledge of DetailAdapter, Repository, or lifecycle scope; the host wires
 * those after the callback fires.
 *
 * Reusable by Story 4.5 swipe-delete without coupling to DetailAdapter.
 */
object NotificationDeleteConfirmation {

    /**
     * Shows a Material confirmation dialog styled by AppTheme (surface, text, radius, shadow).
     * The destructive Delete action is tinted with [R.color.priority_max].
     *
     * @param context  A lifecycle-safe Context (Activity or ContextWrapper).
     * @param onConfirmed  Invoked exactly once if the user taps Delete; never invoked on Cancel,
     *                     Back, outside-tap, or if the host is recreated before confirmation.
     * @return The created dialog (for testing/cancellation if needed).
     */
    fun show(context: Context, onConfirmed: () -> Unit): AlertDialog {
        var confirmed = false
        val dialog = MaterialAlertDialogBuilder(context)
            .setMessage(R.string.notification_delete_dialog_message)
            .setNegativeButton(R.string.notification_delete_dialog_cancel, null)
            .setPositiveButton(R.string.notification_delete_dialog_delete) { _, _ ->
                if (!confirmed) {
                    confirmed = true
                    onConfirmed()
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(context, R.color.priority_max))
        }
        dialog.show()
        return dialog
    }
}
