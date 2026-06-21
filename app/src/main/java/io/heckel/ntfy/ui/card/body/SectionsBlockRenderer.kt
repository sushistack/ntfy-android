package io.heckel.ntfy.ui.card.body

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.heckel.ntfy.R

/**
 * Renders a top-level [type:"sections"] block into the card_body [ViewGroup].
 *
 * Responsibilities (Story 3.7):
 * - Parse the child array via [SectionsSpecParser]; admit only markdown/kv/list/chart.
 * - Delegate each admitted child to the matching existing renderer instance.
 * - Apply exactly [R.dimen.spacing_3] (12dp) between successfully rendered visible children;
 *   no leading gap before the first child, no trailing gap after the last (AC 2).
 * - Clear the container before every bind so recycled cards carry no stale content (AC 7).
 * - Skipped children (nested sections, unknown type, malformed) leave no spacer (AC 5).
 * - Missing/null/non-array/empty blocks → clean empty body (AC 6).
 * - No maxLines, ellipsize, fixed height, or collapse affordance anywhere in the subtree (AC 8).
 *
 * Construction takes only [Context]-level dependencies via the container; no Activity,
 * adapter, repository, coroutine, navigation, or lifecycle reference (AC 9, 10).
 */
class SectionsBlockRenderer(
    private val markdownRenderer: CardMarkdownRenderer,
    private val kvRenderer: KvBlockRenderer = KvBlockRenderer(),
    private val listRenderer: ListBlockRenderer = ListBlockRenderer(),
    private val chartRenderer: ChartBlockRenderer = ChartBlockRenderer(),
) : CardBodyRenderer {

    override fun render(container: ViewGroup, route: CardBodyRoute) {
        if (route !is CardBodyRoute.Structured) return
        if (route.spec.type != CardSpec.KnownType.SECTIONS) return
        renderSections(container, route)
    }

    /**
     * Entry point for sections rendering. Callable from [CardBodyBinder] after it hides
     * [messageView] and passes [container] = card_body.
     */
    fun renderSections(container: ViewGroup, route: CardBodyRoute.Structured) {
        // Always clear stale content first — recycler reuse is a primary regression risk (AC 7).
        container.removeAllViews()

        val spec = SectionsSpecParser.parse(route.spec.root)
        if (spec.children.isEmpty()) return  // clean empty body (AC 6)

        val context = container.context
        val inflater = LayoutInflater.from(context)

        val host = inflater.inflate(R.layout.view_card_sections, container, false) as LinearLayout
        container.addView(host)

        val spacing3Px = context.resources.getDimensionPixelSize(R.dimen.spacing_3)

        var renderedCount = 0
        for (child in spec.children) {
            val childHost = FrameLayout(context).also { fl ->
                fl.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            val rendered = renderChild(child, childHost)
            if (!rendered) continue  // child produced nothing → no view, no spacer (AC 5)

            // Apply top margin for all children after the first (AC 2): spacing_3 between blocks,
            // no leading gap before the first, no trailing gap after the last.
            val lp = childHost.layoutParams as LinearLayout.LayoutParams
            if (renderedCount > 0) {
                lp.topMargin = spacing3Px
            }
            childHost.layoutParams = lp

            host.addView(childHost)
            renderedCount++
        }
    }

    /**
     * Dispatch a single [ChildBlock] into [container].
     * Returns true when at least one view was successfully added, false when the child
     * produced no visible output (so the caller skips the spacer).
     *
     * Child renderers own their own [container.removeAllViews] contract; the sections
     * host passes a fresh per-child FrameLayout so there is nothing to clear here.
     */
    private fun renderChild(child: ChildBlock, container: ViewGroup): Boolean {
        return try {
            when (child) {
                is ChildBlock.Markdown -> renderMarkdownChild(child, container)
                is ChildBlock.Kv -> renderKvChild(child, container)
                is ChildBlock.List -> renderListChild(child, container)
                is ChildBlock.Chart -> renderChartChild(child, container)
            }
        } catch (_: Exception) {
            false  // one failed child must not abort siblings (AC 5)
        }
    }

    private fun renderMarkdownChild(child: ChildBlock.Markdown, container: ViewGroup): Boolean {
        val tv = TextView(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            // No maxLines, no ellipsize anywhere in the sections subtree (AC 8)
            maxLines = Int.MAX_VALUE
            ellipsize = null
        }
        markdownRenderer.render(tv, child.text)
        container.addView(tv)
        return true
    }

    private fun renderKvChild(child: ChildBlock.Kv, container: ViewGroup): Boolean {
        val spec = KvSpecParser.parse(child.root) ?: return false
        if (spec.rows.isEmpty()) return false
        kvRenderer.renderKvSpec(container, spec)
        return container.childCount > 0
    }

    private fun renderListChild(child: ChildBlock.List, container: ViewGroup): Boolean {
        val spec = ListSpec.from(child.root)
        if (spec.items.isEmpty()) return false
        listRenderer.renderInto(container, spec)
        return container.childCount > 0
    }

    private fun renderChartChild(child: ChildBlock.Chart, container: ViewGroup): Boolean {
        val before = container.childCount
        chartRenderer.renderInto(container, child.root)
        return container.childCount > before
    }
}
