package io.heckel.ntfy.ui.card.chart

import kotlin.math.max

/**
 * Pure coordinate calculations for bar and line chart geometry.
 * No Android View or Canvas references — fast in JVM tests.
 *
 * Y-axis convention: top of plot = y=0, bottom = y=plotHeight (screen coords).
 * Value → y: higher values map to smaller y (draw upward).
 */
object ChartGeometry {

    private const val BAR_GAP_FRACTION = 0.15f   // fraction of per-bar slot used as gap
    private const val MIN_BAR_WIDTH = 1f          // px minimum so bars are always visible

    data class BarGeom(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val baseline: Float, // y-coordinate of the zero baseline
    ) {
        val width: Float get() = right - left
    }

    data class LinePoint(val x: Float, val y: Float)

    /**
     * Compute bar rectangles for [points] within a plot of [plotWidth] × [plotHeight] pixels.
     * Bars share the available width without overlap; the zero baseline divides +/- regions.
     */
    fun computeBars(
        points: List<ChartPoint>,
        domain: ChartDomain,
        plotWidth: Float,
        plotHeight: Float,
    ): List<BarGeom> {
        if (points.isEmpty()) return emptyList()
        val n = points.size
        val slotWidth = plotWidth / n
        val rawBarWidth = max(MIN_BAR_WIDTH, slotWidth * (1f - BAR_GAP_FRACTION))

        val baselineY = plotHeight - domain.valueToFraction(0.0).toFloat().coerceIn(0f, 1f) * plotHeight

        return points.mapIndexed { i, pt ->
            val slotCenter = slotWidth * i + slotWidth / 2f
            val left = (slotCenter - rawBarWidth / 2f).coerceAtLeast(0f)
            val right = (left + rawBarWidth).coerceAtMost(plotWidth)

            val valueFraction = domain.valueToFraction(pt.value).toFloat().coerceIn(0f, 1f)
            val valueY = plotHeight - valueFraction * plotHeight

            val top = minOf(valueY, baselineY)
            val bottom = maxOf(valueY, baselineY)

            BarGeom(left = left, top = top, right = right, bottom = bottom, baseline = baselineY)
        }
    }

    /**
     * Compute line polyline points for [points] within a plot of [plotWidth] × [plotHeight] pixels.
     * X positions are evenly distributed across the full width; y from domain mapping.
     */
    fun computeLinePoints(
        points: List<ChartPoint>,
        domain: ChartDomain,
        plotWidth: Float,
        plotHeight: Float,
    ): List<LinePoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) {
            val valueFraction = domain.valueToFraction(points[0].value).toFloat().coerceIn(0f, 1f)
            val y = plotHeight - valueFraction * plotHeight
            return listOf(LinePoint(x = plotWidth / 2f, y = y))
        }
        val lastIndex = (points.size - 1).toFloat()
        return points.mapIndexed { i, pt ->
            val x = if (lastIndex == 0f) 0f else (i / lastIndex) * plotWidth
            val valueFraction = domain.valueToFraction(pt.value).toFloat().coerceIn(0f, 1f)
            val y = plotHeight - valueFraction * plotHeight
            LinePoint(x = x, y = y)
        }
    }
}
