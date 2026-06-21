package io.heckel.ntfy.ui.card.body

import com.google.gson.JsonObject

/**
 * Parses the `blocks` array of a top-level sections [CardSpec] into a [SectionsSpec].
 *
 * Allowlist: markdown, kv, list, chart.
 * Reject list: sections (recursion prevention), unknown type, missing type, non-object
 * entries, null entries, and any other malformed shape.
 *
 * The caller (SectionsBlockRenderer) receives only the admitted children; skipped entries
 * produce neither a view nor a spacer (AC 4, 5, 6).
 *
 * Returns a [SectionsSpec] with an empty list when `blocks` is missing, null, non-array,
 * or empty — never returns null (AC 6).
 */
object SectionsSpecParser {

    private const val KEY_BLOCKS = "blocks"
    private const val KEY_TYPE = "type"

    private const val TYPE_MARKDOWN = "markdown"
    private const val TYPE_KV = "kv"
    private const val TYPE_LIST = "list"
    private const val TYPE_CHART = "chart"
    private const val TYPE_SECTIONS = "sections"

    private const val KEY_TEXT = "text"

    fun parse(root: JsonObject): SectionsSpec {
        val blocksEl = root.get(KEY_BLOCKS)
        if (blocksEl == null || !blocksEl.isJsonArray) return SectionsSpec(emptyList())

        val array = blocksEl.asJsonArray
        if (array.size() == 0) return SectionsSpec(emptyList())

        val children = mutableListOf<ChildBlock>()
        for (element in array) {
            val child = parseChild(element) ?: continue
            children += child
        }
        return SectionsSpec(children)
    }

    private fun parseChild(element: com.google.gson.JsonElement?): ChildBlock? {
        if (element == null || element.isJsonNull || !element.isJsonObject) return null
        val obj = element.asJsonObject

        val typeEl = obj.get(KEY_TYPE) ?: return null
        if (!typeEl.isJsonPrimitive || !typeEl.asJsonPrimitive.isString) return null
        val type = typeEl.asString

        return when (type) {
            TYPE_MARKDOWN -> {
                val textEl = obj.get(KEY_TEXT)
                val text = if (textEl != null && textEl.isJsonPrimitive && textEl.asJsonPrimitive.isString) {
                    textEl.asString
                } else {
                    ""
                }
                ChildBlock.Markdown(text)
            }
            TYPE_KV -> ChildBlock.Kv(obj)
            TYPE_LIST -> ChildBlock.List(obj)
            TYPE_CHART -> ChildBlock.Chart(obj)
            TYPE_SECTIONS -> null  // explicit recursion prevention (AC 4)
            else -> null           // unknown type → skip (AC 5)
        }
    }
}
