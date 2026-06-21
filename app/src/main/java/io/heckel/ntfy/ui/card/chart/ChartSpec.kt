package io.heckel.ntfy.ui.card.chart

/**
 * Validated, immutable chart specification ready for rendering.
 *
 * Created only after normalization; never retains references to mutable JSON structures.
 *
 * @param kind  wire kind string (null / "bar" / "line" / anything else → BAR default)
 * @param unit  optional unit suffix appended to fallback numeric labels
 * @param points validated, finite, ordered points (max 60)
 */
data class ChartSpec(
    val kind: String?,
    val unit: String?,
    val points: List<ChartPoint>,
) {
    val effectiveKind: ChartKind get() = ChartKind.fromWire(kind)

    companion object {
        private const val MAX_POINTS = 60
        private const val LABEL_THRESHOLD = 12

        /**
         * Coerce and filter [raw] into a validated [ChartPoint] list.
         * Steps: coerce → drop non-finite → preserve order → take first 60.
         * The source list is never mutated.
         */
        fun normalize(raw: List<RawChartPoint>): List<ChartPoint> {
            return raw
                .mapNotNull { rp ->
                    val v = coerceToFinite(rp.rawValue) ?: return@mapNotNull null
                    ChartPoint(value = v, label = rp.label)
                }
                .take(MAX_POINTS)
        }

        /** Returns true when a label row should be shown (1–12 valid points). */
        fun showLabels(validPointCount: Int): Boolean =
            validPointCount in 1..LABEL_THRESHOLD

        private fun coerceToFinite(raw: Any?): Double? {
            val d: Double = when (raw) {
                is Double -> raw
                is Float -> raw.toDouble()
                is Int -> raw.toDouble()
                is Long -> raw.toDouble()
                is Short -> raw.toDouble()
                is Byte -> raw.toDouble()
                is Number -> raw.toDouble()
                else -> return null
            }
            return if (d.isFinite()) d else null
        }
    }
}
