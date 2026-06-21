package io.heckel.ntfy.ui.card

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.heckel.ntfy.ui.card.body.ChildBlock
import io.heckel.ntfy.ui.card.body.SectionsSpec
import io.heckel.ntfy.ui.card.body.SectionsSpecParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [SectionsSpecParser] (AC 1, 3–6, 9).
 *
 * Verifies child allowlist, source-order preservation, nested-sections rejection,
 * unknown/malformed entry skipping, and missing/null/non-array blocks handling.
 */
class SectionsSpecParserTest {

    private val gson = Gson()

    private fun parse(json: String): SectionsSpec {
        val root = gson.fromJson(json, JsonObject::class.java)
        return SectionsSpecParser.parse(root)
    }

    // -------------------------------------------------------------------------
    // Allowlist: markdown, kv, list, chart admitted (AC 1, 3)
    // -------------------------------------------------------------------------

    @Test
    fun `canonical mixed payload admits all four child types in source order`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "markdown", "text": "## Build failed"},
                {"type": "kv", "rows": [{"key": "Stage", "value": "test"}]},
                {"type": "list", "ordered": true, "items": ["Compile", "Test"]},
                {"type": "chart", "kind": "bar", "data": [{"label": "test", "value": 252}]}
              ]
            }
        """)
        assertEquals(4, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Markdown)
        assertTrue(spec.children[1] is ChildBlock.Kv)
        assertTrue(spec.children[2] is ChildBlock.List)
        assertTrue(spec.children[3] is ChildBlock.Chart)
    }

    @Test
    fun `markdown child carries the text field`() {
        val spec = parse("""{"type":"sections","blocks":[{"type":"markdown","text":"hello **world**"}]}""")
        val md = spec.children.single() as ChildBlock.Markdown
        assertEquals("hello **world**", md.text)
    }

    @Test
    fun `markdown child with missing text field yields empty string`() {
        val spec = parse("""{"type":"sections","blocks":[{"type":"markdown"}]}""")
        val md = spec.children.single() as ChildBlock.Markdown
        assertEquals("", md.text)
    }

    @Test
    fun `kv child is admitted`() {
        val spec = parse("""{"type":"sections","blocks":[{"type":"kv","rows":[]}]}""")
        assertTrue(spec.children.single() is ChildBlock.Kv)
    }

    @Test
    fun `list child is admitted`() {
        val spec = parse("""{"type":"sections","blocks":[{"type":"list","items":["a"]}]}""")
        assertTrue(spec.children.single() is ChildBlock.List)
    }

    @Test
    fun `chart child is admitted`() {
        val spec = parse("""{"type":"sections","blocks":[{"type":"chart","kind":"bar","data":[]}]}""")
        assertTrue(spec.children.single() is ChildBlock.Chart)
    }

    // -------------------------------------------------------------------------
    // Source order preservation (AC 1)
    // -------------------------------------------------------------------------

    @Test
    fun `children are returned in exact JSON array order`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "chart", "kind": "bar", "data": []},
                {"type": "list", "items": ["x"]},
                {"type": "markdown", "text": "note"},
                {"type": "kv", "rows": []}
              ]
            }
        """)
        assertEquals(4, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Chart)
        assertTrue(spec.children[1] is ChildBlock.List)
        assertTrue(spec.children[2] is ChildBlock.Markdown)
        assertTrue(spec.children[3] is ChildBlock.Kv)
    }

    @Test
    fun `repeated child types retain their positions`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "kv", "rows": []},
                {"type": "kv", "rows": []},
                {"type": "list", "items": ["a"]}
              ]
            }
        """)
        assertEquals(3, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
        assertTrue(spec.children[1] is ChildBlock.Kv)
        assertTrue(spec.children[2] is ChildBlock.List)
    }

    // -------------------------------------------------------------------------
    // Nested sections rejection (AC 4)
    // -------------------------------------------------------------------------

    @Test
    fun `nested sections block is silently skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "markdown", "text": "before"},
                {"type": "sections", "blocks": [{"type": "kv", "rows": []}]},
                {"type": "list", "items": ["after"]}
              ]
            }
        """)
        assertEquals(2, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Markdown)
        assertTrue(spec.children[1] is ChildBlock.List)
    }

    @Test
    fun `nested sections at first position still admits siblings`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "sections", "blocks": []},
                {"type": "kv", "rows": []}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
    }

    @Test
    fun `nested sections at last position still admits prior siblings`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "list", "items": ["a"]},
                {"type": "sections", "blocks": []}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.List)
    }

    // -------------------------------------------------------------------------
    // Unknown / missing / malformed child entries skipped (AC 5)
    // -------------------------------------------------------------------------

    @Test
    fun `unknown type is skipped, siblings remain`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "kv", "rows": []},
                {"type": "unknown_future_type"},
                {"type": "list", "items": ["b"]}
              ]
            }
        """)
        assertEquals(2, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
        assertTrue(spec.children[1] is ChildBlock.List)
    }

    @Test
    fun `entry with missing type field is skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"noType": "oops"},
                {"type": "kv", "rows": []}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
    }

    @Test
    fun `null entry in blocks array is skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "kv", "rows": []},
                null,
                {"type": "list", "items": ["x"]}
              ]
            }
        """)
        assertEquals(2, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
        assertTrue(spec.children[1] is ChildBlock.List)
    }

    @Test
    fun `primitive entry in blocks array is skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [42, "string", true, {"type": "markdown", "text": "ok"}]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Markdown)
    }

    @Test
    fun `array entry in blocks array is skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                [1, 2, 3],
                {"type": "chart", "kind": "bar", "data": []}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Chart)
    }

    @Test
    fun `entry with non-string type field is skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": 42},
                {"type": "list", "items": ["c"]}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.List)
    }

    // -------------------------------------------------------------------------
    // Missing / null / non-array / empty blocks (AC 6)
    // -------------------------------------------------------------------------

    @Test
    fun `missing blocks field returns empty SectionsSpec without crash`() {
        val spec = parse("""{"type":"sections"}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `null blocks field returns empty SectionsSpec without crash`() {
        val spec = parse("""{"type":"sections","blocks":null}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `non-array blocks field returns empty SectionsSpec without crash`() {
        val spec = parse("""{"type":"sections","blocks":"not-an-array"}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `empty blocks array returns empty SectionsSpec without crash`() {
        val spec = parse("""{"type":"sections","blocks":[]}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `blocks containing only unsupported entries returns empty list`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "sections", "blocks": []},
                {"type": "future"},
                null,
                42
              ]
            }
        """)
        assertEquals(0, spec.children.size)
    }

    // -------------------------------------------------------------------------
    // Child JsonObject integrity — kv, list, chart roots forwarded intact (AC 1, 9)
    // -------------------------------------------------------------------------

    @Test
    fun `kv child root preserves rows field`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [{"type": "kv", "rows": [{"key": "K", "value": "V"}]}]
            }
        """)
        val kv = spec.children.single() as ChildBlock.Kv
        assertTrue(kv.root.has("rows"))
        val rows = kv.root.getAsJsonArray("rows")
        assertEquals(1, rows.size())
    }

    @Test
    fun `list child root preserves items field`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [{"type": "list", "ordered": true, "items": ["one", "two"]}]
            }
        """)
        val list = spec.children.single() as ChildBlock.List
        assertTrue(list.root.has("items"))
        assertEquals(2, list.root.getAsJsonArray("items").size())
    }

    @Test
    fun `chart child root preserves kind and data fields`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [{"type": "chart", "kind": "line", "data": [{"label": "a", "value": 1}]}]
            }
        """)
        val chart = spec.children.single() as ChildBlock.Chart
        assertEquals("line", chart.root.get("kind").asString)
    }
}
