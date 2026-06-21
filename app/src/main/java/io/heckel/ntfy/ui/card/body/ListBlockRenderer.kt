package io.heckel.ntfy.ui.card.body

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.heckel.ntfy.R

/**
 * Renders a structured [type:"list"] block into a supplied [ViewGroup] host.
 *
 * Mounts [R.layout.view_card_list] into the container; clears the row host
 * before every bind so recycled cards never show stale rows.
 *
 * Each item occupies one horizontal row: a fixed-width marker column and a
 * weighted text column so wrapped continuation lines align under the text.
 *
 * Also callable from Story 3.7 sections via [renderInto] with a custom host.
 */
class ListBlockRenderer : CardBodyRenderer {

    override fun render(container: ViewGroup, route: CardBodyRoute) {
        if (route !is CardBodyRoute.Structured) return
        if (route.spec.type != CardSpec.KnownType.LIST) return

        val spec = ListSpec.from(route.spec.root)
        renderInto(container, spec)
    }

    /**
     * Public entry point for Story 3.7 sections orchestration.
     * Clears [container], inflates the list layout, and binds [spec].
     * Always removes stale children first so sections can safely call this
     * on a reused block host.
     */
    fun renderInto(container: ViewGroup, spec: ListSpec) {
        // Always clear stale content first (mirrors KvBlockRenderer pattern for AC 9).
        container.removeAllViews()

        val context = container.context
        val root = LayoutInflater.from(context)
            .inflate(R.layout.view_card_list, container, false)
        val rowHost = root.findViewById<LinearLayout>(R.id.list_row_host)

        if (spec.items.isEmpty()) {
            root.visibility = ViewGroup.GONE
        } else {
            root.visibility = ViewGroup.VISIBLE
            for ((index, text) in spec.items.withIndex()) {
                val row = LayoutInflater.from(context)
                    .inflate(R.layout.view_card_list_item, rowHost, false)

                val markerView = row.findViewById<TextView>(R.id.list_item_marker)
                val textView = row.findViewById<TextView>(R.id.list_item_text)

                val markerText = if (spec.ordered) "${index + 1}." else "•"
                markerView.text = markerText

                textView.text = text

                // Accessibility: row is one logical list entry; let TalkBack read
                // marker then item in natural child order. The marker view is marked
                // importantForAccessibility="no" in XML so the row LinearLayout
                // announces marker text only through the item's content description.
                row.contentDescription = "$markerText $text"

                rowHost.addView(row)
            }
        }

        container.addView(root)
    }
}
