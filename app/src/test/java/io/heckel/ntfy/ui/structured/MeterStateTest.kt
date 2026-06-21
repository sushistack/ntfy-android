package io.heckel.ntfy.ui.structured

import io.heckel.ntfy.ui.message.ParserParityGoldenCorpus
import io.heckel.ntfy.ui.structured.MeterState.Band
import org.junit.Assert.assertEquals
import org.junit.Test

class MeterStateTest {

    // -------------------------------------------------------------------------
    // clamp()
    // -------------------------------------------------------------------------

    @Test fun clamp_negativeBecomesZero() = assertEquals(0f, MeterState.clamp(-5f))
    @Test fun clamp_zeroStaysZero() = assertEquals(0f, MeterState.clamp(0f))
    @Test fun clamp_midRangeUnchanged() = assertEquals(50f, MeterState.clamp(50f))
    @Test fun clamp_hundredStaysHundred() = assertEquals(100f, MeterState.clamp(100f))
    @Test fun clamp_overHundredBecomesHundred() = assertEquals(100f, MeterState.clamp(130f))

    // -------------------------------------------------------------------------
    // threshold() — boundary assertions matching Story 3.0 golden corpus
    // -------------------------------------------------------------------------

    @Test fun threshold_64_isOk() = assertEquals(Band.OK, MeterState.threshold(64f))
    @Test fun threshold_65_isWarning() = assertEquals(Band.WARNING, MeterState.threshold(65f))
    @Test fun threshold_89_isWarning() = assertEquals(Band.WARNING, MeterState.threshold(89f))
    @Test fun threshold_90_isCritical() = assertEquals(Band.CRITICAL, MeterState.threshold(90f))
    @Test fun threshold_91_isCritical() = assertEquals(Band.CRITICAL, MeterState.threshold(91f))
    @Test fun threshold_zero_isOk() = assertEquals(Band.OK, MeterState.threshold(0f))
    @Test fun threshold_hundred_isCritical() = assertEquals(Band.CRITICAL, MeterState.threshold(100f))

    // -------------------------------------------------------------------------
    // from() — clamp then threshold; out-of-range values normalize then classify
    // -------------------------------------------------------------------------

    @Test fun from_negative5_clampsTo0_isOk() {
        val s = MeterState.from(-5.0)
        assertEquals(0f, s.normalizedValue)
        assertEquals(Band.OK, s.band)
    }

    @Test fun from_over130_clampsTo100_isCritical() {
        val s = MeterState.from(130.0)
        assertEquals(100f, s.normalizedValue)
        assertEquals(Band.CRITICAL, s.band)
    }

    @Test fun from_identicalInputProducesIdenticalState() {
        val a = MeterState.from(75.0)
        val b = MeterState.from(75.0)
        assertEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // Golden corpus: Story 3.0 meter boundary assertions
    // -------------------------------------------------------------------------

    @Test fun goldenCorpus_meterBoundaries() {
        val corpus = ParserParityGoldenCorpus.load()
        for (case in corpus.meterCases) {
            val state = MeterState.from(case.input.value)
            val expectedBand = when (case.expected) {
                ParserParityGoldenCorpus.MeterClass.OK -> Band.OK
                ParserParityGoldenCorpus.MeterClass.WARNING -> Band.WARNING
                ParserParityGoldenCorpus.MeterClass.CRITICAL -> Band.CRITICAL
            }
            assertEquals("Case ${case.id}: value=${case.input.value}", expectedBand, state.band)
        }
    }
}
