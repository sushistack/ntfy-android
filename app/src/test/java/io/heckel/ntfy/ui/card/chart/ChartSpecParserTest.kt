package io.heckel.ntfy.ui.card.chart

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ChartSpecParser] — JSON object → [ChartSpec] conversion.
 * Covers valid inputs, missing/empty data, all-invalid data, kind defaults, unit parsing.
 */
class ChartSpecParserTest {

    private fun parse(json: String): ChartSpec? {
        val obj = JsonParser.parseString(json).asJsonObject
        return ChartSpecParser.parse(obj)
    }

    @Test fun `bar chart with two valid points is parsed`() {
        val spec = parse("""{"type":"chart","data":[{"value":12},{"value":34}]}""")
        assertNotNull(spec)
        assertEquals(2, spec!!.points.size)
        assertEquals(ChartKind.BAR, spec.effectiveKind)
    }

    @Test fun `line kind is parsed`() {
        val spec = parse("""{"type":"chart","kind":"line","data":[{"value":1},{"value":2}]}""")
        assertNotNull(spec)
        assertEquals(ChartKind.LINE, spec!!.effectiveKind)
    }

    @Test fun `absent kind defaults to BAR`() {
        val spec = parse("""{"type":"chart","data":[{"value":5}]}""")
        assertNotNull(spec)
        assertEquals(ChartKind.BAR, spec!!.effectiveKind)
    }

    @Test fun `unit is parsed`() {
        val spec = parse("""{"type":"chart","unit":"%","data":[{"value":50}]}""")
        assertNotNull(spec)
        assertEquals("%", spec!!.unit)
    }

    @Test fun `label is parsed per point`() {
        val spec = parse("""{"type":"chart","data":[{"value":1,"label":"Jan"},{"value":2,"label":"Feb"}]}""")
        assertNotNull(spec)
        assertEquals("Jan", spec!!.points[0].label)
        assertEquals("Feb", spec.points[1].label)
    }

    @Test fun `missing data field returns null`() {
        val spec = parse("""{"type":"chart"}""")
        assertNull(spec)
    }

    @Test fun `empty data array returns null`() {
        val spec = parse("""{"type":"chart","data":[]}""")
        assertNull(spec)
    }

    @Test fun `all-invalid data returns null`() {
        val spec = parse("""{"type":"chart","data":[{"value":null},{"value":"oops"}]}""")
        assertNull(spec)
    }

    @Test fun `non-array data returns null`() {
        val spec = parse("""{"type":"chart","data":"bad"}""")
        assertNull(spec)
    }

    @Test fun `non-object items in array are skipped`() {
        val spec = parse("""{"type":"chart","data":[42,{"value":10}]}""")
        assertNotNull(spec)
        assertEquals(1, spec!!.points.size)
        assertEquals(10.0, spec.points[0].value, 0.0)
    }

    @Test fun `61 valid points are capped to 60`() {
        val data = (1..61).joinToString(",") { """{"value":$it}""" }
        val spec = parse("""{"type":"chart","data":[$data]}""")
        assertNotNull(spec)
        assertEquals(60, spec!!.points.size)
    }

    @Test fun `mixed valid and invalid — 5 invalid then 65 valid — keeps first 60 valid`() {
        val invalids = (1..5).joinToString(",") { """{"value":null}""" }
        val valids = (1..65).joinToString(",") { """{"value":$it}""" }
        val spec = parse("""{"type":"chart","data":[$invalids,$valids]}""")
        assertNotNull(spec)
        assertEquals(60, spec!!.points.size)
        assertEquals(1.0, spec.points[0].value, 0.0)
    }
}
