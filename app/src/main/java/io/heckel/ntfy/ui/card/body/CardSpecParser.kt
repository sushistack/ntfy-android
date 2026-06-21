package io.heckel.ntfy.ui.card.body

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

/**
 * Pure dual-gate parser for structured card bodies.
 *
 * Gate 1: tags list contains the exact literal string "card".
 * Gate 2: decoded body is a JSON object whose "type" string is a known KnownType.
 *
 * Any failure in either gate returns null (fallback path); no exception escapes.
 */
object CardSpecParser {

    private const val CARD_TAG = "card"
    private const val TYPE_KEY = "type"

    private val gson = Gson()

    /**
     * @param tags   list of individual tag strings (already split, not comma-joined)
     * @param decodedBody  the decoded message string (Base64 already resolved by caller)
     * @return [CardSpec] if both gates pass, null otherwise
     */
    fun parseCardSpec(tags: List<String>, decodedBody: String): CardSpec? {
        if (!tags.contains(CARD_TAG)) return null
        if (decodedBody.isEmpty()) return null

        return try {
            val element = gson.fromJson(decodedBody, com.google.gson.JsonElement::class.java)
                ?: return null
            if (!element.isJsonObject) return null
            val obj = element.asJsonObject

            val typeElement = obj.get(TYPE_KEY) ?: return null
            if (!typeElement.isJsonPrimitive || !typeElement.asJsonPrimitive.isString) return null
            val typeStr = typeElement.asString

            val knownType = CardSpec.KnownType.fromWire(typeStr) ?: return null
            CardSpec(type = knownType, root = obj)
        } catch (_: JsonParseException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
