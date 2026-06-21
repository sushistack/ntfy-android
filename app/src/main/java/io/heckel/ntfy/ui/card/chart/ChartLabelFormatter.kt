package io.heckel.ntfy.ui.card.chart

/**
 * Deterministic label text for a chart point.
 *
 * Precedence (AC 6):
 *   1. Non-empty point.label
 *   2. Formatted numeric value + optional unit (no separator between value and unit)
 *
 * Numeric format: integer-valued doubles print without decimal part ("100" not "100.0");
 * non-integer values use the minimal decimal representation without locale-dependent grouping.
 */
object ChartLabelFormatter {

    fun labelFor(point: ChartPoint, unit: String?): String {
        if (point.label.isNotEmpty()) return point.label
        val numStr = formatNumber(point.value)
        return if (unit.isNullOrEmpty()) numStr else "$numStr$unit"
    }

    internal fun formatNumber(v: Double): String {
        if (v == kotlin.math.floor(v) && !v.isInfinite()) {
            return v.toLong().toString()
        }
        return v.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}
