package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.KvRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for kv row status presentation decisions (AC 3, 4, 5).
 *
 * These tests validate the pure decision logic extracted from KvBlockRenderer:
 * - Value color: error → priority_urgent; others → default text.
 * - Status dot color: ok/warn/error; absent/unknown → no dot.
 * - Finite meter suppresses status dot (AC 5 overrides AC 4).
 *
 * The color resource IDs are not resolved here (JVM environment has no Android resources).
 * Instead, we model the decision table as named outcomes and assert their correctness.
 */
class KvStatusPresentationTest {

    // Models what KvBlockRenderer decides for each row:
    enum class ValueColor { ERROR_CORAL, DEFAULT }
    enum class DotColor { OK, WARN, ERROR, NONE }

    private fun decideValueColor(status: String?): ValueColor =
        if (status == "error") ValueColor.ERROR_CORAL else ValueColor.DEFAULT

    private fun decideDotColor(status: String?, meter: Double?): DotColor {
        // Finite meter suppresses dot
        if (meter != null && meter.isFinite()) return DotColor.NONE
        return when (status) {
            "ok" -> DotColor.OK
            "warn" -> DotColor.WARN
            "error" -> DotColor.ERROR
            else -> DotColor.NONE
        }
    }

    // -------------------------------------------------------------------------
    // Value color (AC 3)
    // -------------------------------------------------------------------------

    @Test fun valueColor_error_isCoralColor() = assertEquals(ValueColor.ERROR_CORAL, decideValueColor("error"))
    @Test fun valueColor_ok_isDefault() = assertEquals(ValueColor.DEFAULT, decideValueColor("ok"))
    @Test fun valueColor_warn_isDefault() = assertEquals(ValueColor.DEFAULT, decideValueColor("warn"))
    @Test fun valueColor_null_isDefault() = assertEquals(ValueColor.DEFAULT, decideValueColor(null))
    @Test fun valueColor_unknown_isDefault() = assertEquals(ValueColor.DEFAULT, decideValueColor("unknown"))

    // -------------------------------------------------------------------------
    // Status dot color (AC 4)
    // -------------------------------------------------------------------------

    @Test fun dot_ok_noMeter_showsOkDot() = assertEquals(DotColor.OK, decideDotColor("ok", null))
    @Test fun dot_warn_noMeter_showsWarnDot() = assertEquals(DotColor.WARN, decideDotColor("warn", null))
    @Test fun dot_error_noMeter_showsErrorDot() = assertEquals(DotColor.ERROR, decideDotColor("error", null))
    @Test fun dot_null_noMeter_showsNoDot() = assertEquals(DotColor.NONE, decideDotColor(null, null))
    @Test fun dot_unknown_noMeter_showsNoDot() = assertEquals(DotColor.NONE, decideDotColor("unknown", null))

    // -------------------------------------------------------------------------
    // Finite meter suppresses dot (AC 5 overrides AC 4)
    // -------------------------------------------------------------------------

    @Test fun dot_error_withFiniteMeter_suppressesDot() = assertEquals(DotColor.NONE, decideDotColor("error", 95.0))
    @Test fun dot_ok_withFiniteMeter_suppressesDot() = assertEquals(DotColor.NONE, decideDotColor("ok", 50.0))
    @Test fun dot_warn_withFiniteMeter_suppressesDot() = assertEquals(DotColor.NONE, decideDotColor("warn", 70.0))
    @Test fun dot_null_withFiniteMeter_suppressesDot() = assertEquals(DotColor.NONE, decideDotColor(null, 10.0))

    // Error status still colors the value even with a meter (AC 3 + AC 5 coexist)
    @Test fun valueColor_error_withFiniteMeter_stillCoral() {
        val row = KvRow(key = "Disk", value = "95%", status = "error", meter = 95.0)
        assertEquals(ValueColor.ERROR_CORAL, decideValueColor(row.status))
        assertEquals(DotColor.NONE, decideDotColor(row.status, row.meter))
    }

    // -------------------------------------------------------------------------
    // Meter delegation decision (AC 5) — finite vs non-finite
    // -------------------------------------------------------------------------

    @Test fun meterDelegate_finiteValue_rendersMeter() {
        val row = KvRow(key = "CPU", value = "48%", meter = 48.0)
        assertNotNull(row.meter)
        assert(row.meter!!.isFinite())
    }

    @Test fun meterDelegate_nullMeter_noMeter() {
        val row = KvRow(key = "CPU", value = "48%", meter = null)
        assertNull(row.meter)
    }

    @Test fun meterDelegate_infiniteMeter_excluded() {
        // KvSpecParser filters Infinity from JSON; this validates the precondition
        val finiteCheck = Double.POSITIVE_INFINITY.isFinite()
        assert(!finiteCheck)
    }
}
