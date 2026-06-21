package io.heckel.ntfy.ui.card.body

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject

/**
 * Normalized model for a structured list block.
 *
 * [ordered] true → one-based decimal markers ("1.", "2.", …)
 *           false → bullet markers ("•")
 * [items]   deterministically coerced string values; never null, never drops entries.
 */
data class ListSpec(
    val ordered: Boolean,
    val items: List<String>,
) {
    companion object {
        private const val KEY_ORDERED = "ordered"
        private const val KEY_ITEMS = "items"

        /**
         * Extract and normalize a [ListSpec] from a parsed [CardSpec] root object.
         *
         * - [ordered] is true only when the JSON value is the boolean literal `true`.
         * - [items] is empty when the field is missing, null, or not a JSON array.
         * - Each array entry is coerced deterministically:
         *     strings → raw string value (no JSON quotes)
         *     everything else → compact JSON representation
         */
        fun from(root: JsonObject): ListSpec {
            val orderedEl: JsonElement? = root.get(KEY_ORDERED)
            val ordered = orderedEl != null &&
                orderedEl.isJsonPrimitive &&
                orderedEl.asJsonPrimitive.isBoolean &&
                orderedEl.asBoolean

            val itemsEl: JsonElement? = root.get(KEY_ITEMS)
            val items: List<String> = if (itemsEl != null && itemsEl.isJsonArray) {
                coerceItems(itemsEl.asJsonArray)
            } else {
                emptyList()
            }

            return ListSpec(ordered = ordered, items = items)
        }

        private fun coerceItems(array: JsonArray): List<String> {
            val result = mutableListOf<String>()
            for (element in array) {
                result += coerce(element)
            }
            return result
        }

        private fun coerce(el: JsonElement): String = when {
            el.isJsonNull || el == JsonNull.INSTANCE -> "null"
            el.isJsonPrimitive -> {
                val prim = el.asJsonPrimitive
                when {
                    prim.isString -> prim.asString
                    else -> el.toString() // compact JSON for numbers, booleans
                }
            }
            else -> el.toString() // compact JSON for objects and arrays
        }
    }
}
