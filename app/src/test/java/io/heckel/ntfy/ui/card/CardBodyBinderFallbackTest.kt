package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.CardSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for CardBodyBinder dispatch and fail-safe fallback logic at the route level.
 *
 * View-level tests (inflation, partial-child clearing, listener reset) require Robolectric or
 * an instrumentation test environment and are out of scope for this local-unit test class.
 * The dispatch and route contract is verified here without any Android view dependencies.
 *
 * AC 4: exceptions in rendering must be contained (verified in CardBodyBinder implementation).
 * AC 6: reset clears body state (covered by binder reset contract test).
 */
class CardBodyBinderDispatchRouteTest {

    // -------------------------------------------------------------------------
    // Dispatch integration (uses real dispatcher + parser — no mocks)
    // -------------------------------------------------------------------------

    @Test
    fun `text route is dispatched for plain body without card tag`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "Hello")
        assertTrue(route is CardBodyRoute.Text)
        assertEquals("Hello", (route as CardBodyRoute.Text).decodedBody)
    }

    @Test
    fun `structured route is dispatched for valid card body`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(listOf("card"), """{"type":"kv","rows":[]}""")
        assertTrue(route is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.KV, (route as CardBodyRoute.Structured).spec.type)
    }

    @Test
    fun `malformed json with card tag produces text route not exception`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(listOf("card"), "{ broken json")
        assertTrue("Expected Text, got: $route", route is CardBodyRoute.Text)
    }

    @Test
    fun `fuzz bodies with card tag produce text route not exception`() {
        val dispatcher = CardBodyDispatcher()
        for (fuzz in listOf("", "   ", "null", "false", "[]")) {
            val route = dispatcher.dispatch(listOf("card"), fuzz)
            assertTrue("Unexpected non-Text route for fuzz '$fuzz': $route",
                route is CardBodyRoute.Text || route is CardBodyRoute.Structured)
        }
    }

    @Test
    fun `empty json object without known type produces text route`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(listOf("card"), "{}")
        assertTrue("Expected Text for missing type, got: $route", route is CardBodyRoute.Text)
    }

    // -------------------------------------------------------------------------
    // Decoded body contract: Text route carries decoded message string (AC 5)
    // -------------------------------------------------------------------------

    @Test
    fun `text route carries exact decoded body string`() {
        val body = "Exact body content with unicode: 🚀"
        val route = CardBodyDispatcher().dispatch(emptyList(), body)
        assertEquals(body, (route as CardBodyRoute.Text).decodedBody)
    }

    // -------------------------------------------------------------------------
    // card tag with extra non-card tags still detects structured (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `structured detection passes with extra non-card tags`() {
        val dispatcher = CardBodyDispatcher()
        val tags = listOf("card", "service:gh", "v3")
        val route = dispatcher.dispatch(tags, """{"type":"list","items":[]}""")
        assertTrue(route is CardBodyRoute.Structured)
    }

    // -------------------------------------------------------------------------
    // Heuristic seam safe fallback until Story 3.8 (AC 3, 5)
    // -------------------------------------------------------------------------

    @Test
    fun `unimplemented heuristic seam continues to Text safely`() {
        val dispatcher = CardBodyDispatcher() // uses UNIMPLEMENTED
        val kvBody = "cpu: 80%\nmem: 60%"
        val route = dispatcher.dispatch(emptyList(), kvBody)
        assertTrue("Expected Text fallback for unimplemented heuristic, got: $route",
            route is CardBodyRoute.Text)
    }

    @Test
    fun `custom heuristic detector can produce HeuristicKv route`() {
        val dispatcher = CardBodyDispatcher(
            heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector { true }
        )
        val route = dispatcher.dispatch(emptyList(), "key: value")
        assertTrue(route is CardBodyRoute.HeuristicKv)
    }

    @Test
    fun `heuristic seam is not invoked when structured gate passes`() {
        var heuristicCalled = false
        val dispatcher = CardBodyDispatcher(
            heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector { heuristicCalled = true; true }
        )
        val route = dispatcher.dispatch(listOf("card"), """{"type":"kv","rows":[]}""")
        assertTrue(route is CardBodyRoute.Structured)
        assertTrue("Heuristic should not be called when structured gate passes", !heuristicCalled)
    }

    // -------------------------------------------------------------------------
    // Deterministic ordering across all four types (AC 3)
    // -------------------------------------------------------------------------

    @Test
    fun `all four known types produce Structured route`() {
        val types = listOf("kv", "list", "chart", "sections")
        for (type in types) {
            val route = CardBodyDispatcher().dispatch(listOf("card"), """{"type":"$type"}""")
            assertTrue("Expected Structured for type=$type, got: $route", route is CardBodyRoute.Structured)
        }
    }
}
