package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.CardSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for CardBodyDispatcher route precedence and seam behavior.
 *
 * AC 3: dispatch order is deterministic — Structured → HeuristicKv → Text.
 * AC 5: unimplemented heuristic seam safely continues to Text.
 */
class CardBodyDispatcherTest {

    private val defaultDispatcher = CardBodyDispatcher()

    // -------------------------------------------------------------------------
    // Structured route (AC 3)
    // -------------------------------------------------------------------------

    @Test
    fun `valid card tag and json returns Structured route`() {
        val route = defaultDispatcher.dispatch(listOf("card"), """{"type":"kv","rows":[]}""")
        assertTrue(route is CardBodyRoute.Structured)
        val structured = route as CardBodyRoute.Structured
        assertEquals(CardSpec.KnownType.KV, structured.spec.type)
    }

    @Test
    fun `structured route has precedence over plain text`() {
        // Even if the body could be treated as text, structured wins when gates pass.
        val route = defaultDispatcher.dispatch(listOf("card"), """{"type":"sections","blocks":[]}""")
        assertTrue("Expected Structured, got: $route", route is CardBodyRoute.Structured)
    }

    // -------------------------------------------------------------------------
    // Text fallback (AC 3, 5)
    // -------------------------------------------------------------------------

    @Test
    fun `no card tag returns Text route`() {
        val route = defaultDispatcher.dispatch(emptyList(), "Hello world")
        assertTrue(route is CardBodyRoute.Text)
        assertEquals("Hello world", (route as CardBodyRoute.Text).decodedBody)
    }

    @Test
    fun `invalid json with card tag returns Text route`() {
        val route = defaultDispatcher.dispatch(listOf("card"), "not json")
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test
    fun `unknown type with card tag returns Text route`() {
        val route = defaultDispatcher.dispatch(listOf("card"), """{"type":"unknown"}""")
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test
    fun `empty body returns Text route`() {
        val route = defaultDispatcher.dispatch(listOf("card"), "")
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test
    fun `plain text without card tag returns Text with decoded body`() {
        val body = "This is a plain notification."
        val route = defaultDispatcher.dispatch(emptyList(), body)
        assertTrue(route is CardBodyRoute.Text)
        assertEquals(body, (route as CardBodyRoute.Text).decodedBody)
    }

    // -------------------------------------------------------------------------
    // HeuristicKv seam: unimplemented falls through to Text (AC 3, 5)
    // -------------------------------------------------------------------------

    @Test
    fun `UNIMPLEMENTED detector returns Text not HeuristicKv`() {
        // Inject UNIMPLEMENTED explicitly; default is now the real Story 3.8 detector.
        val dispatcher = CardBodyDispatcher(heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector.UNIMPLEMENTED)
        val multiline = "key1: value1\nkey2: value2"
        val route = dispatcher.dispatch(emptyList(), multiline)
        assertTrue("UNIMPLEMENTED seam must not produce HeuristicKv", route !is CardBodyRoute.HeuristicKv)
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test
    fun `custom heuristic detector can produce HeuristicKv route`() {
        // Verify seam wiring: a real Story 3.8 implementation can inject a true detector.
        val dispatcher = CardBodyDispatcher(heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector { true })
        val route = dispatcher.dispatch(emptyList(), "key: value")
        assertTrue(route is CardBodyRoute.HeuristicKv)
    }

    @Test
    fun `heuristic seam is not invoked when structured gate passes`() {
        // Structured must win over heuristic — even with an always-true detector.
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
            val route = defaultDispatcher.dispatch(listOf("card"), """{"type":"$type"}""")
            assertTrue("Expected Structured for type=$type, got: $route", route is CardBodyRoute.Structured)
        }
    }
}
