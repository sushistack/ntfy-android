package io.heckel.ntfy.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.db.*
import io.heckel.ntfy.msg.DownloadManager
import io.heckel.ntfy.msg.DownloadType
import io.heckel.ntfy.util.Log
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import io.heckel.ntfy.util.MarkwonFactory

class DetailAdapter(
    private val activity: Activity,
    private val lifecycleScope: CoroutineScope,
    private val repository: Repository,
    private val onClick: (Notification) -> Unit,
    private val onLongClick: (Notification) -> Unit,
    private val onMarkReadCallback: ((Notification) -> Unit)? = null,
    private val onDeleteRequestCallback: ((Notification) -> Unit)? = null,
) : ListAdapter<Notification, DetailAdapter.DetailViewHolder>(TopicDiffCallback) {

    private val markwon: Markwon = MarkwonFactory.createForMessage(activity)
    val selected = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_detail_item, parent, false)
        val cardActions = buildCardActions(activity, lifecycleScope, repository)
        val binder = MessageCardBinder(view, markwon, cardActions)
        return DetailViewHolder(view, binder, selected)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: DetailViewHolder) {
        super.onViewRecycled(holder)
        holder.reset()
    }

    fun get(position: Int): Notification = getItem(position)

    fun toggleSelection(notificationId: String) {
        if (selected.contains(notificationId)) {
            selected.remove(notificationId)
        } else {
            selected.add(notificationId)
        }
        if (selected.isNotEmpty()) {
            val notificationPosition = currentList.indexOfFirst { it.id == notificationId }
            if (notificationPosition >= 0) notifyItemChanged(notificationPosition)
        }
    }

    private fun buildCardActions(
        activity: Activity,
        lifecycleScope: CoroutineScope,
        repository: Repository,
    ): MessageCardActions = object : MessageCardActions {
        override fun onClick(notification: Notification) = this@DetailAdapter.onClick(notification)
        override fun onLongClick(notification: Notification) = this@DetailAdapter.onLongClick(notification)

        override fun onDownloadAttachment(notification: Notification) {
            val requiresPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
            if (requiresPermission) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE_PERMISSION_FOR_DOWNLOAD)
                return
            }
            DownloadManager.enqueue(activity, notification.id, userAction = true, DownloadType.ATTACHMENT)
        }

        override fun onCancelDownload(notification: Notification) {
            DownloadManager.cancel(activity, notification.id)
        }

        override fun onDeleteAttachment(notification: Notification, attachment: Attachment): Boolean {
            try {
                val contentUri = attachment.contentUri!!.let { android.net.Uri.parse(it) }
                val resolver = activity.applicationContext.contentResolver
                val deleted = resolver.delete(contentUri, null, null) > 0
                if (!deleted) throw Exception("no rows deleted")
                val newAttachment = attachment.copy(contentUri = null, progress = ATTACHMENT_PROGRESS_DELETED)
                val newNotification = notification.copy(attachment = newAttachment)
                GlobalScope.launch(Dispatchers.IO) {
                    repository.updateNotification(newNotification)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete attachment: ${e.message}", e)
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.detail_item_cannot_delete, e.message),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            return true
        }

        override fun onDeleteRequested(notification: Notification) {
            onDeleteRequestCallback?.invoke(notification)
        }

        override val onMarkRead: ((Notification) -> Unit)? = onMarkReadCallback
    }

    class DetailViewHolder(
        itemView: View,
        private val binder: MessageCardBinder,
        private val selected: Set<String>,
    ) : RecyclerView.ViewHolder(itemView) {

        private var notification: Notification? = null

        fun bind(notification: Notification) {
            this.notification = notification
            binder.bind(
                notification = notification,
                topicName = null,
                selected = selected.contains(notification.id),
            )
        }

        fun reset() {
            binder.reset()
        }
    }

    object TopicDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean =
            oldItem == newItem
    }

    companion object {
        const val TAG = "NtfyDetailAdapter"
        const val REQUEST_CODE_WRITE_STORAGE_PERMISSION_FOR_DOWNLOAD = 9876
    }
}
