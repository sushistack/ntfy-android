package io.heckel.ntfy.ui.card

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.heckel.ntfy.ui.card.body.KvRow
import io.heckel.ntfy.ui.card.body.KvSpec
import io.heckel.ntfy.ui.card.body.KvSpecParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [KvSpecParser] (AC 5, 7, 8 data model).
 *
 * Covers:
 * - Full canonical payload parsing
 * - columns field (1 default, 2 explicit, other values → 1)
 * - meter: finite/non-finite/absent handling
 * - status: present/absent/unknown
 * - Malformed/missing inputs return null or empty rows gracefully
 * - Row order preserved
 */
class KvSpecParserTest {

    private val gson = Gson()

    private fun parse(json: String): KvSpec? {
        val obj = gson.fromJson(json, JsonObject::class.java)
        return KvSpecParser.parse(obj)
    }

    // -------------------------------------------------------------------------
    // Full canonical payload (wire format from story Dev Notes)
    // -------------------------------------------------------------------------

    @Test fun canonicalPayload_parsesAllFields() {
        val spec = parse("""
            {
              "type": "kv",
              "columns": 2,
              "rows": [
                {"key": "CPU", "value": "4.86%", "meter": 4.86},
                {"key": "Load Avg", "value": "0.11 0.12 0.18", "status": "ok"},
                {"key": "Agent", "value": "0.18.7", "icon": "agent"},
                {"key": "Disk", "value": "95%", "status": "error", "meter": 95}
              ]
            }
        """)
        assertNotNull(spec)
        assertEquals(2, spec!!.columns)
        assertEquals(4, spec.rows.size)

        val cpuRow = spec.rows[0]
        assertEquals("CPU", cpuRow.key)
        assertEquals("4.86%", cpuRow.value)
        assertEquals(4.86, cpuRow.meter!!, 0.001)
        assertNull(cpuRow.status)
        assertNull(cpuRow.icon)

        val loadRow = spec.rows[1]
        assertEquals("Load Avg", loadRow.key)
        assertEquals("ok", loadRow.status)
        assertNull(loadRow.meter)

        val agentRow = spec.rows[2]
        assertEquals("agent", agentRow.icon)

        val diskRow = spec.rows[3]
        assertEquals("error", diskRow.status)
        assertEquals(95.0, diskRow.meter!!, 0.001)
    }

    // -------------------------------------------------------------------------
    // columns field
    // -------------------------------------------------------------------------

    @Test fun columns_defaultsToOne_whenAbsent() {
        val spec = parse("""{"rows":[]}""")
        assertNotNull(spec)
        assertEquals(1, spec!!.columns)
    }

    @Test fun columns_two_whenExplicit() {
        val spec = parse("""{"columns":2,"rows":[]}""")
        assertEquals(2, spec!!.columns)
    }

    @Test fun columns_treatsThreeAsOne() {
        val spec = parse("""{"columns":3,"rows":[]}""")
        assertEquals(1, spec!!.columns)
    }

    @Test fun columns_treatsOneAsOne() {
        val spec = parse("""{"columns":1,"rows":[]}""")
        assertEquals(1, spec!!.columns)
    }

    // -------------------------------------------------------------------------
    // meter field — finite / non-finite / absent
    // -------------------------------------------------------------------------

    @Test fun meter_finitePositive_present() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","meter":42.5}]}""")
        assertEquals(42.5, spec!!.rows[0].meter!!, 0.001)
    }

    @Test fun meter_zero_isFinite() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","meter":0}]}""")
        assertEquals(0.0, spec!!.rows[0].meter!!, 0.001)
    }

    @Test fun meter_absent_isNull() {
        val spec = parse("""{"rows":[{"key":"k","value":"v"}]}""")
        assertNull(spec!!.rows[0].meter)
    }

    @Test fun meter_nullJson_isNull() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","meter":null}]}""")
        assertNull(spec!!.rows[0].meter)
    }

    @Test fun meter_stringValue_isNull() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","meter":"high"}]}""")
        assertNull(spec!!.rows[0].meter)
    }

    // -------------------------------------------------------------------------
    // status field
    // -------------------------------------------------------------------------

    @Test fun status_ok() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","status":"ok"}]}""")
        assertEquals("ok", spec!!.rows[0].status)
    }

    @Test fun status_warn() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","status":"warn"}]}""")
        assertEquals("warn", spec!!.rows[0].status)
    }

    @Test fun status_error() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","status":"error"}]}""")
        assertEquals("error", spec!!.rows[0].status)
    }

    @Test fun status_unknown_preserved() {
        val spec = parse("""{"rows":[{"key":"k","value":"v","status":"critical"}]}""")
        assertEquals("critical", spec!!.rows[0].status)
    }

    @Test fun status_absent_isNull() {
        val spec = parse("""{"rows":[{"key":"k","value":"v"}]}""")
        assertNull(spec!!.rows[0].status)
    }

    // -------------------------------------------------------------------------
    // icon field
    // -------------------------------------------------------------------------

    @Test fun icon_present() {
        val spec = parse("""{"rows":[{"key":"cpu","value":"4%","icon":"agent"}]}""")
        assertEquals("agent", spec!!.rows[0].icon)
    }

    @Test fun icon_absent_isNull() {
        val spec = parse("""{"rows":[{"key":"cpu","value":"4%"}]}""")
        assertNull(spec!!.rows[0].icon)
    }

    // -------------------------------------------------------------------------
    // Row ordering
    // -------------------------------------------------------------------------

    @Test fun rowOrder_preserved() {
        val spec = parse("""{"rows":[
            {"key":"A","value":"1"},
            {"key":"B","value":"2"},
            {"key":"C","value":"3"}
        ]}""")
        val keys = spec!!.rows.map { it.key }
        assertEquals(listOf("A", "B", "C"), keys)
    }

    // -------------------------------------------------------------------------
    // Malformed / edge cases
    // -------------------------------------------------------------------------

    @Test fun emptyRows_returnsEmptyList() {
        val spec = parse("""{"rows":[]}""")
        assertNotNull(spec)
        assertTrue(spec!!.rows.isEmpty())
    }

    @Test fun rowsAbsent_returnsEmptyList() {
        val spec = parse("""{}""")
        assertNotNull(spec)
        assertTrue(spec!!.rows.isEmpty())
    }

    @Test fun rowMissingKey_skipped() {
        val spec = parse("""{"rows":[{"value":"v"},{"key":"ok","value":"yes"}]}""")
        assertEquals(1, spec!!.rows.size)
        assertEquals("ok", spec.rows[0].key)
    }

    @Test fun nonObjectRowEntries_skipped() {
        val spec = parse("""{"rows":["not-an-object",{"key":"k","value":"v"}]}""")
        assertEquals(1, spec!!.rows.size)
    }
}
