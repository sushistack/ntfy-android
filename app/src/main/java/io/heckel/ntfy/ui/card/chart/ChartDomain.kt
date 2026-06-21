package io.heckel.ntfy.ui.card.chart

/**
 * Immutable y-domain for a chart series.
 *
 * Domain rule (AC 5):
 *   min = min(0, series.min)
 *   max = max(0, series.max)
 *   If min == max (all-zero or equal values), expand to a stable non-zero range.
 *
 * [valueToFraction] maps a value onto [0, 1] within [min, max].
 * The zero baseline is always in [0, 1].
 */
data class ChartDomain(val min: Double, val max: Double) {

    fun valueToFraction(v: Double): Double = (v - min) / (max - min)

    companion object {
        private const val EXPAND_AMOUNT = 1.0

        fun compute(points: List<ChartPoint>): ChartDomain {
            require(points.isNotEmpty())
            val seriesMin = points.minOf { it.value }
            val seriesMax = points.maxOf { it.value }

            var dMin = minOf(0.0, seriesMin)
            var dMax = maxOf(0.0, seriesMax)

            if (dMax == dMin) {
                // Expand to a stable range that still contains zero.
                dMin = dMin - EXPAND_AMOUNT
                dMax = dMax + EXPAND_AMOUNT
            }
            return ChartDomain(dMin, dMax)
        }
    }
}
