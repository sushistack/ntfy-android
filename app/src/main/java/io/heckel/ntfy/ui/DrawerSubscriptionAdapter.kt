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
import android.widget.LinearLayout
import android.widget.PopupWindow
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
            val tint = ColorUtils.setAlphaComponent(color, (0.15 * 255).toInt())
            avatarView.text = if (name.isEmpty()) "" else String(Character.toChars(name.codePointAt(0))).uppercase()
            // Web uses the raw palette colour for the letter (as low as 2.2:1 in light mode);
            // nudge only the letter until it reaches 4.5:1 on the tinted circle.
            avatarView.setTextColor(readableOn(color, compositeOver(tint, context.getColor(R.color.surface))))
            avatarView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tint)
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

        // Web-parity menu (ui/Menu.jsx): surface card, 1dp border, radius_sm, real separators.
        // ponytail: hand-built PopupWindow; a PopupMenu can't do separators without blank rows.
        private fun showOverflowMenu(anchor: View, subscription: Subscription) {
            val res = context.resources
            val dp = res.displayMetrics.density
            val isMutedNow = isMuted(subscription)
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(context.getColor(R.color.surface))
                    setStroke(dp.toInt().coerceAtLeast(1), context.getColor(R.color.border))
                    cornerRadius = res.getDimension(R.dimen.radius_sm)
                }
                minimumWidth = (180 * dp).toInt()
            }
            val popup = PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
                elevation = 8 * dp
            }
            fun item(label: Int, action: () -> Unit) {
                list.addView(TextView(context).apply {
                    text = context.getString(label)
                    textSize = 14f
                    typeface = ResourcesCompat.getFont(context, R.font.font_sans)
                    setTextColor(context.getColor(R.color.text))
                    minHeight = (48 * dp).toInt() // touch target
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
                    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                    background = ta.getDrawable(0); ta.recycle()
                    setOnClickListener { popup.dismiss(); action() }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            fun separator() {
                list.addView(View(context).apply { setBackgroundColor(context.getColor(R.color.border)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp.toInt().coerceAtLeast(1)).apply {
                        topMargin = (4 * dp).toInt(); bottomMargin = (4 * dp).toInt()
                    })
            }
            if (isMutedNow) item(R.string.drawer_sub_menu_unmute) { doUnmute(subscription) }
            else item(R.string.drawer_sub_menu_mute) { showMuteDurationDialog(subscription) }
            separator()
            item(R.string.drawer_sub_menu_rename) { showRenameDialog(subscription) }
            item(R.string.drawer_sub_menu_clear) { showClearDialog(subscription) }
            separator()
            item(R.string.drawer_sub_menu_unsubscribe) { showUnsubscribeDialog(subscription) }
            // Below the dots, right edges aligned, so it stays inside the drawer. Gravity.END can't
            // align a WRAP_CONTENT popup (width unknown before show), so measure and offset.
            list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            popup.width = list.measuredWidth
            popup.showAsDropDown(anchor, anchor.width - list.measuredWidth, (4 * dp).toInt())
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

// Pure ARGB math (no android.graphics) so it runs in plain JVM unit tests.
private fun channelLum(c: Int): Double {
    val x = c / 255.0
    return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
}
internal fun luminance(rgb: Int): Double =
    0.2126 * channelLum(rgb shr 16 and 0xFF) + 0.7152 * channelLum(rgb shr 8 and 0xFF) + 0.0722 * channelLum(rgb and 0xFF)
internal fun contrast(a: Int, b: Int): Double {
    val la = luminance(a); val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
private fun mix(a: Int, b: Int, t: Double): Int {
    fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
/** Opaque result of [fg] (with alpha) drawn over opaque [bg]. */
internal fun compositeOver(fg: Int, bg: Int): Int = mix(bg, fg or (0xFF shl 24), (fg ushr 24) / 255.0)
/** [fg] moved toward black or white (whichever [bg] contrasts with) until it hits [min]. */
internal fun readableOn(fg: Int, bg: Int, min: Double = 4.5): Int {
    val target = if (luminance(bg) > 0.18) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    var t = 0.0
    var c = fg or (0xFF shl 24)
    while (contrast(c, bg) < min && t < 1.0) { t += 0.05; c = mix(fg, target, t) }
    return c
}

private fun isMuted(subscription: Subscription): Boolean {
    val m = subscription.mutedUntil
    return m == Repository.MUTED_UNTIL_FOREVER || (m > 1L && m > System.currentTimeMillis() / 1000)
}
