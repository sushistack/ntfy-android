package io.heckel.ntfy.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.util.Linkify
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.allViews
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.stfalcon.imageviewer.StfalconImageViewer
import io.heckel.ntfy.R
import io.heckel.ntfy.db.*
import io.heckel.ntfy.msg.DownloadManager
import io.heckel.ntfy.msg.DownloadType
import io.heckel.ntfy.msg.NotificationService
import io.heckel.ntfy.msg.NotificationService.Companion.ACTION_COPY
import io.heckel.ntfy.msg.NotificationService.Companion.ACTION_VIEW
import io.heckel.ntfy.ui.CardTagFormatter.categorize
import io.heckel.ntfy.ui.CardTagFormatter.formatAbsoluteTimestamp
import io.heckel.ntfy.ui.card.body.CardBodyBinder
import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.design.GlowToken
import io.heckel.ntfy.ui.design.resolveGlow
import io.heckel.ntfy.util.*
import io.noties.markwon.Markwon
import me.saket.bettermovementmethod.BetterLinkMovementMethod

/**
 * Reusable view binder for a notification card row.
 *
 * Host dependencies are explicit callbacks; this class has no knowledge of
 * DetailActivity, DetailAdapter, or any specific RecyclerView host.
 */
class MessageCardBinder(
    private val itemView: View,
    private val markwon: Markwon,
    private val actions: MessageCardActions,
) {
    // ID of the notification currently bound; reset on every bind to prevent recycler leakage.
    private var boundNotificationId: String? = null
    // Set to true once a mark-read dispatch is in-flight; cleared when a different ID is bound.
    private var markReadPending: Boolean = false

    private val cardView: androidx.cardview.widget.CardView = itemView.findViewById(R.id.detail_item_card)

    // Story 2.6: effect controller manages transient animations and resets.
    private val effectController: CardEffectController = CardEffectController(itemView, cardView)

    private val priorityAccentView: View = itemView.findViewById(R.id.card_priority_accent)
    // Story 2.3a: new header views
    private val headerBadgeView: TextView = itemView.findViewById(R.id.card_header_badge)
    private val headerTitleView: TextView = itemView.findViewById(R.id.card_header_title)
    private val headerUnreadDotView: View = itemView.findViewById(R.id.card_header_unread_dot)
    private val dateView: TextView = itemView.findViewById(R.id.detail_item_date_text)
    private val titleView: TextView = itemView.findViewById(R.id.detail_item_title_text)
    private val messageView: TextView = itemView.findViewById(R.id.detail_item_message_text)
    private val iconView: ImageView = itemView.findViewById(R.id.detail_item_icon)
    // Story 2.4: meta row — tag chip host and absolute timestamp
    private val tagChipGroup: ChipGroup = itemView.findViewById(R.id.card_tag_chip_group)
    private val metaTimestampView: TextView = itemView.findViewById(R.id.card_meta_timestamp)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.card_delete_button)
    private val menuButton: ImageButton = itemView.findViewById(R.id.detail_item_menu_button)
    private val attachmentImageView: ImageView = itemView.findViewById(R.id.detail_item_attachment_image)
    private val attachmentBoxView: View = itemView.findViewById(R.id.detail_item_attachment_file_box)
    private val attachmentIconView: ImageView = itemView.findViewById(R.id.detail_item_attachment_file_icon)
    private val attachmentInfoView: TextView = itemView.findViewById(R.id.detail_item_attachment_file_info)
    private val actionsWrapperView: ConstraintLayout = itemView.findViewById(R.id.detail_item_actions_wrapper)
    private val actionsFlow: Flow = itemView.findViewById(R.id.detail_item_actions_flow)

    // Story 3.1: body dispatch with safe fallback; owns messageView from this point on.
    private val cardBodyBinder: CardBodyBinder = CardBodyBinder(
        messageView = messageView,
        dispatcher = CardBodyDispatcher(),
        markwon = markwon,
    )

    fun bind(
        notification: Notification,
        topicName: String?,
        selected: Boolean,
        bindState: CardBindState = CardBindState(),
    ) {
        // Story 2.6: cancel any in-flight animators and restore baseline before every bind.
        effectController.resetTransient()

        // Reset per-holder state so recycled views target the new notification only.
        // markReadPending is cleared unconditionally: Room may rebind the same ID (e.g. an
        // attachment download completes) while the notification is still unread, and the
        // pending guard must not survive across any rebind.
        boundNotificationId = notification.id
        markReadPending = false

        val context = itemView.context
        val message = maybeAppendActionErrors(formatMessage(notification), notification)

        // Legacy date view kept for any future callers; hidden in Story 2.4 meta row design.
        dateView.visibility = View.GONE

        // Story 3.1: body dispatch with fail-safe fallback (replaces inline messageView binding).
        val tags = splitTags(notification.tags)
        cardBodyBinder.bind(
            tags = tags,
            decodedBody = message.toString(),
            isMarkdown = notification.isMarkdown(),
            cardClickAction = { actions.onClick(notification) },
            cardLongClickAction = { actions.onLongClick(notification) },
            markReadAction = {
                val markRead = actions.onMarkRead
                if (markRead != null && notification.notificationId != 0 && !markReadPending) {
                    markReadPending = true
                    markRead(notification)
                }
            },
        )
        cardView.setOnClickListener {
            val selectionHandled = actions.onClick(notification)
            // Tap-to-read: only when host is not in selection/action mode, notification is
            // unread, and no dispatch is already in-flight (AC 2, AC 5 of Story 2-5).
            if (!selectionHandled) {
                val markRead = actions.onMarkRead
                if (markRead != null && notification.notificationId != 0 && !markReadPending) {
                    markReadPending = true
                    markRead(notification)
                }
            }
        }
        cardView.setOnLongClickListener { actions.onLongClick(notification); true }

        // X delete button: capture notification.id at bind-time; consumed before card click.
        deleteButton.setOnClickListener {
            it.isPressed = false
            actions.onDeleteRequested(notification)
        }

        if (notification.title != "") {
            titleView.visibility = View.VISIBLE
            titleView.text = formatTitle(notification)
        } else {
            titleView.visibility = View.GONE
        }
        renderMetaRow(context, notification, topicName)

        // Story 2.6: persistent presentation state — static deep-link emphasis overrides
        // the normal background but does not clobber selected/priority state.
        val normalBackgroundColor = if (selected) {
            Colors.cardSelectedBackgroundColor(context)
        } else {
            Colors.cardBackgroundColor(context)
        }
        when (bindState.presentation) {
            is CardPresentation.Loading -> {
                // Skeleton state: reset all views and suppress interactivity.
                // The host (Story 4.3) is responsible for mounting the skeleton layout;
                // this branch ensures a recycled holder shows nothing interactive.
                reset()
                cardView.setCardBackgroundColor(normalBackgroundColor)
                return
            }
            is CardPresentation.StaticDeepLinkEmphasis -> {
                effectController.applyStaticDeepLinkEmphasis(context, normalBackgroundColor)
            }
            else -> {
                cardView.setCardBackgroundColor(normalBackgroundColor)
            }
        }

        val attachment = notification.attachment
        val attachmentFileStat = maybeFileStat(context, attachment?.contentUri)
        val iconFileStat = maybeFileStat(context, notification.icon?.contentUri)

        renderHeader(context, notification, message)
        renderPriority(context, notification)
        resetCardButtons()
        maybeRenderMenu(context, notification, attachmentFileStat)
        maybeRenderAttachment(context, notification, attachmentFileStat)
        maybeRenderIcon(context, notification, iconFileStat)
        maybeRenderActions(context, notification)

        // Story 2.6: one-shot transient effects — dispatched after all persistent state is
        // applied so the animator starts from the correct final position/background.
        when (val eff = bindState.effect) {
            is CardEffect.NewArrival -> effectController.playArrival(context, eff.stableId, eff.consumed)
            is CardEffect.DeepLinkPulse -> effectController.playDeepLinkPulse(context, normalBackgroundColor, eff.consumed)
            is CardEffect.None -> { /* no-op */ }
        }
    }

    fun reset() {
        // Story 2.6: cancel animators and restore baseline before recycling.
        effectController.resetTransient()
        boundNotificationId = null
        markReadPending = false
        // Story 2.4: clear dynamic chip children and listeners to prevent recycler leakage
        tagChipGroup.removeAllViews()
        metaTimestampView.text = null
        dateView.visibility = View.GONE
        // Header reset (Story 2.3a)
        headerBadgeView.text = null
        headerBadgeView.backgroundTintList = null
        headerBadgeView.setTextColor(0)
        headerTitleView.text = null
        headerUnreadDotView.visibility = View.GONE
        headerUnreadDotView.setLayerType(View.LAYER_TYPE_NONE, null)
        // Story 3.1: delegate body reset to cardBodyBinder (clears messageView state).
        cardBodyBinder.reset()
        cardView.setOnClickListener(null)
        cardView.setOnLongClickListener(null)
        attachmentImageView.setImageDrawable(null)
        attachmentImageView.setOnClickListener(null)
        attachmentImageView.visibility = View.GONE
        attachmentBoxView.setOnClickListener(null)
        attachmentBoxView.visibility = View.GONE
        iconView.setImageDrawable(null)
        iconView.visibility = View.GONE
        deleteButton.setOnClickListener(null)
        menuButton.setOnClickListener(null)
        menuButton.visibility = View.GONE
        priorityAccentView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        priorityAccentView.setLayerType(View.LAYER_TYPE_NONE, null)
        actionsWrapperView.visibility = View.GONE
        resetCardButtons()
    }

    private fun renderMetaRow(context: Context, notification: Notification, topicName: String?) {
        // Always clear dynamic children before re-binding (AC 6 — recycler state reset)
        tagChipGroup.removeAllViews()

        metaTimestampView.text = formatAbsoluteTimestamp(notification.timestamp)

        val cardTags = categorize(notification.tags, topicName)
        val cardClickAction: () -> Unit = { actions.onClick(notification) }

        // Topic chip — guard against empty string from host
        if (!cardTags.topic.isNullOrBlank()) {
            tagChipGroup.addView(buildChip(context,
                text = cardTags.topic,
                bgColor = ContextCompat.getColor(context, R.color.topic_chip_bg),
                textColor = ContextCompat.getColor(context, R.color.topic_chip_text),
                cardClickAction))
        }

        // Service chips
        val serviceBg = ContextCompat.getColor(context, R.color.tag_service_bg)
        val serviceText = ContextCompat.getColor(context, R.color.tag_service_text)
        for (label in cardTags.service) {
            tagChipGroup.addView(buildChip(context, label, serviceBg, serviceText, cardClickAction))
        }

        // General chips: first two visible; remainder behind +N more
        val bgColors = context.resources.obtainTypedArray(R.array.tag_general_backgrounds)
        val textColors = context.resources.obtainTypedArray(R.array.tag_general_texts)
        val general = cardTags.general

        val visibleCount = minOf(general.size, GENERAL_TAG_COLLAPSE_COUNT)
        try {
            for (i in 0 until visibleCount) {
                val gt = general[i]
                tagChipGroup.addView(buildChip(
                    context,
                    gt.name,
                    bgColors.getColor(gt.paletteIndex, 0),
                    textColors.getColor(gt.paletteIndex, 0),
                    cardClickAction,
                ))
            }
        } finally {
            bgColors.recycle()
            textColors.recycle()
        }

        // +N more button (when 3 or more general tags)
        if (general.size > GENERAL_TAG_COLLAPSE_COUNT) {
            val remaining = general.subList(visibleCount, general.size)
            val moreButton = buildMoreButton(context, remaining, cardClickAction)
            tagChipGroup.addView(moreButton)
        }
    }

    private fun buildChip(
        context: Context,
        text: String,
        bgColor: Int,
        textColor: Int,
        cardClickAction: () -> Unit,
    ): Chip {
        return Chip(context).apply {
            this.text = text
            chipCornerRadius = context.resources.getDimension(R.dimen.radius_full)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_caption))
            chipBackgroundColor = ColorStateList.valueOf(bgColor)
            setTextColor(textColor)
            isCheckable = false
            isClickable = true
            isFocusable = true
            // Stop chip tap from bubbling into the card click/mark-read action (AC 11)
            setOnClickListener { /* consume; no card action */ }
            setOnLongClickListener { /* consume */ true }
        }
    }

    private fun buildMoreButton(
        context: Context,
        remaining: List<CardTagFormatter.GeneralTag>,
        cardClickAction: () -> Unit,
    ): Chip {
        // Resolve colors eagerly; TypedArray must not outlive this call (AC 5 expansion).
        val bgArray = context.resources.obtainTypedArray(R.array.tag_general_backgrounds)
        val textArray = context.resources.obtainTypedArray(R.array.tag_general_texts)
        val resolvedBg = IntArray(6) { bgArray.getColor(it, 0) }
        val resolvedText = IntArray(6) { textArray.getColor(it, 0) }
        bgArray.recycle()
        textArray.recycle()

        val n = remaining.size
        return Chip(context).apply {
            text = context.getString(R.string.notification_card_tags_more, n)
            chipCornerRadius = context.resources.getDimension(R.dimen.radius_full)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_caption))
            chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_2))
            setTextColor(ContextCompat.getColor(context, R.color.muted))
            isCheckable = false
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Reveal remaining general chips; remove this button (AC 5)
                tagChipGroup.removeView(this)
                for (gt in remaining) {
                    tagChipGroup.addView(buildChip(
                        context,
                        gt.name,
                        resolvedBg[gt.paletteIndex],
                        resolvedText[gt.paletteIndex],
                        cardClickAction,
                    ))
                }
            }
            setOnLongClickListener { true }
        }
    }

    private fun renderPriority(context: Context, notification: Notification) {
        // Priority accent bar: fill color + optional dark-mode glow. Always reset both on every bind.
        // Normalize through toPriority() so invalid values consistently resolve to P3, matching
        // the badge path in renderHeader().
        val priority = toPriority(notification.priority)
        val accentColorRes = accentColorResForPriority(priority)
        priorityAccentView.setBackgroundColor(ContextCompat.getColor(context, accentColorRes))
        applyPriorityGlow(context, priority)
    }

    private fun renderHeader(context: Context, notification: Notification, bodyFallback: CharSequence) {
        val priority = toPriority(notification.priority)
        val spec = badgeSpecForPriority(priority)

        // Badge label, background tint, and text color — reset on every bind (AC 7)
        headerBadgeView.text = context.getString(spec.labelRes)
        headerBadgeView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, spec.backgroundColorRes))
        headerBadgeView.setTextColor(ContextCompat.getColor(context, spec.textColorRes))

        // Header title: non-blank title → formatTitle, else pre-computed body fallback (AC 3, 4).
        // bodyFallback already includes maybeAppendActionErrors so header and body are consistent.
        headerTitleView.text = if (notification.title.isNotBlank()) {
            formatTitle(notification)
        } else {
            bodyFallback
        }

        // Unread dot: visible only when notificationId != 0 (AC 5, 6, 7)
        val isUnread = notification.notificationId != 0
        if (isUnread) {
            headerUnreadDotView.visibility = View.VISIBLE
            applyUnreadDotGlow(context)
        } else {
            headerUnreadDotView.visibility = View.GONE
            headerUnreadDotView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun applyUnreadDotGlow(context: Context) {
        val spec = resolveGlow(context, GlowToken.ACCENT_DOT)
        if (spec != null) {
            val radiusPx = spec.blurRadiusDp * context.resources.displayMetrics.density
            val paint = android.graphics.Paint().apply {
                setShadowLayer(radiusPx, 0f, 0f, spec.color)
            }
            headerUnreadDotView.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
        } else {
            headerUnreadDotView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun applyPriorityGlow(context: Context, priority: Int) {
        val glowToken = priorityGlowToken(priority)
        val spec = if (glowToken != null) resolveGlow(context, glowToken) else null

        if (spec != null) {
            val radiusPx = spec.blurRadiusDp * context.resources.displayMetrics.density
            val paint = android.graphics.Paint().apply {
                setShadowLayer(radiusPx, 0f, 0f, spec.color)
            }
            priorityAccentView.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
        } else {
            // Explicitly clear glow so recycled P4/P5 holders don't leak onto P1–P3.
            priorityAccentView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun maybeRenderMenu(context: Context, notification: Notification, attachmentFileStat: FileInfo?) {
        val menuButtonPopupMenu = maybeCreateMenuPopup(context, menuButton, notification, attachmentFileStat)
        if (menuButtonPopupMenu != null) {
            menuButton.setOnClickListener { menuButtonPopupMenu.show() }
            menuButton.visibility = View.VISIBLE
        } else {
            menuButton.visibility = View.GONE
        }
    }

    private fun maybeRenderAttachment(context: Context, notification: Notification, attachmentFileStat: FileInfo?) {
        if (notification.attachment == null) {
            attachmentImageView.visibility = View.GONE
            attachmentBoxView.visibility = View.GONE
            return
        }
        val attachment = notification.attachment
        val image = attachment.contentUri != null && supportedImage(attachment.type) && previewableImage(attachmentFileStat)
        val bitmap = if (image) attachment.contentUri.readBitmapFromUriOrNull(context) else null
        maybeRenderAttachmentImage(context, bitmap, attachment)
        maybeRenderAttachmentBox(context, notification, attachment, attachmentFileStat, bitmap)
    }

    private fun maybeRenderIcon(context: Context, notification: Notification, iconStat: FileInfo?) {
        if (notification.icon == null || !previewableImage(iconStat)) {
            iconView.visibility = View.GONE
            return
        }
        try {
            val icon = notification.icon
            val bitmap = icon.contentUri?.readBitmapFromUri(context) ?: throw Exception("uri empty")
            iconView.setImageBitmap(bitmap)
            iconView.visibility = View.VISIBLE
        } catch (_: Exception) {
            iconView.visibility = View.GONE
        }
    }

    private fun maybeRenderActions(context: Context, notification: Notification) {
        if (!notification.actions.isNullOrEmpty()) {
            actionsWrapperView.visibility = View.VISIBLE
            val actionsCount = minOf(notification.actions.size, 3)
            for (i in 0 until actionsCount) {
                val action = notification.actions[i]
                val label = formatActionLabel(action)
                val actionButton = createCardButton(context, label) { runAction(context, notification, action) }
                addButtonToCard(actionButton)
            }
        } else {
            actionsWrapperView.visibility = View.GONE
        }
    }

    private fun resetCardButtons() {
        actionsFlow.allViews.toList().forEach { actionsFlow.removeView(it) }
        actionsWrapperView.removeAllViews()
        actionsWrapperView.addView(actionsFlow)
    }

    private fun addButtonToCard(button: View) {
        actionsWrapperView.addView(button)
        actionsFlow.addView(button)
    }

    private fun createCardButton(context: Context, label: String, onClick: () -> Boolean): View {
        val button = LayoutInflater.from(context).inflate(R.layout.button_action, null) as MaterialButton
        button.id = View.generateViewId()
        button.text = label
        button.setOnClickListener { onClick() }
        return button
    }

    private fun maybeRenderAttachmentImage(context: Context, bitmap: Bitmap?, attachment: Attachment) {
        if (bitmap == null) {
            attachmentImageView.visibility = View.GONE
            return
        }
        try {
            Glide.with(context).load(attachment.contentUri).fitCenter().into(attachmentImageView)
            attachmentImageView.setOnClickListener {
                StfalconImageViewer.Builder<Any?>(context, listOf(bitmap)) { imageView, _ ->
                    Glide.with(context).load(attachment.contentUri).into(imageView)
                }
                    .allowZooming(true)
                    .withTransitionFrom(attachmentImageView)
                    .withHiddenStatusBar(false)
                    .show()
            }
            attachmentImageView.visibility = View.VISIBLE
        } catch (_: Exception) {
            attachmentImageView.visibility = View.GONE
        }
    }

    private fun maybeRenderAttachmentBox(context: Context, notification: Notification, attachment: Attachment, attachmentFileStat: FileInfo?, bitmap: Bitmap?) {
        if (bitmap != null) {
            attachmentBoxView.visibility = View.GONE
            return
        }
        attachmentInfoView.text = formatAttachmentDetails(context, attachment, attachmentFileStat)
        attachmentIconView.setImageResource(mimeTypeToIconResource(attachment.type))
        val attachmentBoxPopupMenu = maybeCreateMenuPopup(context, attachmentBoxView, notification, attachmentFileStat)
        if (attachmentBoxPopupMenu != null) {
            attachmentBoxView.setOnClickListener { attachmentBoxPopupMenu.show() }
        } else {
            attachmentBoxView.setOnClickListener {
                Toast.makeText(context, context.getString(R.string.detail_item_cannot_download), Toast.LENGTH_LONG).show()
            }
        }
        attachmentBoxView.visibility = View.VISIBLE
    }

    private fun maybeCreateMenuPopup(context: Context, anchor: View?, notification: Notification, attachmentFileStat: FileInfo?): PopupMenu? {
        val popup = PopupMenu(context, anchor)
        popup.menuInflater.inflate(R.menu.menu_detail_attachment, popup.menu)
        val attachment = notification.attachment
        val hasAttachment = attachment != null
        val attachmentExists = attachmentFileStat != null
        val expired = attachment?.expires != null && attachment.expires < System.currentTimeMillis() / 1000
        val inProgress = attachment?.progress in 0..99
        val hasClickLink = notification.click != ""

        val downloadItem = popup.menu.findItem(R.id.detail_item_menu_download)
        val cancelItem = popup.menu.findItem(R.id.detail_item_menu_cancel)
        val openItem = popup.menu.findItem(R.id.detail_item_menu_open)
        val deleteItem = popup.menu.findItem(R.id.detail_item_menu_delete)
        val saveFileItem = popup.menu.findItem(R.id.detail_item_menu_save_file)
        val copyUrlItem = popup.menu.findItem(R.id.detail_item_menu_copy_url)
        val copyContentsItem = popup.menu.findItem(R.id.detail_item_menu_copy_contents)

        if (attachment != null) {
            openItem.setOnMenuItemClickListener { openFile(context, attachment) }
            saveFileItem.setOnMenuItemClickListener { saveFile(context, attachment) }
            deleteItem.setOnMenuItemClickListener { actions.onDeleteAttachment(notification, attachment) }
            copyUrlItem.setOnMenuItemClickListener { copyToClipboard(context, "attachment url", attachment.url); true }
            downloadItem.setOnMenuItemClickListener { actions.onDownloadAttachment(notification); true }
            cancelItem.setOnMenuItemClickListener { actions.onCancelDownload(notification); true }
        }
        if (hasClickLink) {
            copyContentsItem.setOnMenuItemClickListener {
                copyToClipboard(context, "notification", decodeMessage(notification)); true
            }
        }

        openItem.isVisible = hasAttachment && attachmentExists
        downloadItem.isVisible = hasAttachment && !attachmentExists && !expired && !inProgress
        deleteItem.isVisible = hasAttachment && attachmentExists
        saveFileItem.isVisible = hasAttachment && attachmentExists
        copyUrlItem.isVisible = hasAttachment && !expired
        cancelItem.isVisible = hasAttachment && inProgress
        copyContentsItem.isVisible = notification.click != ""

        val noOptions = !openItem.isVisible && !saveFileItem.isVisible && !downloadItem.isVisible
                && !copyUrlItem.isVisible && !cancelItem.isVisible && !deleteItem.isVisible
                && !copyContentsItem.isVisible
        if (noOptions) return null
        return popup
    }

    private fun formatAttachmentDetails(context: Context, attachment: Attachment, attachmentFileStat: FileInfo?): String {
        val name = attachment.name
        val exists = attachmentFileStat != null
        val notYetDownloaded = !exists && attachment.progress == ATTACHMENT_PROGRESS_NONE
        val downloading = !exists && attachment.progress in 0..99
        val deleted = !exists && (attachment.progress == ATTACHMENT_PROGRESS_DONE || attachment.progress == ATTACHMENT_PROGRESS_DELETED)
        val failed = !exists && attachment.progress == ATTACHMENT_PROGRESS_FAILED
        val expired = attachment.expires != null && attachment.expires < System.currentTimeMillis() / 1000
        val expires = attachment.expires != null && attachment.expires > System.currentTimeMillis() / 1000
        val infos = mutableListOf<String>()
        if (attachment.size != null) infos.add(formatBytes(attachment.size))
        if (notYetDownloaded) {
            if (expired) infos.add(context.getString(R.string.detail_item_download_info_not_downloaded_expired))
            else if (expires) infos.add(context.getString(R.string.detail_item_download_info_not_downloaded_expires_x, formatDateShort(attachment.expires)))
            else infos.add(context.getString(R.string.detail_item_download_info_not_downloaded))
        } else if (downloading) {
            infos.add(context.getString(R.string.detail_item_download_info_downloading_x_percent, attachment.progress))
        } else if (deleted) {
            if (expired) infos.add(context.getString(R.string.detail_item_download_info_deleted_expired))
            else if (expires) infos.add(context.getString(R.string.detail_item_download_info_deleted_expires_x, formatDateShort(attachment.expires)))
            else infos.add(context.getString(R.string.detail_item_download_info_deleted))
        } else if (failed) {
            if (expired) infos.add(context.getString(R.string.detail_item_download_info_download_failed_expired))
            else if (expires) infos.add(context.getString(R.string.detail_item_download_info_download_failed_expires_x, formatDateShort(attachment.expires)))
            else infos.add(context.getString(R.string.detail_item_download_info_download_failed))
        }
        return if (infos.isNotEmpty()) "$name\n${infos.joinToString(", ")}" else name
    }

    private fun openFile(context: Context, attachment: Attachment): Boolean {
        if (!canOpenAttachment(attachment)) {
            Toast.makeText(context, context.getString(R.string.detail_item_cannot_open_apk), Toast.LENGTH_LONG).show()
            return true
        }
        Log.d(TAG, "Opening file ${attachment.contentUri}")
        try {
            val contentUri = attachment.contentUri?.toUri()
            val intent = Intent(Intent.ACTION_VIEW, contentUri)
            intent.setDataAndType(contentUri, attachment.type ?: "application/octet-stream")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.detail_item_cannot_open_not_found), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.detail_item_cannot_open, e.message), Toast.LENGTH_LONG).show()
        }
        return true
    }

    private fun saveFile(context: Context, attachment: Attachment): Boolean {
        Log.d(TAG, "Copying file ${attachment.contentUri}")
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, attachment.name)
                if (attachment.type != null) put(MediaStore.MediaColumns.MIME_TYPE, attachment.type)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_DOWNLOAD, 1)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val inUri = attachment.contentUri!!.toUri()
            val inFile = resolver.openInputStream(inUri) ?: throw Exception("Cannot open input stream")
            val outUri = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                val file = ensureSafeNewFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), attachment.name)
                FileProvider.getUriForFile(context, io.heckel.ntfy.msg.DownloadAttachmentWorker.FILE_PROVIDER_AUTHORITY, file)
            } else {
                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                resolver.insert(contentUri, values) ?: throw Exception("Cannot insert content")
            }
            val outFile = resolver.openOutputStream(outUri) ?: throw Exception("Cannot open output stream")
            inFile.use { it.copyTo(outFile) }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(outUri, values, null, null)
            }
            val actualName = fileName(context, outUri.toString(), attachment.name)
            Toast.makeText(context, context.getString(R.string.detail_item_saved_successfully, actualName), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save file: ${e.message}", e)
            Toast.makeText(context, context.getString(R.string.detail_item_cannot_save, e.message), Toast.LENGTH_LONG).show()
        }
        return true
    }

    private fun runAction(context: Context, notification: Notification, action: Action): Boolean {
        when (action.action) {
            ACTION_VIEW -> runViewAction(context, action)
            ACTION_COPY -> runCopyAction(context, action)
            else -> runOtherUserAction(context, notification, action)
        }
        return true
    }

    private fun runViewAction(context: Context, action: Action) {
        try {
            val url = action.url ?: return
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start activity from URL ${action.url}", e)
            val message = if (e is ActivityNotFoundException) action.url else e.message
            Toast.makeText(context, context.getString(R.string.detail_item_cannot_open_url, message), Toast.LENGTH_LONG).show()
        }
    }

    private fun runCopyAction(context: Context, action: Action) {
        val value = action.value ?: return
        copyToClipboard(context, action.label, value)
    }

    private fun runOtherUserAction(context: Context, notification: Notification, action: Action) {
        val intent = Intent(context, NotificationService.UserActionBroadcastReceiver::class.java).apply {
            putExtra(NotificationService.BROADCAST_EXTRA_TYPE, NotificationService.BROADCAST_TYPE_USER_ACTION)
            putExtra(NotificationService.BROADCAST_EXTRA_NOTIFICATION_ID, notification.id)
            putExtra(NotificationService.BROADCAST_EXTRA_ACTION_ID, action.id)
        }
        context.sendBroadcast(intent)
    }

    private fun previewableImage(fileStat: FileInfo?): Boolean {
        return fileStat != null && fileStat.size <= IMAGE_PREVIEW_MAX_BYTES
    }

    /** Badge presentation — label string resource, background color token, text color token. */
    data class BadgeSpec(val labelRes: Int, val backgroundColorRes: Int, val textColorRes: Int)

    companion object {
        const val TAG = "NtfyMessageCardBinder"
        const val IMAGE_PREVIEW_MAX_BYTES = 5 * 1024 * 1024
        // Show first 2 general tags collapsed; 3+ triggers +N more button (AC 5)
        const val GENERAL_TAG_COLLAPSE_COUNT = 2

        /**
         * Pure mapping from (already-normalized) priority int to BadgeSpec.
         * Exposed for unit testing; do not use outside this file and its tests.
         */
        fun badgeSpecForPriority(priority: Int): BadgeSpec = when (priority) {
            PRIORITY_MIN     -> BadgeSpec(R.string.notification_card_badge_min,    R.color.surface_2, R.color.muted)
            PRIORITY_LOW     -> BadgeSpec(R.string.notification_card_badge_low,    R.color.surface_2, R.color.muted)
            PRIORITY_DEFAULT -> BadgeSpec(R.string.notification_card_badge_normal, R.color.surface_2, R.color.text)
            PRIORITY_HIGH    -> BadgeSpec(R.string.notification_card_badge_high,   R.color.priority_high, R.color.priority_high_on_surface)
            PRIORITY_MAX     -> BadgeSpec(R.string.notification_card_badge_max,    R.color.priority_max,  R.color.priority_max_on_surface)
            else             -> BadgeSpec(R.string.notification_card_badge_normal, R.color.surface_2, R.color.text)
        }

        /**
         * Pure mapping from priority int to the color resource ID used by the accent bar.
         * Exposed for unit testing; do not use outside this file and its tests.
         */
        fun accentColorResForPriority(priority: Int): Int = when (priority) {
            PRIORITY_MIN     -> R.color.muted
            PRIORITY_LOW     -> R.color.muted
            PRIORITY_DEFAULT -> R.color.text
            PRIORITY_HIGH    -> R.color.priority_high
            PRIORITY_MAX     -> R.color.priority_max
            else             -> R.color.text
        }

        /**
         * Pure mapping from priority int to the GlowToken string, or null for no glow.
         * Exposed for unit testing; do not use outside this file and its tests.
         */
        fun priorityGlowToken(priority: Int): String? = when (priority) {
            PRIORITY_HIGH -> GlowToken.PRIORITY_HIGH
            PRIORITY_MAX  -> GlowToken.PRIORITY_MAX
            else          -> null
        }
    }
}
