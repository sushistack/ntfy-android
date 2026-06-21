package io.heckel.ntfy.ui.card.body

import com.google.gson.JsonObject

/**
 * Immutable dispatch-level representation of a structured card body.
 *
 * [type] is one of the four known top-level types: kv, list, chart, sections.
 * [root] is the full parsed JSON object retained for later renderers.
 */
data class CardSpec(
    val type: KnownType,
    val root: JsonObject,
) {
    enum class KnownType(val wire: String) {
        KV("kv"),
        LIST("list"),
        CHART("chart"),
        SECTIONS("sections"),
        ;

        companion object {
            private val BY_WIRE = values().associateBy { it.wire }
            fun fromWire(s: String): KnownType? = BY_WIRE[s]
        }
    }
}

/**
 * Fully parsed kv card body. Produced by [KvSpecParser]; consumed by KvBlockRenderer.
 *
 * [columns] is 1 (default) or 2. Any other value is treated as 1.
 * [rows] preserves payload order.
 */
data class KvSpec(
    val columns: Int,
    val rows: List<KvRow>,
)

/**
 * One row in a kv block.
 *
 * [icon] is an optional explicit icon key. When present it overrides [key] for icon resolution.
 * [status] recognizes "ok", "warn", "error"; any other value is treated as no semantic status.
 * [meter] is finite when the JSON field is a valid finite number; null otherwise.
 */
data class KvRow(
    val key: String,
    val value: String,
    val icon: String? = null,
    val status: String? = null,
    val meter: Double? = null,
)
