package io.heckel.ntfy.ui.card.chart

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden tests for [ChartLabelFormatter.formatNumber] — integers, decimals, negatives,
 * large/small values.  These are the canonical format expectations (AC 6, Dev Notes §Label rules).
 */
class ChartLabelFormatterTest {

    private fun fmt(v: Double) = ChartLabelFormatter.formatNumber(v)

    @Test fun `zero formats as 0`() = assertEquals("0", fmt(0.0))
    @Test fun `positive integer formats without decimal`() = assertEquals("42", fmt(42.0))
    @Test fun `negative integer formats correctly`() = assertEquals("-7", fmt(-7.0))
    @Test fun `decimal value formats without trailing zeros`() = assertEquals("3.5", fmt(3.5))
    @Test fun `large integer formats without grouping separator`() = assertEquals("1000000", fmt(1_000_000.0))
    @Test fun `small decimal preserves significant digits`() = assertEquals("0.001", fmt(0.001))
    @Test fun `negative decimal formats correctly`() = assertEquals("-1.5", fmt(-1.5))
    @Test fun `integer double formats without decimal suffix`() = assertEquals("100", fmt(100.0))
}
