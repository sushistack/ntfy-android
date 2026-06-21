package io.heckel.ntfy.ui.card.chart

import com.google.gson.JsonObject

/**
 * Parses a validated chart [JsonObject] (type == "chart") into a [ChartSpec].
 *
 * Returns null for missing/empty data (AC 8 — no View mounted for zero valid points).
 * Never throws; parsing errors return null so the dispatch seam falls back to text.
 */
object ChartSpecParser {

    private const val KEY_KIND = "kind"
    private const val KEY_UNIT = "unit"
    private const val KEY_DATA = "data"
    private const val KEY_VALUE = "value"
    private const val KEY_LABEL = "label"

    fun parse(root: JsonObject): ChartSpec? {
        return try {
            val kind = root.get(KEY_KIND)?.takeIf { it.isJsonPrimitive }?.asString
            val unit = root.get(KEY_UNIT)?.takeIf { it.isJsonPrimitive }?.asString

            val dataEl = root.get(KEY_DATA) ?: return null
            if (!dataEl.isJsonArray) return null
            val dataArray = dataEl.asJsonArray

            val rawPoints = mutableListOf<RawChartPoint>()
            for (el in dataArray) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val rawValue = extractRawValue(obj)
                val label = obj.get(KEY_LABEL)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                rawPoints.add(RawChartPoint(rawValue = rawValue, label = label))
            }

            val validPoints = ChartSpec.normalize(rawPoints)
            if (validPoints.isEmpty()) return null

            ChartSpec(kind = kind, unit = unit, points = validPoints)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRawValue(obj: JsonObject): Any? {
        val el = obj.get(KEY_VALUE) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asDouble
            else -> null
        }
    }
}
