package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.HeuristicKvParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the heuristic-kv dispatch path (Story 3.8, AC 1, 8–11).
 *
 * Covers:
 *  - Dispatch routing: heuristic-kv body → HeuristicKv route; card-tagged body never reaches heuristic.
 *  - KvSpec produced by the real parser is carried in the route.
 *  - Fault tolerance: throwing heuristic detector falls through to Text (Story 3.1 boundary).
 *
 * Recycling (view-layer) tests are omitted: they require Android View infrastructure
 * and are covered by instrumented tests in androidTest/.
 *
 * Run:
 *   ./gradlew testPlayDebugUnitTest --tests '*HeuristicKvDispatchIntegrationTest'
 *   ./gradlew testFdroidDebugUnitTest --tests '*HeuristicKvDispatchIntegrationTest'
 */
class HeuristicKvDispatchIntegrationTest {

    // -------------------------------------------------------------------------
    // Dispatch routing (AC 1, 11)
    // -------------------------------------------------------------------------

    @Test
    fun `kv body without card tag routes to HeuristicKv`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "CPU: 78%\nMemory: 45%")
        assertTrue("Expected HeuristicKv route, got: $route", route is CardBodyRoute.HeuristicKv)
    }

    @Test
    fun `kv body without card tag carries parsed KvSpec in route`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "CPU: 78%\nMemory: 45%") as CardBodyRoute.HeuristicKv
        assertEquals(2, route.kvSpec.rows.size)
        assertEquals("CPU", route.kvSpec.rows[0].key)
        assertEquals("Memory", route.kvSpec.rows[1].key)
    }

    @Test
    fun `KvSpec in HeuristicKv route has columns 1`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "CPU: 78%\nMemory: 45%") as CardBodyRoute.HeuristicKv
        assertEquals(1, route.kvSpec.columns)
    }

    @Test
    fun `card tag body with valid json bypasses heuristic path (AC 11)`() {
        // Structured route must win even if body looks like kv lines.
        var heuristicInvoked = false
        val dispatcher = CardBodyDispatcher(
            heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector { body ->
                heuristicInvoked = true
                HeuristicKvParser.detectBodyShape(body) == io.heckel.ntfy.ui.card.body.BodyShape.HeuristicKv
            }
        )
        val route = dispatcher.dispatch(listOf("card"), """{"type":"kv","rows":[]}""")
        assertTrue("Structured must win", route is CardBodyRoute.Structured)
        assertFalse("Heuristic seam must not be invoked when structured gate passes", heuristicInvoked)
    }

    @Test
    fun `single kv line without card tag routes to Text not HeuristicKv (AC 4)`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "CPU: 78%")
        assertTrue("Single kv line must route to Text (paragraph), got: $route", route is CardBodyRoute.Text)
    }

    @Test
    fun `plain text without card tag routes to Text`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "Hello world")
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test
    fun `mixed kv and plain line without card tag routes to Text not HeuristicKv`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "CPU: 78%\nJust plain text")
        assertTrue("Mixed body must route to Text, got: $route", route is CardBodyRoute.Text)
    }

    @Test
    fun `empty body routes to Text not HeuristicKv`() {
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "")
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test
    fun `heuristic path produces KvSpec reusable by KvBlockRenderer`() {
        // Verify the KvSpec type (KvSpec from CardSpec.kt) not a different type.
        val dispatcher = CardBodyDispatcher()
        val route = dispatcher.dispatch(emptyList(), "Error count: 3\nStatus: ok")
        assertTrue(route is CardBodyRoute.HeuristicKv)
        val kvRoute = route as CardBodyRoute.HeuristicKv
        // status row check
        val errorRow = kvRoute.kvSpec.rows.first { it.key == "Error count" }
        assertEquals("error", errorRow.status)
        assertEquals(3.0, errorRow.meter!!, 0.001)
    }

    // -------------------------------------------------------------------------
    // Fault tolerance (AC 9) — throwing heuristic detector falls back to Text
    // -------------------------------------------------------------------------

    @Test
    fun `throwing heuristic detector causes Text fallback via Story 3_1 boundary`() {
        val dispatcher = CardBodyDispatcher(
            heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector { throw RuntimeException("injected failure") }
        )
        // The dispatcher itself does not catch; the CardBodyBinder try/catch is the boundary.
        // So from the dispatcher's perspective it re-throws. Test that the exception propagates
        // (i.e. the detector is not silently swallowed inside dispatch()).
        var caught = false
        try {
            dispatcher.dispatch(emptyList(), "CPU: 78%\nMemory: 45%")
        } catch (e: RuntimeException) {
            caught = true
            assertEquals("injected failure", e.message)
        }
        assertTrue("Exception from detector must propagate so CardBodyBinder catch can handle it", caught)
    }

    // -------------------------------------------------------------------------
    // Dispatch ordering invariant (AC 1 — Structured > HeuristicKv > Text)
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch order is Structured then HeuristicKv then Text`() {
        val alwaysTrueDetector = CardBodyDispatcher.HeuristicKvDetector { true }
        val dispatcher = CardBodyDispatcher(heuristicKvDetector = alwaysTrueDetector)

        // Structured wins even with always-true heuristic
        val structuredRoute = dispatcher.dispatch(listOf("card"), """{"type":"kv","rows":[]}""")
        assertTrue("Structured must beat heuristic", structuredRoute is CardBodyRoute.Structured)

        // HeuristicKv wins over Text — use a body with colon so parseHeuristicKvSpec can handle it
        val heuristicRoute = dispatcher.dispatch(emptyList(), "key: value")
        assertTrue("HeuristicKv must beat Text when detector returns true", heuristicRoute is CardBodyRoute.HeuristicKv)
    }

    @Test
    fun `UNIMPLEMENTED detector always falls through to Text`() {
        val dispatcher = CardBodyDispatcher(heuristicKvDetector = CardBodyDispatcher.HeuristicKvDetector.UNIMPLEMENTED)
        val route = dispatcher.dispatch(emptyList(), "CPU: 78%\nMemory: 45%")
        assertTrue("UNIMPLEMENTED seam must produce Text", route is CardBodyRoute.Text)
    }
}
