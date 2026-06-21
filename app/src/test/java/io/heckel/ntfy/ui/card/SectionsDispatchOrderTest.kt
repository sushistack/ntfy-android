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
 * Pure JVM tests for sections child dispatch order via [SectionsSpecParser] (AC 1, 4, 5, 9).
 *
 * Validates that:
 * - Children are admitted in exact JSON array order.
 * - Nested sections, unknown types, null entries, and malformed entries are excluded.
 * - Supported siblings before and after excluded entries are still dispatched.
 * - Missing/null/non-array/empty blocks produce zero admitted children.
 * - All four types dispatch from the top-level sections gate (AC 1).
 *
 * The SectionsBlockRenderer itself requires View inflation and is covered in
 * SectionsRendererArchitectureTest and the recycling contract tests.
 */
class SectionsDispatchOrderTest {

    private val gson = Gson()
    private val dispatcher = CardBodyDispatcher()

    private fun parse(json: String) = SectionsSpecParser.parse(
        gson.fromJson(json, JsonObject::class.java)
    )

    private fun dispatchRoute(json: String): CardBodyRoute.Structured {
        val route = dispatcher.dispatch(listOf("card"), json)
        assertTrue("Expected Structured route, got $route", route is CardBodyRoute.Structured)
        return route as CardBodyRoute.Structured
    }

    // -------------------------------------------------------------------------
    // Top-level sections gate produces Structured route (AC 1, 9)
    // -------------------------------------------------------------------------

    @Test
    fun `sections type with card tag produces Structured route`() {
        val route = dispatcher.dispatch(listOf("card"), """{"type":"sections","blocks":[]}""")
        assertTrue(route is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.SECTIONS, (route as CardBodyRoute.Structured).spec.type)
    }

    @Test
    fun `sections without card tag falls through to Text`() {
        val route = dispatcher.dispatch(emptyList(), """{"type":"sections","blocks":[]}""")
        assertTrue(route is CardBodyRoute.Text)
    }

    // -------------------------------------------------------------------------
    // Canonical mixed payload: all four types in source order (AC 1)
    // -------------------------------------------------------------------------

