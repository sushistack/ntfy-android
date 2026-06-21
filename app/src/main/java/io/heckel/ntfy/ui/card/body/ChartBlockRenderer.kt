package io.heckel.ntfy.ui.card.body

import android.view.ViewGroup
import com.google.gson.JsonObject
import io.heckel.ntfy.ui.card.chart.ChartRenderModel
import io.heckel.ntfy.ui.card.chart.ChartSpecParser
import io.heckel.ntfy.ui.card.chart.StructuredChartBlockView

/**
 * Renders a structured [type:"chart"] block into a supplied [ViewGroup] host.
 *
 * Mounts a [StructuredChartBlockView] and binds the parsed [ChartRenderModel].
 * Zero valid points → returns without mounting any View (AC 8).
 * Stale chart from a recycled card is cleared before mounting (AC 9).
 *
 * Also callable from Story 3.7 sections via [renderInto].
 */
class ChartBlockRenderer : CardBodyRenderer {

    override fun render(container: ViewGroup, route: CardBodyRoute) {
        if (route !is CardBodyRoute.Structured) return
        if (route.spec.type != CardSpec.KnownType.CHART) return
        renderInto(container, route.spec.root)
    }

    /**
     * Public entry point for Story 3.7 sections orchestration.
     */
    fun renderInto(container: ViewGroup, root: JsonObject) {
        container.removeAllViews()

        val spec = ChartSpecParser.parse(root) ?: return   // zero valid points → no View
        val model = ChartRenderModel.from(spec)

        val chartBlock = StructuredChartBlockView(container.context)
        chartBlock.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        chartBlock.bind(model)
        container.addView(chartBlock)
    }
}
