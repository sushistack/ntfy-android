package io.heckel.ntfy.ui.card.chart

import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.CardSpec
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests: dispatch pipeline routes chart JSON through the dual-gate to Structured(CHART).
 */
class ChartDispatchIntegrationTest {

    private val dispatcher = CardBodyDispatcher()
    private val tags = listOf("card")

    @Test fun `valid chart JSON routes to Structured with CHART type`() {
        val body = """{"type":"chart","data":[{"value":12},{"value":34}]}"""
        val route = dispatcher.dispatch(tags, body)
        assertTrue(route is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.CHART, (route as CardBodyRoute.Structured).spec.type)
    }

    @Test fun `chart JSON without card tag routes to Text`() {
        val body = """{"type":"chart","data":[{"value":12}]}"""
        val route = dispatcher.dispatch(emptyList(), body)
        assertTrue(route is CardBodyRoute.Text)
    }

    @Test fun `chart JSON with all-invalid data still routes to Structured (parser handles null)`() {
        // The dispatcher only does dual-gate; ChartSpecParser handles empty-data null.
        val body = """{"type":"chart","data":[{"value":null}]}"""
        val route = dispatcher.dispatch(tags, body)
        // type == CHART passes gate 2; ChartBlockRenderer will produce no View.
        assertTrue(route is CardBodyRoute.Structured)
        assertEquals(CardSpec.KnownType.CHART, (route as CardBodyRoute.Structured).spec.type)
    }
}
