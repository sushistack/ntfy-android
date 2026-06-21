package io.heckel.ntfy.ui

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.heckel.ntfy.R

/**
 * Rotation-safe delete confirm dialog for swipe-to-delete in the feed.
 *
 * Survives configuration changes via the Fragment back-stack (AC 5).
 * The host Activity must implement [Listener] to receive the result.
 */
class DeleteSwipeConfirmFragment : DialogFragment() {

    interface Listener {
        fun onSwipeDeleteConfirmed(notificationId: String, position: Int)
        fun onSwipeDeleteCancelled(position: Int)
    }

    // Guards against the NegativeButton callback + onCancel both firing on explicit cancel.
    private var cancelDispatched = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val notificationId = requireArguments().getString(ARG_NOTIFICATION_ID)!!
        val position = requireArguments().getInt(ARG_POSITION)
        val listener = activity as? Listener

        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.notification_delete_dialog_message)
            .setNegativeButton(R.string.notification_delete_dialog_cancel) { _, _ ->
                cancelDispatched = true
                listener?.onSwipeDeleteCancelled(position)
            }
            .setPositiveButton(R.string.notification_delete_dialog_delete) { _, _ ->
                listener?.onSwipeDeleteConfirmed(notificationId, position)
            }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        ?.setTextColor(
                            androidx.core.content.ContextCompat.getColor(
                                requireContext(), R.color.priority_max
                            )
                        )
                }
            }
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        if (cancelDispatched) return  // NegativeButton already dispatched cancel
        val position = requireArguments().getInt(ARG_POSITION)
        (activity as? Listener)?.onSwipeDeleteCancelled(position)
    }

    companion object {
        const val TAG = "DeleteSwipeConfirmFragment"
        private const val ARG_NOTIFICATION_ID = "notificationId"
        private const val ARG_POSITION = "position"

        fun newInstance(notificationId: String, position: Int): DeleteSwipeConfirmFragment {
            return DeleteSwipeConfirmFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NOTIFICATION_ID, notificationId)
                    putInt(ARG_POSITION, position)
                }
            }
        }
    }
}
