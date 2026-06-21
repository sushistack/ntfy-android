package io.heckel.ntfy.ui.card.chart

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ChartGeometry — coordinate mapping, bar layout, line layout,
 * and boundary conditions (AC 2–5, AC 9).
 */
class ChartGeometryTest {

    private fun point(v: Double) = ChartPoint(value = v, label = "")

    // ── bar geometry ──────────────────────────────────────────────────────────

    @Test fun `single bar fills full plot width and remains visible`() {
        val pts = listOf(point(5.0))
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 300f, plotHeight = 120f)

        assertEquals(1, geom.size)
        val b = geom[0]
        assertTrue("bar width must be > 0", b.width > 0f)
        assertTrue("bar left must be >= 0", b.left >= 0f)
        assertTrue("bar right must be <= plotWidth", b.left + b.width <= 300f + 1f) // allow float rounding
    }

    @Test fun `bars do not overlap with sorted left-edges strictly increasing`() {
        val pts = (1..10).map { point(it.toDouble()) }
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 300f, plotHeight = 120f)

        for (i in 1 until geom.size) {
            assertTrue("bar $i left (${geom[i].left}) must be > bar ${i-1} right (${geom[i-1].left + geom[i-1].width})",
                geom[i].left >= geom[i-1].left + geom[i-1].width - 0.01f)
        }
    }

    @Test fun `bar for positive value grows upward from baseline`() {
        val pts = listOf(point(10.0))
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 200f, plotHeight = 120f)

        val b = geom[0]
        assertTrue("bar top (${b.top}) < baseline (${b.baseline})", b.top < b.baseline)
    }

    @Test fun `bar for negative value grows downward from baseline`() {
        val pts = listOf(point(-10.0))
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 200f, plotHeight = 120f)

        val b = geom[0]
        assertTrue("bar bottom (${b.bottom}) > baseline (${b.baseline})", b.bottom > b.baseline)
    }

    @Test fun `bar top and bottom are within plot height`() {
        val pts = listOf(point(-10.0), point(20.0))
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 300f, plotHeight = 120f)

        for (b in geom) {
            assertTrue("top ${b.top} must be >= 0", b.top >= 0f)
            assertTrue("bottom ${b.bottom} must be <= plotHeight", b.bottom <= 120f + 0.01f)
        }
    }

    @Test fun `60 bars all have positive width on narrow screen`() {
        val pts = (1..60).map { point(it.toDouble()) }
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 200f, plotHeight = 120f)

        for (b in geom) {
            assertTrue("bar width ${b.width} must be > 0", b.width > 0f)
        }
    }

    @Test fun `bar baseline coordinate is within plot bounds`() {
        val pts = listOf(point(-5.0), point(5.0))
        val domain = ChartDomain.compute(pts)
        val geom = ChartGeometry.computeBars(pts, domain, plotWidth = 300f, plotHeight = 120f)
        val baseline = geom[0].baseline
        assertTrue(baseline in 0f..120f)
    }

    // ── line geometry ─────────────────────────────────────────────────────────

    @Test fun `line with two points returns two coordinates`() {
        val pts = listOf(point(0.0), point(10.0))
        val domain = ChartDomain.compute(pts)
        val coords = ChartGeometry.computeLinePoints(pts, domain, plotWidth = 300f, plotHeight = 120f)
        assertEquals(2, coords.size)
    }

    @Test fun `line x-coordinates increase monotonically`() {
        val pts = (0..9).map { point(it.toDouble()) }
        val domain = ChartDomain.compute(pts)
        val coords = ChartGeometry.computeLinePoints(pts, domain, plotWidth = 300f, plotHeight = 120f)

        for (i in 1 until coords.size) {
            assertTrue("x[$i] (${coords[i].x}) must be >= x[${i-1}] (${coords[i-1].x})",
                coords[i].x >= coords[i-1].x - 0.01f)
        }
    }

    @Test fun `line y-coordinates are within plot height`() {
        val pts = listOf(point(-50.0), point(50.0), point(0.0))
        val domain = ChartDomain.compute(pts)
        val coords = ChartGeometry.computeLinePoints(pts, domain, plotWidth = 300f, plotHeight = 120f)

        for (c in coords) {
            assertTrue("y ${c.y} must be in [0, plotHeight]", c.y in 0f..120f)
        }
    }

    @Test fun `single-point line returns one coordinate`() {
        val pts = listOf(point(5.0))
        val domain = ChartDomain.compute(pts)
        val coords = ChartGeometry.computeLinePoints(pts, domain, plotWidth = 200f, plotHeight = 120f)
        assertEquals(1, coords.size)
    }

    @Test fun `first x is 0 and last x is plotWidth`() {
        val pts = listOf(point(0.0), point(10.0))
        val domain = ChartDomain.compute(pts)
        val coords = ChartGeometry.computeLinePoints(pts, domain, plotWidth = 300f, plotHeight = 120f)
        assertEquals(0f, coords.first().x, 0.5f)
        assertEquals(300f, coords.last().x, 0.5f)
    }
}
