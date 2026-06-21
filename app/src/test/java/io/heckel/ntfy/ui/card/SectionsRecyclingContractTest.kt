package io.heckel.ntfy.ui.card

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.CardSpec
import io.heckel.ntfy.ui.card.body.ChildBlock
import io.heckel.ntfy.ui.card.body.SectionsSpecParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM contract tests for sections recycling and rebind behavior (AC 7).
 *
 * Verifies at the spec-parse level that:
 * - Rebinding from a long sections payload to a shorter one produces correct child counts.
 * - Rebinding to empty sections produces zero children.
 * - Rebinding to a different body type produces a different route type.
 * - Each parse is stateless; no cross-call contamination.
 *
 * View-level clearing (removeAllViews on rebind) is part of SectionsBlockRenderer and
 * confirmed by its `container.removeAllViews()` call at the start of renderSections.
 */
class SectionsRecyclingContractTest {

    private val gson = Gson()
    private val dispatcher = CardBodyDispatcher()

    private fun parse(json: String) = SectionsSpecParser.parse(
        gson.fromJson(json, JsonObject::class.java)
    )

    private fun dispatch(json: String) = dispatcher.dispatch(listOf("card"), json)

    // -------------------------------------------------------------------------
    // Rebind: long → shorter → empty → different type (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `rebind long sections payload to shorter produces correct child count`() {
        val long = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "markdown", "text": "a"},
                {"type": "kv", "rows": []},
                {"type": "list", "items": ["x","y","z"]},
                {"type": "chart", "kind": "bar", "data": []}
              ]
            }
        """)
        assertEquals(4, long.children.size)

        val shorter = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "markdown", "text": "b"},
                {"type": "list", "items": ["a"]}
              ]
            }
        """)
        assertEquals(2, shorter.children.size)
        // Prior parse not contaminated
        assertEquals(4, long.children.size)
    }

    @Test
    fun `rebind to empty sections produces zero children`() {
        val full = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "kv", "rows": [{"key": "k", "value": "v"}]},
                {"type": "list", "items": ["item"]}
              ]
            }
        """)
        assertEquals(2, full.children.size)

        val empty = parse("""{"type":"sections","blocks":[]}""")
        assertEquals(0, empty.children.size)
    }

    @Test
    fun `rebind to null blocks produces zero children`() {
        val full = parse("""{"type":"sections","blocks":[{"type":"markdown","text":"x"}]}""")
        assertEquals(1, full.children.size)

        val nullBlocks = parse("""{"type":"sections","blocks":null}""")
        assertEquals(0, nullBlocks.children.size)
    }

    @Test
    fun `rebind from sections to kv produces Structured KV route`() {
        val sectionsRoute = dispatch("""{"type":"sections","blocks":[]}""")
        assertTrue(sectionsRoute is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.SECTIONS, (sectionsRoute as CardBodyRoute.Structured).spec.type)

        val kvRoute = dispatch("""{"type":"kv","rows":[]}""")
        assertTrue(kvRoute is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.KV, (kvRoute as CardBodyRoute.Structured).spec.type)
    }

    @Test
    fun `rebind from sections to plain text produces Text route`() {
        val sectionsRoute = dispatch("""{"type":"sections","blocks":[{"type":"markdown","text":"msg"}]}""")
        assertTrue(sectionsRoute is CardBodyRoute.Structured)

        val textRoute = dispatcher.dispatch(emptyList(), "Hello plain world")
        assertTrue(textRoute is CardBodyRoute.Text)
    }

    // -------------------------------------------------------------------------
    // Stateless parser: consecutive calls are independent (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `consecutive sections parses are independent`() {
        val p1 = parse("""{"type":"sections","blocks":[{"type":"kv","rows":[]},{"type":"list","items":["a"]}]}""")
        val p2 = parse("""{"type":"sections","blocks":[{"type":"chart","kind":"bar","data":[]}]}""")
        val p3 = parse("""{"type":"sections","blocks":[]}""")

        assertEquals(2, p1.children.size)
        assertEquals(1, p2.children.size)
        assertEquals(0, p3.children.size)
        // All remain unchanged after further parses
        assertEquals(2, p1.children.size)
        assertEquals(1, p2.children.size)
    }

    // -------------------------------------------------------------------------
    // Child type identity preserved across rebinds (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `first child type is correct after two successive parses`() {
        parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "chart", "kind": "line", "data": []},
                {"type": "kv", "rows": []}
              ]
            }
        """)
        // Second parse should independently produce Markdown first
        val second = parse("""
            {
              "type": "sections",
              "blocks": [{"type": "markdown", "text": "second parse"}]
            }
        """)
        assertEquals(1, second.children.size)
        assertTrue(second.children[0] is ChildBlock.Markdown)
        assertEquals("second parse", (second.children[0] as ChildBlock.Markdown).text)
    }
}
