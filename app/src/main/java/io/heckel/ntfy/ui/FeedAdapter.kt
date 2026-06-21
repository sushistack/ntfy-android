package io.heckel.ntfy.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Attachment
import io.heckel.ntfy.db.Notification
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.msg.DownloadManager
import io.heckel.ntfy.msg.DownloadType
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.ui.card.body.MarkwonCardMarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedAdapter(
    private val activity: Activity,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val repository: Repository,
    private val onDeleteRequestCallback: ((Notification) -> Unit)? = null,
    private val onMarkReadCallback: ((Notification) -> Unit)? = null,
    private val onArrivalConsumedCallback: ((String) -> Unit)? = null,
) : ListAdapter<FeedItem, FeedAdapter.FeedViewHolder>(FeedItemDiffCallback) {

    private val markdownRenderer = MarkwonCardMarkdownRenderer(activity)
    private var deepLinkTargetId: String? = null
    private var newlyArrivedIds: Set<String> = emptySet()

    fun setDeepLinkTargetId(id: String?) {
        deepLinkTargetId = id
    }

    fun setNewlyArrivedIds(ids: Set<String>) {
        newlyArrivedIds = ids
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_detail_item, parent, false)
        val cardActions = buildCardActions()
        val binder = MessageCardBinder(view, markdownRenderer, cardActions)
        return FeedViewHolder(view, binder)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val item = getItem(position)
        val bindState = buildBindState(item.notification.id)
        holder.bind(item, bindState)
    }

    override fun onViewRecycled(holder: FeedViewHolder) {
        super.onViewRecycled(holder)
        holder.reset()
    }

    private fun buildBindState(notificationId: String): CardBindState {
        val isDeepLinkTarget = notificationId == deepLinkTargetId
        val presentation = if (isDeepLinkTarget) {
            CardPresentation.StaticDeepLinkEmphasis(notificationId)
        } else {
            CardPresentation.Normal
        }
        val effect = if (notificationId in newlyArrivedIds) {
            CardEffect.NewArrival(notificationId) {
                newlyArrivedIds = newlyArrivedIds - notificationId
                onArrivalConsumedCallback?.invoke(notificationId)
            }
        } else if (isDeepLinkTarget) {
            CardEffect.DeepLinkPulse(notificationId) { deepLinkTargetId = null }
        } else {
            CardEffect.None
        }
        return CardBindState(presentation = presentation, effect = effect)
    }

    private fun buildCardActions(): MessageCardActions = object : MessageCardActions {
        override fun onClick(notification: Notification): Boolean = false

        override fun onLongClick(notification: Notification) {}

        override fun onDownloadAttachment(notification: Notification) {
            val requiresPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
            if (requiresPermission) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE)
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
                val newAttachment = attachment.copy(contentUri = null, progress = io.heckel.ntfy.db.ATTACHMENT_PROGRESS_DELETED)
                val newNotification = notification.copy(attachment = newAttachment)
                lifecycleScope.launch(Dispatchers.IO) {
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

    class FeedViewHolder(
        itemView: View,
        private val binder: MessageCardBinder,
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: FeedItem, bindState: CardBindState) {
            binder.bind(
                notification = item.notification,
                topicName = item.topicName,
                selected = false,
                bindState = bindState,
            )
        }

        fun reset() {
            binder.reset()
        }
    }

    object FeedItemDiffCallback : DiffUtil.ItemCallback<FeedItem>() {
        override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean =
            oldItem.notification.id == newItem.notification.id

        override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean =
            oldItem == newItem
    }

    companion object {
        const val TAG = "NtfyFeedAdapter"
        const val REQUEST_CODE_WRITE_STORAGE = 9877
    }
}
