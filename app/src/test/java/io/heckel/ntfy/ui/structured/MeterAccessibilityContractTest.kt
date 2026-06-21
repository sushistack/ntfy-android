package io.heckel.ntfy.ui.structured

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the accessibility contract values produced by [MeterState] (Story 3.2, AC 4–5).
 *
 * InlineMeterView feeds these values directly into RangeInfoCompat and stateDescription.
 * Verifying them at the model level gives deterministic JVM coverage without a device/emulator.
 *
 * Range semantics: min=0, max=100, current=normalizedValue.
 * The View uses ProgressBar class name and a localized "%d%%" string.
 */
class MeterAccessibilityContractTest {

    // ── range values passed to RangeInfoCompat ────────────────────────────────

    @Test fun rangeMin_isAlways0() {
        // All bind values must yield normalizedValue >= 0 (range min = 0)
        listOf(-5.0, 0.0, 50.0, 100.0, 130.0).forEach { input ->
            val state = MeterState.from(input)
            assertTrue(
                "normalizedValue for input=$input must be >= 0 (RangeInfo min=0), got ${state.normalizedValue}",
                state.normalizedValue >= 0f
            )
        }
    }

    @Test fun rangeMax_isAlways100() {
        // All bind values must yield normalizedValue <= 100 (range max = 100)
        listOf(-5.0, 0.0, 50.0, 100.0, 130.0).forEach { input ->
            val state = MeterState.from(input)
            assertTrue(
                "normalizedValue for input=$input must be <= 100 (RangeInfo max=100), got ${state.normalizedValue}",
                state.normalizedValue <= 100f
            )
        }
    }

    @Test fun currentValue_equalsNormalizedValue_forInRange() {
        val state = MeterState.from(75.0)
        assertEquals(75f, state.normalizedValue, 0.001f)
    }

    @Test fun currentValue_equalsClampedValue_forOutOfRange() {
        // negative input → clamped to 0
        assertEquals(0f, MeterState.from(-5.0).normalizedValue, 0.001f)
        // over-100 input → clamped to 100
        assertEquals(100f, MeterState.from(130.0).normalizedValue, 0.001f)
    }

    // ── rebind determinism (AC 5) — no state leaking between binds ────────────

    @Test fun rebind_criticalToOk_bandChanges() {
        val s1 = MeterState.from(95.0)
        val s2 = MeterState.from(30.0)
        assertEquals(MeterState.Band.CRITICAL, s1.band)
        assertEquals(MeterState.Band.OK, s2.band)
        assertNotEquals("rebind must produce a different state", s1, s2)
    }

    @Test fun rebind_highToLow_normalizedChanges() {
        val s1 = MeterState.from(100.0)
        val s2 = MeterState.from(0.0)
        assertEquals(100f, s1.normalizedValue, 0.001f)
        assertEquals(0f, s2.normalizedValue, 0.001f)
    }

    @Test fun rebind_warningToOk_bandChanges() {
        val s1 = MeterState.from(80.0)
        val s2 = MeterState.from(20.0)
        assertEquals(MeterState.Band.WARNING, s1.band)
        assertEquals(MeterState.Band.OK, s2.band)
    }

    @Test fun identicalRebind_producesEqualState() {
        val s1 = MeterState.from(65.0)
        val s2 = MeterState.from(65.0)
        assertEquals("identical value must produce identical state", s1, s2)
    }

    // ── percentage announcement value ─────────────────────────────────────────

    @Test fun percentAnnouncement_matchesNormalizedValueInt() {
        // The View announces: context.getString(R.string.meter_accessibility_percent, normalizedValue.toInt())
        // We verify the integer fed into the format string is the clamped value.
        val cases = listOf(
            -5.0 to 0,
            0.0 to 0,
            64.0 to 64,
            65.0 to 65,
            90.0 to 90,
            100.0 to 100,
            130.0 to 100,
        )
        for ((input, expected) in cases) {
            val state = MeterState.from(input)
            assertEquals(
                "Percentage int for input=$input must be $expected",
                expected,
                state.normalizedValue.toInt()
            )
        }
    }
}
