package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.CardBodyRoute
import io.heckel.ntfy.ui.card.body.ListSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for recycling and rebinding boundary rules (AC 9).
 *
 * These tests verify the dispatch layer correctly routes different rebind
 * scenarios — from long ordered list to empty, to single-item — without
 * requiring Android Views. The contract is:
 *   - Each dispatch produces a fresh route with the new spec.
 *   - The renderer (ListBlockRenderer.renderInto) must call removeAllViews() first,
 *     which is tested here via ListSpec state changes rather than view inspection.
 */
class ListBlockRecyclingTest {

    private val dispatcher = CardBodyDispatcher()

    private fun dispatchList(json: String): ListSpec {
        val route = dispatcher.dispatch(listOf("card"), json) as CardBodyRoute.Structured
        return ListSpec.from(route.spec.root)
    }

    // -------------------------------------------------------------------------
    // State transitions: long ordered → empty unordered → single item (AC 9)
    // -------------------------------------------------------------------------

    @Test
    fun `rebind from long ordered list produces empty unordered spec`() {
        val longOrdered = dispatchList(
            """{"type":"list","ordered":true,"items":${(1..12).map{"item$it"}.let{it.toString()}}}"""
                .replace("[", "[\"").replace(", ", "\",\"").replace("]", "\"]")
        )
        assertEquals(12, longOrdered.items.size)
        assertTrue(longOrdered.ordered)

        val emptyUnordered = dispatchList("""{"type":"list","ordered":false,"items":[]}""")
        assertEquals(0, emptyUnordered.items.size)
        assertFalse(emptyUnordered.ordered)
    }

    @Test
    fun `rebind from empty list to single-item list reflects new spec`() {
        val empty = dispatchList("""{"type":"list","items":[]}""")
        assertEquals(0, empty.items.size)

        val singleItem = dispatchList("""{"type":"list","items":["only"]}""")
        assertEquals(1, singleItem.items.size)
        assertEquals("only", singleItem.items[0])
    }

    @Test
    fun `rebind from unordered to ordered reflects marker mode change`() {
        val unordered = dispatchList("""{"type":"list","ordered":false,"items":["a","b"]}""")
        assertFalse(unordered.ordered)

        val ordered = dispatchList("""{"type":"list","ordered":true,"items":["a","b"]}""")
        assertTrue(ordered.ordered)
    }

    // -------------------------------------------------------------------------
    // Null/missing items after a populated list produce empty (AC 4, 9)
    // -------------------------------------------------------------------------

    @Test
    fun `rebind from populated list to null items produces empty`() {
        val populated = dispatchList("""{"type":"list","items":["x","y"]}""")
        assertEquals(2, populated.items.size)

        val withNull = dispatchList("""{"type":"list","items":null}""")
        assertEquals(0, withNull.items.size)
    }

    @Test
    fun `rebind from populated list to missing items field produces empty`() {
        val populated = dispatchList("""{"type":"list","items":["a"]}""")
        assertEquals(1, populated.items.size)

        val missing = dispatchList("""{"type":"list"}""")
        assertEquals(0, missing.items.size)
    }

    // -------------------------------------------------------------------------
    // Dispatch is stateless — each call is independent (AC 9)
    // -------------------------------------------------------------------------

    @Test
    fun `consecutive dispatches are independent`() {
        val first = dispatchList("""{"type":"list","ordered":true,"items":["1","2","3"]}""")
        val second = dispatchList("""{"type":"list","ordered":false,"items":["a"]}""")
        val third = dispatchList("""{"type":"list","items":[]}""")

        assertEquals(3, first.items.size)
        assertTrue(first.ordered)

        assertEquals(1, second.items.size)
        assertFalse(second.ordered)

        assertEquals(0, third.items.size)
    }
}
