package io.heckel.ntfy.ui.card.chart

/**
 * A single validated, finite data point in a chart's series.
 * Instances are only created by [ChartSpec.normalize] after coercion and finite-check.
 */
data class ChartPoint(
    val value: Double,
    val label: String,
)

/**
 * Raw input point before coercion/validation.
 * [rawValue] may be any JSON-decoded type (Double, Int, Long, String, null, Boolean, …).
 */
data class RawChartPoint(
    val rawValue: Any?,
    val label: String,
)