    @Test
    fun `canonical mixed payload admits all four child types in source order`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "markdown", "text": "## Build failed"},
                {"type": "kv", "rows": [{"key": "Stage", "value": "test", "status": "error"}]},
                {"type": "list", "ordered": true, "items": ["Compile", "Test"]},
                {"type": "chart", "kind": "bar", "data": [{"label": "tests", "value": 252}]}
              ]
            }
        """)
        assertEquals(4, spec.children.size)
        assertTrue("Expected Markdown first", spec.children[0] is ChildBlock.Markdown)
        assertTrue("Expected Kv second",      spec.children[1] is ChildBlock.Kv)
        assertTrue("Expected List third",     spec.children[2] is ChildBlock.List)
        assertTrue("Expected Chart fourth",   spec.children[3] is ChildBlock.Chart)
        assertEquals("## Build failed", (spec.children[0] as ChildBlock.Markdown).text)
    }

    @Test
    fun `reverse order is preserved`() {
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
    fun `repeated child types preserve their positions`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "kv", "rows": []},
                {"type": "kv", "rows": []},
                {"type": "list", "items": ["a"]},
                {"type": "kv", "rows": []}
              ]
            }
        """)
        assertEquals(4, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
        assertTrue(spec.children[1] is ChildBlock.Kv)
        assertTrue(spec.children[2] is ChildBlock.List)
        assertTrue(spec.children[3] is ChildBlock.Kv)
    }

    // -------------------------------------------------------------------------
    // Nested sections rejection (AC 4)
    // -------------------------------------------------------------------------

    @Test
    fun `nested sections in middle is excluded, outer siblings remain`() {
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
    fun `nested sections at start is excluded, later siblings remain`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "sections", "blocks": []},
                {"type": "kv", "rows": [{"key": "k", "value": "v"}]},
                {"type": "chart", "kind": "bar", "data": []}
              ]
            }
        """)
        assertEquals(2, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
        assertTrue(spec.children[1] is ChildBlock.Chart)
    }

    @Test
    fun `nested sections at end is excluded, prior siblings remain`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "list", "items": ["a", "b"]},
                {"type": "sections", "blocks": [{"type": "markdown", "text": "deep"}]}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.List)
    }

    // -------------------------------------------------------------------------
    // Unknown / missing / malformed entries skipped (AC 5)
    // -------------------------------------------------------------------------

    @Test
    fun `unknown type in middle is skipped, siblings render`() {
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
    fun `missing type field is skipped, siblings render`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"noType": "bad"},
                {"type": "kv", "rows": []},
                {"missingType": true},
                {"type": "markdown", "text": "ok"}
              ]
            }
        """)
        assertEquals(2, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Kv)
        assertTrue(spec.children[1] is ChildBlock.Markdown)
    }

    @Test
    fun `null entry is skipped, siblings render`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "chart", "kind": "bar", "data": []},
                null,
                {"type": "list", "items": ["x"]}
              ]
            }
        """)
        assertEquals(2, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.Chart)
        assertTrue(spec.children[1] is ChildBlock.List)
    }

    @Test
    fun `primitive entries are skipped, admitted children remain`() {
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
    fun `non-string type field is skipped`() {
        val spec = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": 99},
                {"type": "list", "items": ["c"]}
              ]
            }
        """)
        assertEquals(1, spec.children.size)
        assertTrue(spec.children[0] is ChildBlock.List)
    }

    @Test
    fun `all entries unsupported yields zero children`() {
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
    // Missing / null / non-array / empty blocks (AC 6)
    // -------------------------------------------------------------------------

    @Test
    fun `missing blocks field produces zero children`() {
        val spec = parse("""{"type":"sections"}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `null blocks field produces zero children`() {
        val spec = parse("""{"type":"sections","blocks":null}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `non-array blocks field produces zero children`() {
        val spec = parse("""{"type":"sections","blocks":"not-an-array"}""")
        assertEquals(0, spec.children.size)
    }

    @Test
    fun `empty blocks array produces zero children`() {
        val spec = parse("""{"type":"sections","blocks":[]}""")
        assertEquals(0, spec.children.size)
    }

    // -------------------------------------------------------------------------
    // Recycling: consecutive parses are independent (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `consecutive parses produce independent specs`() {
        val long = parse("""
            {
              "type": "sections",
              "blocks": [
                {"type": "markdown", "text": "a"},
                {"type": "kv", "rows": []},
                {"type": "list", "items": ["x"]},
                {"type": "chart", "kind": "bar", "data": []}
              ]
            }
        """)
        val short = parse("""{"type":"sections","blocks":[{"type":"markdown","text":"b"}]}""")
        val empty = parse("""{"type":"sections","blocks":[]}""")

        assertEquals(4, long.children.size)
        assertEquals(1, short.children.size)
        assertEquals(0, empty.children.size)
        // First parse unchanged
        assertEquals(4, long.children.size)
    }

    // -------------------------------------------------------------------------
    // Architecture guard: sections is not a valid markdown top-level type (AC 3)
    // -------------------------------------------------------------------------

    @Test
    fun `markdown is not a top-level known card type`() {
        // CardSpec.KnownType must not include MARKDOWN; sections gate must not admit markdown
        val topLevelTypes = CardSpec.KnownType.values().map { it.wire }
        assertTrue("kv must be top-level", topLevelTypes.contains("kv"))
        assertTrue("list must be top-level", topLevelTypes.contains("list"))
        assertTrue("chart must be top-level", topLevelTypes.contains("chart"))
        assertTrue("sections must be top-level", topLevelTypes.contains("sections"))
        assertTrue("markdown must NOT be a top-level type",
            !topLevelTypes.contains("markdown"))
    }

    @Test
    fun `markdown type with card tag produces Text route (not Structured)`() {
        // markdown is child-only; the top-level gate must reject it.
        val route = dispatcher.dispatch(listOf("card"), """{"type":"markdown","text":"hi"}""")
        assertTrue("markdown must fall to Text, got: $route", route is CardBodyRoute.Text)
    }
}
