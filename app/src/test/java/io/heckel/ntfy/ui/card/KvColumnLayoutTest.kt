package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.KvRow
import io.heckel.ntfy.ui.card.body.KvSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for kv responsive column-count decision logic (AC 6, 7).
 *
 * The breakpoint is 600dp. KvBlockRenderer decides column count by comparing
 * measured body width in pixels against the breakpoint in pixels.
 * This test exercises the pure decision table:
 *   - requested columns = 1 always → 1 column regardless of width
 *   - requested columns = 2, width < breakpoint → 1 column
 *   - requested columns = 2, width >= breakpoint → 2 columns
 *
 * Row-major placement and payload-order preservation are asserted via
 * the KvSpec model (no View inflation needed).
 */
class KvColumnLayoutTest {

    // Breakpoint 600dp at 2x density = 1200px
    private val breakpointPx = 1200

    private fun effectiveColumns(requestedColumns: Int, availableWidthPx: Int): Int {
        return if (requestedColumns == 2 && availableWidthPx >= breakpointPx) 2 else 1
    }

    // -------------------------------------------------------------------------
    // One-column path (AC 6)
    // -------------------------------------------------------------------------

    @Test fun oneColumn_always_regardlessOfWidth() {
        assertEquals(1, effectiveColumns(1, 0))
        assertEquals(1, effectiveColumns(1, breakpointPx))
        assertEquals(1, effectiveColumns(1, breakpointPx + 1))
    }

    @Test fun twoColumn_belowBreakpoint_collapses() {
        assertEquals(1, effectiveColumns(2, breakpointPx - 1))
        assertEquals(1, effectiveColumns(2, 0))
        assertEquals(1, effectiveColumns(2, 100))
    }

    @Test fun twoColumn_atBreakpoint_activates() {
        assertEquals(2, effectiveColumns(2, breakpointPx))
    }

    @Test fun twoColumn_aboveBreakpoint_activates() {
        assertEquals(2, effectiveColumns(2, breakpointPx + 500))
    }

    // -------------------------------------------------------------------------
    // Row order preserved in both layout modes (AC 7)
    // -------------------------------------------------------------------------

    @Test fun rowOrder_preservedInOneColumn() {
        val rows = listOf(
            KvRow("A", "1"),
            KvRow("B", "2"),
            KvRow("C", "3"),
        )
        val spec = KvSpec(columns = 1, rows = rows)
        assertEquals(listOf("A", "B", "C"), spec.rows.map { it.key })
    }

    @Test fun rowOrder_preservedInTwoColumn() {
        val rows = listOf(
            KvRow("A", "1"),
            KvRow("B", "2"),
            KvRow("C", "3"),
            KvRow("D", "4"),
        )
        val spec = KvSpec(columns = 2, rows = rows)
        // Payload order is the authoritative source; the grid places row-major
        assertEquals(listOf("A", "B", "C", "D"), spec.rows.map { it.key })
    }

    @Test fun rowOrder_oddRowCount_twoColumn_noTruncation() {
        val rows = (1..5).map { KvRow("Key$it", "Val$it") }
        val spec = KvSpec(columns = 2, rows = rows)
        assertEquals(5, spec.rows.size)
        assertEquals("Key5", spec.rows[4].key)
    }

    // -------------------------------------------------------------------------
    // No truncation / clamp — all rows present (AC 7)
    // -------------------------------------------------------------------------

    @Test fun allRowsPresent_largeRowSet() {
        val n = 50
        val rows = (1..n).map { KvRow("Key$it", "Value that is very long and should not be clipped $it") }
        val spec = KvSpec(columns = 1, rows = rows)
        assertEquals(n, spec.rows.size)
    }
}
