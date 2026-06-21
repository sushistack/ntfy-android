package io.heckel.ntfy.ui.card.body

import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import io.heckel.ntfy.R
import io.noties.markwon.Markwon
import me.saket.bettermovementmethod.BetterLinkMovementMethod

/**
 * Token-styled plain/Markdown text renderer that inflates [R.layout.view_card_text] into
 * a [ViewGroup] host (card_body).
 *
 * Stories 3.6a/3.6b will wire this into [CardBodyBinder] when they migrate body rendering
 * from the pre-existing [messageView] (detail_item_message_text) to a dedicated token-styled
 * view inflated into card_body. Until then this class defines the seam and is unused in
 * production paths; [CardBodyBinder] handles text rendering directly on messageView.
 *
 * [isMarkdown] preserves the existing Markwon vs plain-Linkify decision; scheme hardening
 * belongs to Story 3.6b.
 */
class CardTextRenderer(
    private val markwon: Markwon,
    private val isMarkdown: Boolean = false,
    private val cardClickAction: (() -> Boolean)? = null,
    private val cardLongClickAction: (() -> Unit)? = null,
    private val markReadAction: (() -> Unit)? = null,
) : CardBodyRenderer {

    override fun render(container: ViewGroup, route: CardBodyRoute) {
        val decodedBody = when (route) {
            is CardBodyRoute.Text -> route.decodedBody
            else -> return
        }
        inflateAndBind(container, decodedBody)
    }

    fun inflateAndBind(container: ViewGroup, text: String) {
        val context = container.context
        val tv = LayoutInflater.from(context)
            .inflate(R.layout.view_card_text, container, false) as TextView

        if (isMarkdown) {
            tv.autoLinkMask = 0
            markwon.setMarkdown(tv, text)
        } else {
            tv.autoLinkMask = Linkify.WEB_URLS
            tv.text = text
        }
        tv.movementMethod = BetterLinkMovementMethod.getInstance()
        tv.setOnClickListener {
            val selectionHandled = cardClickAction?.invoke() ?: false
            if (!selectionHandled) {
                markReadAction?.invoke()
            }
        }
        tv.setOnLongClickListener { cardLongClickAction?.invoke(); true }

        container.addView(tv)
    }
}
