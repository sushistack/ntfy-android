package io.heckel.ntfy.ui.card.chart

/**
 * Immutable render model consumed by [StructuredChartView] and the label row.
 * Created from a validated [ChartSpec]; contains no mutable JSON references.
 */
data class ChartRenderModel(
    val kind: ChartKind,
    val unit: String?,
    val points: List<ChartPoint>,
) {
    companion object {
        fun from(spec: ChartSpec): ChartRenderModel = ChartRenderModel(
            kind = spec.effectiveKind,
            unit = spec.unit,
            points = spec.points,
        )
    }
}
