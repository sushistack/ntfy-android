package io.heckel.ntfy.ui.card.chart

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ChartSpec normalization, point coercion, 60-cap, source immutability,
 * default kind, domain/baseline, and label selection (AC 2–7, AC 4, AC 5, AC 8).
 */
class ChartSpecTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun point(value: Double, label: String = "") = ChartPoint(value = value, label = label)

    private fun rawPoint(rawValue: Any?, label: String = "") = RawChartPoint(rawValue = rawValue, label = label)

    // ── normalization / coercion ──────────────────────────────────────────────

    @Test fun `numeric double is kept`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(3.14)))
        assertEquals(1, pts.size)
        assertEquals(3.14, pts[0].value, 0.0)
    }

    @Test fun `integer value is coerced to double`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(42)))
        assertEquals(42.0, pts[0].value, 0.0)
    }

    @Test fun `Long value is coerced`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(100L)))
        assertEquals(100.0, pts[0].value, 0.0)
    }

    @Test fun `NaN is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(Double.NaN)))
        assertTrue(pts.isEmpty())
    }

    @Test fun `positive infinity is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(Double.POSITIVE_INFINITY)))
        assertTrue(pts.isEmpty())
    }

    @Test fun `negative infinity is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(Double.NEGATIVE_INFINITY)))
        assertTrue(pts.isEmpty())
    }

    @Test fun `null rawValue is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(null)))
        assertTrue(pts.isEmpty())
    }

    @Test fun `non-numeric string is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint("hello")))
        assertTrue(pts.isEmpty())
    }

    @Test fun `empty string is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint("")))
        assertTrue(pts.isEmpty())
    }

    @Test fun `boolean true is dropped`() {
        val pts = ChartSpec.normalize(listOf(rawPoint(true)))
        assertTrue(pts.isEmpty())
    }

    @Test fun `mixed valid and invalid with invalid removed before cap`() {
        // 5 invalid + 60 valid = 65 inputs; after removing 5 invalid → 60 valid → take(60)
        val raw = mutableListOf<RawChartPoint>()
        for (i in 1..5) raw.add(rawPoint(null))          // invalid
        for (i in 1..65) raw.add(rawPoint(i.toDouble()))  // valid
        val pts = ChartSpec.normalize(raw)
        assertEquals(60, pts.size)
        // first valid value is 1.0
        assertEquals(1.0, pts[0].value, 0.0)
    }

    @Test fun `cap is applied after invalid removal, not before`() {
        // 2 invalids at positions 0 and 1, then 61 valid values.
        // After dropping 2 invalids: 61 valid. take(60) → 60 valid.
        val raw = mutableListOf<RawChartPoint>()
        raw.add(rawPoint(null)); raw.add(rawPoint(null))
        for (i in 1..61) raw.add(rawPoint(i.toDouble()))
        val pts = ChartSpec.normalize(raw)
        assertEquals(60, pts.size)
        assertEquals(60.0, pts[59].value, 0.0)  // value 61 is NOT included
    }

    @Test fun `exactly 60 valid points are kept`() {
        val raw = (1..60).map { rawPoint(it.toDouble()) }
        assertEquals(60, ChartSpec.normalize(raw).size)
    }

    @Test fun `61 valid points are capped to 60`() {
        val raw = (1..61).map { rawPoint(it.toDouble()) }
        assertEquals(60, ChartSpec.normalize(raw).size)
    }

    @Test fun `empty input produces empty output`() {
        assertTrue(ChartSpec.normalize(emptyList()).isEmpty())
    }

    @Test fun `source list is not mutated`() {
        val original = listOf(rawPoint(1.0), rawPoint(null), rawPoint(2.0))
        val copy = original.toList()
        ChartSpec.normalize(original)
        assertEquals(copy, original)
    }

    @Test fun `order is preserved`() {
        val vals = listOf(5.0, 3.0, 8.0, 1.0)
        val raw = vals.map { rawPoint(it) }
        val pts = ChartSpec.normalize(raw)
        assertEquals(vals, pts.map { it.value })
    }

    // ── default kind ─────────────────────────────────────────────────────────

    @Test fun `absent kind defaults to bar`() {
        val spec = ChartSpec(kind = null, unit = null, points = listOf(point(1.0)))
        assertEquals(ChartKind.BAR, spec.effectiveKind)
    }

    @Test fun `kind 'bar' maps to BAR`() {
        val spec = ChartSpec(kind = "bar", unit = null, points = listOf(point(1.0)))
        assertEquals(ChartKind.BAR, spec.effectiveKind)
    }

    @Test fun `kind 'line' maps to LINE`() {
        val spec = ChartSpec(kind = "line", unit = null, points = listOf(point(1.0)))
        assertEquals(ChartKind.LINE, spec.effectiveKind)
    }

    @Test fun `unknown kind value defaults to bar`() {
        val spec = ChartSpec(kind = "scatter", unit = null, points = listOf(point(1.0)))
        assertEquals(ChartKind.BAR, spec.effectiveKind)
    }

    // ── domain calculation ────────────────────────────────────────────────────

    @Test fun `all-positive domain includes zero as min`() {
        val pts = listOf(point(3.0), point(5.0), point(2.0))
        val d = ChartDomain.compute(pts)
        assertEquals(0.0, d.min, 0.0)
        assertEquals(5.0, d.max, 0.0)
    }

    @Test fun `all-negative domain includes zero as max`() {
        val pts = listOf(point(-3.0), point(-5.0), point(-1.0))
        val d = ChartDomain.compute(pts)
        assertEquals(-5.0, d.min, 0.0)
        assertEquals(0.0, d.max, 0.0)
    }

    @Test fun `mixed sign domain spans from min to max, always including zero`() {
        val pts = listOf(point(-10.0), point(0.0), point(20.0))
        val d = ChartDomain.compute(pts)
        assertEquals(-10.0, d.min, 0.0)
        assertEquals(20.0, d.max, 0.0)
    }

    @Test fun `all-zero domain is expanded to non-zero range`() {
        val pts = listOf(point(0.0), point(0.0))
        val d = ChartDomain.compute(pts)
        assertTrue("domain must be non-zero span", d.max > d.min)
        assertTrue("min must be <= 0", d.min <= 0.0)
        assertTrue("max must be >= 0", d.max >= 0.0)
    }

    @Test fun `equal positive values domain is expanded`() {
        val pts = listOf(point(5.0), point(5.0), point(5.0))
        val d = ChartDomain.compute(pts)
        assertTrue("domain must be non-zero span", d.max > d.min)
    }

    @Test fun `equal negative values domain is expanded`() {
        val pts = listOf(point(-3.0), point(-3.0))
        val d = ChartDomain.compute(pts)
        assertTrue("domain must be non-zero span", d.max > d.min)
    }

    @Test fun `single point, positive — domain expanded`() {
        val pts = listOf(point(7.0))
        val d = ChartDomain.compute(pts)
        assertTrue(d.max > d.min)
        assertEquals(0.0, d.min, 0.0) // zero included
    }

    @Test fun `domain zero baseline maps to plot fraction between 0 and 1`() {
        val pts = listOf(point(-10.0), point(20.0))
        val d = ChartDomain.compute(pts)
        val baselineFraction = d.valueToFraction(0.0)
        assertTrue(baselineFraction in 0.0..1.0)
        assertEquals(10.0 / 30.0, baselineFraction, 1e-9)
    }

    @Test fun `valueToFraction for max value is 1`() {
        val pts = listOf(point(0.0), point(10.0))
        val d = ChartDomain.compute(pts)
        assertEquals(1.0, d.valueToFraction(10.0), 1e-9)
    }

    @Test fun `valueToFraction for min value is 0`() {
        val pts = listOf(point(-5.0), point(0.0))
        val d = ChartDomain.compute(pts)
        assertEquals(0.0, d.valueToFraction(-5.0), 1e-9)
    }

    // ── label selection ───────────────────────────────────────────────────────

    @Test fun `non-empty label field takes precedence`() {
        val pt = ChartPoint(value = 34.0, label = "Jan")
        assertEquals("Jan", ChartLabelFormatter.labelFor(pt, unit = null))
    }

    @Test fun `empty label falls back to formatted value`() {
        val pt = ChartPoint(value = 34.0, label = "")
        assertEquals("34", ChartLabelFormatter.labelFor(pt, unit = null))
    }

    @Test fun `unit appended directly to numeric fallback`() {
        val pt = ChartPoint(value = 34.0, label = "")
        assertEquals("34%", ChartLabelFormatter.labelFor(pt, unit = "%"))
    }

    @Test fun `decimal value formatted without trailing zero noise`() {
        val pt = ChartPoint(value = 3.5, label = "")
        val label = ChartLabelFormatter.labelFor(pt, unit = null)
        assertEquals("3.5", label)
    }

    @Test fun `integer double formatted as integer string`() {
        val pt = ChartPoint(value = 100.0, label = "")
        assertEquals("100", ChartLabelFormatter.labelFor(pt, unit = null))
    }

    @Test fun `negative value formatted correctly`() {
        val pt = ChartPoint(value = -42.0, label = "")
        assertEquals("-42", ChartLabelFormatter.labelFor(pt, unit = null))
    }

    @Test fun `label row shown for 1 point`() {
        assertTrue(ChartSpec.showLabels(1))
    }

    @Test fun `label row shown for 12 points`() {
        assertTrue(ChartSpec.showLabels(12))
    }

    @Test fun `label row omitted for 13 points`() {
        assertFalse(ChartSpec.showLabels(13))
    }

    @Test fun `label row omitted for 60 points`() {
        assertFalse(ChartSpec.showLabels(60))
    }

    @Test fun `label row omitted for 0 points`() {
        assertFalse(ChartSpec.showLabels(0))
    }
}
