package io.heckel.ntfy.ui.card.body

import android.text.util.Linkify
import android.view.ViewGroup
import android.widget.TextView
import me.saket.bettermovementmethod.BetterLinkMovementMethod

/**
 * Manages body content rendered into the existing [messageView] (detail_item_message_text).
 *
 * Epic 3 owns body dispatch; the view itself lives in fragment_detail_item.xml owned by Epic 2.
 * This binder takes over text/markdown assignment from MessageCardBinder and adds the
 * dispatch seam, fail-safe containment, and recycle reset.
 *
 * Structured renderers that need a ViewGroup host (e.g. list, kv, chart) use [bodyContainer]
 * when provided. The [messageView] is hidden when a structured renderer takes over; it is
 * shown again and cleared on reset or text/fallback rendering.
 *
 * Safe fallback sequence (AC 4 / Story 3.1):
 *   1. Reset messageView + bodyContainer state.
 *   2. Dispatch route from tags + decoded body.
 *   3. Attempt selected renderer.
 *   4. On any Exception: clear and apply decoded text directly (plain, no Markdown).
 *   5. If that also throws: clear and set decoded text minimally.
 *
 * Never catches Throwable. Never renders exception messages to the user.
 * Header, meta, attachment, and action failures are outside this boundary (AC 4).
 */
class CardBodyBinder(
    private val messageView: TextView,
    private val dispatcher: CardBodyDispatcher,
    private val markdownRenderer: CardMarkdownRenderer,
    private val bodyContainer: ViewGroup? = null,
) {
    private val listRenderer = ListBlockRenderer()
    private val kvRenderer = KvBlockRenderer()
    private val chartRenderer = ChartBlockRenderer()
    private val sectionsRenderer by lazy {
        SectionsBlockRenderer(markdownRenderer, kvRenderer, listRenderer, chartRenderer)
    }

    /**
     * Bind [decodedBody] for a notification.
     * [cardClickAction] returns true when selection mode consumed the event.
     */
    fun bind(
        tags: List<String>,
        decodedBody: String,
        isMarkdown: Boolean,
        cardClickAction: (() -> Boolean)? = null,
        cardLongClickAction: (() -> Unit)? = null,
        markReadAction: (() -> Unit)? = null,
    ) {
        resetView()
        try {
            val route = dispatcher.dispatch(tags, decodedBody)
            renderRoute(route, decodedBody, isMarkdown, cardClickAction, cardLongClickAction, markReadAction)
        } catch (_: Exception) {
            // Step 4: clear and fall back to plain decoded text.
            resetView()
            try {
                applyPlainText(messageView, decodedBody)
                attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
            } catch (_: Exception) {
                // Step 5: terminal — set text directly, no listeners.
                resetView()
                messageView.text = decodedBody
            }
        }
    }

    fun reset() {
        resetView()
    }

    private fun renderRoute(
        route: CardBodyRoute,
        decodedBody: String,
        isMarkdown: Boolean,
        cardClickAction: (() -> Boolean)?,
        cardLongClickAction: (() -> Unit)?,
        markReadAction: (() -> Unit)?,
    ) {
        when (route) {
            is CardBodyRoute.Structured -> {
                val container = bodyContainer
                when (route.spec.type) {
                    CardSpec.KnownType.KV -> {
                        if (container != null) {
                            messageView.visibility = ViewGroup.GONE
                            kvRenderer.render(container, route)
                            attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
                            return
                        }
                        applyTextToView(messageView, decodedBody, isMarkdown)
                    }
                    CardSpec.KnownType.LIST -> {
                        if (container != null) {
                            messageView.visibility = ViewGroup.GONE
                            listRenderer.render(container, route)
                            attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
                            return
                        }
                        applyTextToView(messageView, decodedBody, isMarkdown)
                    }
                    CardSpec.KnownType.CHART -> {
                        if (container != null) {
                            messageView.visibility = ViewGroup.GONE
                            chartRenderer.render(container, route)
                            attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
                            return
                        }
                        applyTextToView(messageView, decodedBody, isMarkdown)
                    }
                    CardSpec.KnownType.SECTIONS -> {
                        if (container != null) {
                            messageView.visibility = ViewGroup.GONE
                            sectionsRenderer.renderSections(container, route)
                            attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
                            return
                        }
                        applyTextToView(messageView, decodedBody, isMarkdown)
                    }
                }
            }
            is CardBodyRoute.HeuristicKv -> {
                val container = bodyContainer
                if (container != null) {
                    messageView.visibility = ViewGroup.GONE
                    kvRenderer.renderKvSpec(container, route.kvSpec)
                    attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
                    return
                }
                applyTextToView(messageView, route.kvSpec.rows.joinToString("\n") { "${it.key}: ${it.value}" }, isMarkdown)
            }
            is CardBodyRoute.Text -> {
                applyTextToView(messageView, route.decodedBody, isMarkdown)
            }
        }
        attachListeners(messageView, cardClickAction, cardLongClickAction, markReadAction)
    }

    private fun applyTextToView(tv: TextView, text: String, isMarkdown: Boolean) {
        if (isMarkdown) {
            markdownRenderer.render(tv, text)
        } else {
            applyPlainText(tv, text)
        }
    }

    private fun applyPlainText(tv: TextView, text: String) {
        tv.autoLinkMask = Linkify.WEB_URLS
        tv.text = text
        tv.movementMethod = BetterLinkMovementMethod.getInstance()
    }

    private fun attachListeners(
        tv: TextView,
        cardClickAction: (() -> Boolean)?,
        cardLongClickAction: (() -> Unit)?,
        markReadAction: (() -> Unit)?,
    ) {
        tv.setOnClickListener {
            val selectionHandled = cardClickAction?.invoke() ?: false
            if (!selectionHandled) {
                markReadAction?.invoke()
            }
        }
        tv.setOnLongClickListener { cardLongClickAction?.invoke(); true }
    }

    private fun resetView() {
        messageView.setOnClickListener(null)
        messageView.setOnLongClickListener(null)
        messageView.movementMethod = null
        messageView.autoLinkMask = 0
        messageView.text = ""
        messageView.visibility = ViewGroup.VISIBLE
        // Clear any structured renderer views from the body container.
        bodyContainer?.removeAllViews()
    }
}
