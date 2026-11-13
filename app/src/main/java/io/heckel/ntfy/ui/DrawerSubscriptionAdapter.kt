package io.heckel.ntfy.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.Subscription
import io.heckel.ntfy.firebase.FirebaseMessenger
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.util.displayName
import io.heckel.ntfy.util.topicShortUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ListAdapter for subscription rows in the navigation drawer (Story 4.7).
 *
 * Row anatomy: [active bar] [chat icon] [name] [unread count] [muted indicator?] [⋯ button]
 * The host must implement [DrawerHost] to receive navigation and unsubscribe callbacks.
 */
class DrawerSubscriptionAdapter(
    private val repository: Repository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val messenger: FirebaseMessenger,
    private val host: DrawerHost,
) : ListAdapter<Subscription, DrawerSubscriptionAdapter.ViewHolder>(TopicDiffCallback) {

    /** Active subscription id — rows whose id matches render the accent active-bar. */
    private var activeSubscriptionId: Long = ALL_SUBSCRIPTIONS_ID

    fun setActiveSubscriptionId(id: Long) {
        if (activeSubscriptionId == id) return
        activeSubscriptionId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_drawer_subscription_item, parent, false)
        return ViewHolder(view, repository, lifecycleScope, messenger, host)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), activeSubscriptionId)
    }

    // ──────────────────────────────────────────────────────────────────────
    // ViewHolder
    // ──────────────────────────────────────────────────────────────────────

    class ViewHolder(
        itemView: View,
        private val repository: Repository,
        private val lifecycleScope: LifecycleCoroutineScope,
        private val messenger: FirebaseMessenger,
        private val host: DrawerHost,
    ) : RecyclerView.ViewHolder(itemView) {

        private val context: Context = itemView.context
        private val appBaseUrl: String = context.getString(R.string.app_base_url)

        private val avatarView: TextView = itemView.findViewById(R.id.drawer_item_avatar)
        private val nameView: TextView = itemView.findViewById(R.id.drawer_item_name)
        private val unreadView: TextView = itemView.findViewById(R.id.drawer_item_unread)
        private val mutedView: ImageView = itemView.findViewById(R.id.drawer_item_muted)
        private val overflowBtn: ImageButton = itemView.findViewById(R.id.drawer_item_overflow)

        fun bind(subscription: Subscription, activeSubscriptionId: Long) {
            val isActive = subscription.id == activeSubscriptionId
            val name = displayName(appBaseUrl, subscription)
            itemView.setBackgroundResource(if (isActive) R.drawable.drawer_tile_bg else 0)
            nameView.text = name
            setBold(nameView, isActive)
            nameView.setTextColor(context.getColor(if (isMuted(subscription)) R.color.muted else R.color.text))
            bindAvatar(name, isMuted(subscription))
            bindUnreadCount(subscription)
            bindMutedIndicator(subscription)
            bindClickListeners(subscription)
        }

        /** ntfy-web TopicAvatar: first letter on a 15% tint of a stable per-name colour. */
        private fun bindAvatar(name: String, muted: Boolean) {
            val color = avatarColor(name)
            avatarView.text = if (name.isEmpty()) "" else String(Character.toChars(name.codePointAt(0))).uppercase()
            avatarView.setTextColor(color)
            avatarView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(color, (0.15 * 255).toInt()))
            }
            avatarView.alpha = if (muted) 0.5f else 1f
        }

        private fun bindUnreadCount(subscription: Subscription) {
            val count = subscription.newCount
            if (count <= 0) {
                unreadView.visibility = View.GONE
            } else {
                unreadView.visibility = View.VISIBLE
                unreadView.text = if (count <= 99) count.toString() else "99+"
            }
        }

        private fun bindMutedIndicator(subscription: Subscription) {
            mutedView.visibility = if (isMuted(subscription)) View.VISIBLE else View.GONE
        }

        private fun bindClickListeners(subscription: Subscription) {
            itemView.setOnClickListener {
                host.onSubscriptionRowClick(subscription)
            }

            overflowBtn.setOnClickListener { anchor ->
                showOverflowMenu(anchor, subscription)
            }
        }

        private fun showOverflowMenu(anchor: View, subscription: Subscription) {
            val popup = PopupMenu(context, anchor)
            popup.menuInflater.inflate(R.menu.menu_drawer_subscription_overflow, popup.menu)

            val isMutedNow = isMuted(subscription)
            popup.menu.findItem(R.id.drawer_sub_menu_mute)?.isVisible = !isMutedNow
            popup.menu.findItem(R.id.drawer_sub_menu_unmute)?.isVisible = isMutedNow

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.drawer_sub_menu_mute -> { showMuteDurationDialog(subscription); true }
                    R.id.drawer_sub_menu_unmute -> { doUnmute(subscription); true }
                    R.id.drawer_sub_menu_rename -> { showRenameDialog(subscription); true }
                    R.id.drawer_sub_menu_clear -> { showClearDialog(subscription); true }
                    R.id.drawer_sub_menu_unsubscribe -> { showUnsubscribeDialog(subscription); true }
                    else -> false
                }
            }
            popup.show()
        }

        // ── Mute / Unmute ─────────────────────────────────────────────────

        private fun showMuteDurationDialog(subscription: Subscription) {
            val options = arrayOf(
                context.getString(R.string.notification_dialog_30min),
                context.getString(R.string.notification_dialog_1h),
                context.getString(R.string.notification_dialog_2h),
                context.getString(R.string.notification_dialog_8h),
                context.getString(R.string.notification_dialog_tomorrow),
                context.getString(R.string.notification_dialog_forever),
            )
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.drawer_sub_menu_mute)
                .setItems(options) { _, which ->
                    val mutedUntil = computeMutedUntil(which)
                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.updateSubscription(subscription.copy(mutedUntil = mutedUntil))
                    }
                }
                .show()
        }

        private fun computeMutedUntil(optionIndex: Int): Long {
            return when (optionIndex) {
                0 -> System.currentTimeMillis() / 1000 + 30 * 60
                1 -> System.currentTimeMillis() / 1000 + 60 * 60
                2 -> System.currentTimeMillis() / 1000 + 2 * 60 * 60
                3 -> System.currentTimeMillis() / 1000 + 8 * 60 * 60
                4 -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 8)
                    cal.set(Calendar.MINUTE, 30)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis / 1000
                }
                else -> Repository.MUTED_UNTIL_FOREVER
            }
        }

        private fun doUnmute(subscription: Subscription) {
            lifecycleScope.launch(Dispatchers.IO) {
                repository.updateSubscription(subscription.copy(mutedUntil = Repository.MUTED_UNTIL_SHOW_ALL))
            }
        }

        // ── Rename ────────────────────────────────────────────────────────

        private fun showRenameDialog(subscription: Subscription) {
            val editText = EditText(context).apply {
                setText(subscription.displayName ?: "")
                hint = topicShortUrl(subscription.baseUrl, subscription.topic)
                setSingleLine()
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.drawer_sub_menu_rename)
                .setMessage(R.string.drawer_sub_rename_hint)
                .setView(editText)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val input = editText.text.toString()
                    val newDisplayName = if (input.isBlank()) null else input.trim()
                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.updateSubscription(subscription.copy(displayName = newDisplayName))
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // ── Clear ─────────────────────────────────────────────────────────

        private fun showClearDialog(subscription: Subscription) {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.drawer_sub_clear_dialog_message)
                .setPositiveButton(R.string.drawer_sub_clear_dialog_confirm) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.markAllAsDeleted(subscription.id)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // ── Unsubscribe ───────────────────────────────────────────────────

        private fun showUnsubscribeDialog(subscription: Subscription) {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.drawer_sub_unsubscribe_dialog_message)
                .setPositiveButton(R.string.drawer_sub_unsubscribe_dialog_confirm) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.removeSubscription(subscription)
                        if (subscription.baseUrl == appBaseUrl && BuildConfig.FIREBASE_AVAILABLE) {
                            messenger.unsubscribe(subscription.topic)
                            Log.d(TAG, "Firebase unsubscribed from topic ${subscription.topic}")
                        }
                    }
                    host.onSubscriptionUnsubscribed(subscription)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // DiffUtil
    // ──────────────────────────────────────────────────────────────────────

    object TopicDiffCallback : DiffUtil.ItemCallback<Subscription>() {
        override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription) =
            oldItem == newItem
    }

    // ──────────────────────────────────────────────────────────────────────
    // Host interface — implemented by MainActivity shell
    // ──────────────────────────────────────────────────────────────────────

    interface DrawerHost {
        /** Row tap → navigate to per-topic feed and close drawer. */
        fun onSubscriptionRowClick(subscription: Subscription)

        /** Unsubscribe confirmed → navigate to All feed if the removed topic was active. */
        fun onSubscriptionUnsubscribed(subscription: Subscription)
    }

    companion object {
        private const val TAG = "NtfyDrawerSubAdapter"
    }
}

/** Pretendard regular/bold (the font family's 400/700 faces) — Typeface.DEFAULT* would fall back to Roboto. */
internal fun setBold(view: TextView, bold: Boolean) {
    view.typeface = Typeface.create(ResourcesCompat.getFont(view.context, R.font.font_sans), if (bold) Typeface.BOLD else Typeface.NORMAL)
}

private val AVATAR_COLORS = intArrayOf(
    0xFF3182F6.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(), 0xFFF97316.toInt(),
    0xFF10B981.toInt(), 0xFF0EA5E9.toInt(), 0xFFEF4444.toInt(), 0xFF64748B.toInt(),
)

/** Same hash as ntfy-web Sidebar colorFor (UTF-16 code units), so a topic gets the same colour on both. */
internal fun avatarColor(name: String): Int {
    var h = 0
    for (c in name) h = (h * 31 + c.code) % 997
    return AVATAR_COLORS[h % AVATAR_COLORS.size]
}

private fun isMuted(subscription: Subscription): Boolean {
    val m = subscription.mutedUntil
    return m == Repository.MUTED_UNTIL_FOREVER || (m > 1L && m > System.currentTimeMillis() / 1000)
}
