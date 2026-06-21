package io.heckel.ntfy.ui.card.body

import com.google.gson.JsonObject

/**
 * Parses a [CardSpec] whose [CardSpec.KnownType] is KV into a [KvSpec].
 *
 * All parsing is done here so KvBlockRenderer receives typed data only.
 * Returns null on any structural problem; callers fall back to raw text.
 */
object KvSpecParser {

    fun parse(root: JsonObject): KvSpec? {
        return try {
            val columnsEl = root.get("columns")
            val columns = if (columnsEl != null && columnsEl.isJsonPrimitive &&
                columnsEl.asJsonPrimitive.isNumber &&
                columnsEl.asInt == 2) 2 else 1

            val rowsEl = root.get("rows") ?: return KvSpec(columns, emptyList())
            if (!rowsEl.isJsonArray) return KvSpec(columns, emptyList())

            val rows = mutableListOf<KvRow>()
            for (el in rowsEl.asJsonArray) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val key = obj.get("key")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val value = obj.get("value")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val icon = obj.get("icon")?.takeIf { it.isJsonPrimitive }?.asString
                val status = obj.get("status")?.takeIf { it.isJsonPrimitive }?.asString
                val meterEl = obj.get("meter")
                val meter: Double? = if (meterEl != null && meterEl.isJsonPrimitive &&
                    meterEl.asJsonPrimitive.isNumber) {
                    val d = meterEl.asDouble
                    if (d.isFinite()) d else null
                } else null

                rows.add(KvRow(key = key, value = value, icon = icon, status = status, meter = meter))
            }
            KvSpec(columns, rows)
        } catch (_: Exception) {
            null
        }
    }
}
