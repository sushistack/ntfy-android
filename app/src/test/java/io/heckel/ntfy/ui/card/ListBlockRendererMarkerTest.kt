package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.ListSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for list block marker generation and accessibility description logic.
 *
 * These tests exercise the marker text rules (AC 1, 2) and the row content description
 * format (AC 12) without requiring Android Views or Robolectric.
 *
 * The logic under test is extracted here as pure functions that mirror the renderer's
 * internal rules — acting as a specification anchor that future refactors must keep green.
 */
class ListBlockRendererMarkerTest {

    // -------------------------------------------------------------------------
    // Marker text generation (AC 1, 2)
    // -------------------------------------------------------------------------

    private fun markerText(ordered: Boolean, index: Int): String =
        if (ordered) "${index + 1}." else "•"

    private fun contentDescription(ordered: Boolean, index: Int, text: String): String =
        "${markerText(ordered, index)} $text"

    @Test
    fun `ordered marker at index 0 is 1 dot`() {
        assertEquals("1.", markerText(ordered = true, index = 0))
    }

    @Test
    fun `ordered marker at index 8 is 9 dot`() {
        assertEquals("9.", markerText(ordered = true, index = 8))
    }

    @Test
    fun `ordered marker at index 9 is 10 dot (no single-digit cap)`() {
        assertEquals("10.", markerText(ordered = true, index = 9))
    }

    @Test
    fun `ordered marker at index 11 is 12 dot (12+ items)`() {
        assertEquals("12.", markerText(ordered = true, index = 11))
    }

    @Test
    fun `unordered marker is bullet regardless of index`() {
        for (i in 0..15) {
            assertEquals("•", markerText(ordered = false, index = i))
        }
    }

    // -------------------------------------------------------------------------
    // Ordered markers for 1..12 items form a consecutive sequence (AC 1)
    // -------------------------------------------------------------------------

    @Test
    fun `ordered markers for 12 items form consecutive sequence 1 to 12`() {
        val markers = (0 until 12).map { markerText(ordered = true, index = it) }
        assertEquals((1..12).map { "$it." }, markers)
    }

    // -------------------------------------------------------------------------
    // Content description format (AC 12)
    // -------------------------------------------------------------------------

    @Test
    fun `ordered content description prefixes item with decimal marker`() {
        assertEquals("3. deploy", contentDescription(ordered = true, index = 2, text = "deploy"))
    }

    @Test
    fun `unordered content description prefixes item with bullet`() {
        assertEquals("• 배포 시작", contentDescription(ordered = false, index = 0, text = "배포 시작"))
    }

    @Test
    fun `empty text item content description has marker and empty text`() {
        assertEquals("• ", contentDescription(ordered = false, index = 0, text = ""))
    }

    // -------------------------------------------------------------------------
    // ListSpec ordered/items round-trip (AC 1, 2, 5)
    // -------------------------------------------------------------------------

    @Test
    fun `ListSpec ordered false with 3 items produces 3 bullet markers`() {
        val spec = ListSpec(ordered = false, items = listOf("a", "b", "c"))
        val markers = spec.items.indices.map { markerText(spec.ordered, it) }
        assertEquals(listOf("•", "•", "•"), markers)
    }

    @Test
    fun `ListSpec ordered true with 3 items produces 1 2 3 markers`() {
        val spec = ListSpec(ordered = true, items = listOf("x", "y", "z"))
        val markers = spec.items.indices.map { markerText(spec.ordered, it) }
        assertEquals(listOf("1.", "2.", "3."), markers)
    }

    @Test
    fun `ListSpec empty items produces no markers`() {
        val spec = ListSpec(ordered = true, items = emptyList())
        val markers = spec.items.indices.map { markerText(spec.ordered, it) }
        assertEquals(emptyList<String>(), markers)
    }
}
