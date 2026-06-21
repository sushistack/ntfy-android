package io.heckel.ntfy.ui.card.body

import com.google.gson.JsonObject

/**
 * Parsed representation of a top-level sections block.
 *
 * [children] is the ordered list of admitted child blocks.
 * Only [ChildBlock.Markdown], [ChildBlock.Kv], [ChildBlock.List], and [ChildBlock.Chart]
 * are admitted. Nested sections, unknown types, and malformed entries are excluded at parse
 * time, which makes recursion structurally impossible (AC 4, 5).
 *
 * Array order is preserved; no grouping, sorting, or deduplication is applied (AC 1).
 */
data class SectionsSpec(
    val children: List<ChildBlock>,
)

/**
 * Admitted child block types for a sections block.
 *
 * [Markdown] carries the raw text string; it is child-only and must not appear in the
 * top-level known-type gate.
 * [Kv], [List], and [Chart] each carry their owning JSON object for forwarding to the
 * existing standalone renderers without re-parsing at the sections level.
 */
sealed class ChildBlock {
    data class Markdown(val text: String) : ChildBlock()
    data class Kv(val root: JsonObject) : ChildBlock()
    data class List(val root: JsonObject) : ChildBlock()
    data class Chart(val root: JsonObject) : ChildBlock()
}
