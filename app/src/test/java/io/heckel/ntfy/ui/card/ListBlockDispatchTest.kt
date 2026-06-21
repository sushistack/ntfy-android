package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.CardSpec
import io.heckel.ntfy.ui.card.body.ListSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for list block dispatch and dispatcher integration (AC 10, 11, 13).
 *
 * Verifies that a top-level list spec routes to [CardBodyRoute.Structured] with type LIST,
 * that the dispatch remains deterministic, and that malformed list data still produces a
 * route (not an exception) — the Story 3.1 safety boundary is owned by CardBodyBinder.
 */
class ListBlockDispatchTest {

    private val dispatcher = CardBodyDispatcher()

    // -------------------------------------------------------------------------
    // Top-level list dispatch (AC 10)
    // -------------------------------------------------------------------------

    @Test
    fun `list type with card tag produces Structured route`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list","ordered":false,"items":["a","b"]}"""
        )
        assertTrue("Expected Structured route for list, got: $route", route is CardBodyRoute.Structured)
        val structured = route as CardBodyRoute.Structured
        assertEquals(CardSpec.KnownType.LIST, structured.spec.type)
    }

    @Test
    fun `ordered list with card tag produces Structured route`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list","ordered":true,"items":["step 1","step 2"]}"""
        )
        assertTrue(route is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.LIST, (route as CardBodyRoute.Structured).spec.type)
    }

    @Test
    fun `list type without card tag falls through to Text`() {
        val route = dispatcher.dispatch(
            emptyList(),
            """{"type":"list","ordered":false,"items":["a"]}"""
        )
        assertTrue("Expected Text (no card tag), got: $route", route is CardBodyRoute.Text)
    }

    @Test
    fun `extra non-card tags do not prevent list dispatch`() {
        val route = dispatcher.dispatch(
            listOf("service:ci", "card", "v2"),
            """{"type":"list","ordered":true,"items":["build","test","deploy"]}"""
        )
        assertTrue(route is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.LIST, (route as CardBodyRoute.Structured).spec.type)
    }

    // -------------------------------------------------------------------------
    // ListSpec is extractable from dispatched route (AC 10)
    // -------------------------------------------------------------------------

    @Test
    fun `ListSpec from dispatched Structured route preserves ordered flag`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list","ordered":true,"items":["a","b","c"]}"""
        ) as CardBodyRoute.Structured
        val spec = ListSpec.from(route.spec.root)
        assertTrue(spec.ordered)
        assertEquals(listOf("a", "b", "c"), spec.items)
    }

    @Test
    fun `ListSpec from dispatched route with missing items is empty`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list"}"""
        ) as CardBodyRoute.Structured
        val spec = ListSpec.from(route.spec.root)
        assertFalse(spec.ordered)
        assertEquals(0, spec.items.size)
    }

    // -------------------------------------------------------------------------
    // Malformed list data still produces a Structured route (AC 11)
    // The dispatcher's job is gate + type; fallback rendering is CardBodyBinder's.
    // -------------------------------------------------------------------------

    @Test
    fun `list with null items field produces Structured route`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list","items":null}"""
        )
        assertTrue(route is CardBodyRoute.Structured)
        val spec = ListSpec.from((route as CardBodyRoute.Structured).spec.root)
        assertEquals(0, spec.items.size)
    }

    @Test
    fun `list with non-array items field produces Structured route`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list","items":"not-array"}"""
        )
        assertTrue(route is CardBodyRoute.Structured)
        val spec = ListSpec.from((route as CardBodyRoute.Structured).spec.root)
        assertEquals(0, spec.items.size)
    }

    // -------------------------------------------------------------------------
    // Structured route carries root JsonObject (AC 13)
    // -------------------------------------------------------------------------

    @Test
    fun `Structured route root contains type field`() {
        val route = dispatcher.dispatch(
            listOf("card"),
            """{"type":"list","ordered":false,"items":["x"]}"""
        ) as CardBodyRoute.Structured
        assertNotNull(route.spec.root)
        assertEquals("list", route.spec.root.get("type").asString)
    }
}
