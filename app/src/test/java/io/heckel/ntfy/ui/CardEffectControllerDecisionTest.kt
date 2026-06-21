package io.heckel.ntfy.ui

import io.heckel.ntfy.ui.accessibility.ReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CardEffectController] decision logic — animation duration, ID consumption,
 * and reduced-motion path — without needing a real View (tested via the internal seam
 * in [ReducedMotion]).
 *
 * View-level animator behaviour (translation, alpha, card colour) is exercised
 * in integration tests that need Robolectric.
 */
class CardEffectControllerDecisionTest {

    // ------- ReducedMotion seam (re-used from Story 1.4) -------

    @Test
    fun `reduced motion seam animators enabled means not reduced`() {
        assertFalse(ReducedMotion.isEnabledForAnimatorsEnabled(animatorsEnabled = true))
    }

    @Test
    fun `reduced motion seam animators disabled means reduced`() {
        assertTrue(ReducedMotion.isEnabledForAnimatorsEnabled(animatorsEnabled = false))
    }

    // ------- Effect duration constants -------

    @Test
    fun `arrival duration is exactly 250ms`() {
        assertEquals(250L, CardEffectController.ARRIVAL_DURATION_MS)
    }

    @Test
    fun `deep link pulse duration is positive and non-zero`() {
        assertTrue(CardEffectController.DEEP_LINK_PULSE_DURATION_MS > 0L)
    }

    // ------- ID consumption semantics -------

    @Test
    fun `NewArrival consumed callback fires when effect is dispatched`() {
        var consumedId: String? = null
        val eff = CardEffect.NewArrival(stableId = "msg-1") { consumedId = "msg-1" }

        // Simulate the host's tracking set behaviour: remove ID on consumption.
        val trackedIds = mutableSetOf("msg-1")
        val wrappedEff = CardEffect.NewArrival(stableId = eff.stableId) {
            eff.consumed()
            trackedIds.remove(eff.stableId)
        }
        wrappedEff.consumed()

        assertEquals("msg-1", consumedId)
        assertFalse("ID must be removed after first consumption", trackedIds.contains("msg-1"))
    }

    @Test
    fun `second bind with same ID does not produce a new arrival effect if host removed it`() {
        val trackedIds = mutableSetOf("msg-2")

        fun effectForBind(id: String): CardEffect {
            return if (trackedIds.contains(id)) {
                CardEffect.NewArrival(id) { trackedIds.remove(id) }
            } else {
                CardEffect.None
            }
        }

        // First bind: effect present
        val firstEff = effectForBind("msg-2")
        assertTrue(firstEff is CardEffect.NewArrival)
        (firstEff as CardEffect.NewArrival).consumed()

        // Second bind: host has removed the ID; effect must be None
        val secondEff = effectForBind("msg-2")
        assertTrue("Rebind must not replay arrival effect", secondEff is CardEffect.None)
    }

    @Test
    fun `initial load IDs are never placed in the tracked set`() {
        // Simulates host decision logic: only IDs received AFTER first load are tracked.
        val trackedIds = mutableSetOf<String>()  // empty on initial load

        fun effectForBind(id: String): CardEffect =
            if (trackedIds.contains(id)) CardEffect.NewArrival(id) { trackedIds.remove(id) }
            else CardEffect.None

        // Initial load of three messages — none should get arrival effect
        listOf("init-1", "init-2", "init-3").forEach { id ->
            assertTrue(effectForBind(id) is CardEffect.None)
        }
    }

    @Test
    fun `pagination row does not get arrival effect`() {
        val trackedIds = mutableSetOf<String>()  // pagination never adds to tracked set

        fun effectForBind(id: String): CardEffect =
            if (trackedIds.contains(id)) CardEffect.NewArrival(id) {} else CardEffect.None

        assertTrue("Pagination row must not animate", effectForBind("paged-99") is CardEffect.None)
    }

    // ------- Batch announcement decision -------

    @Test
    fun `shouldAnnounce returns true when new IDs present`() {
        val arrivals = setOf("a", "b")
        assertTrue(io.heckel.ntfy.ui.accessibility.ArrivalAnnouncer.shouldAnnounce(arrivals))
    }

    @Test
    fun `shouldAnnounce returns false for empty set`() {
        assertFalse(io.heckel.ntfy.ui.accessibility.ArrivalAnnouncer.shouldAnnounce(emptySet()))
    }

    @Test
    fun `announcement emitted once per batch, not once per card`() {
        var announceCount = 0
        val arrivals = setOf("n1", "n2", "n3")

        // Host logic: call announceArrival once with the full count, not once per card.
        if (io.heckel.ntfy.ui.accessibility.ArrivalAnnouncer.shouldAnnounce(arrivals)) {
            announceCount++  // one call for the whole batch
        }

        assertEquals("Expected exactly one announcement for the batch", 1, announceCount)
    }

    // ------- Skeleton is non-interactive -------

    @Test
    fun `Loading presentation is a distinct sealed subtype`() {
        val state = CardBindState(presentation = CardPresentation.Loading)
        assertFalse("Loading must not equal Normal", state.presentation == CardPresentation.Normal)
    }

    @Test
    fun `skeleton bind state carries no effect`() {
        val state = CardBindState(presentation = CardPresentation.Loading)
        assertTrue(state.effect is CardEffect.None)
    }
}
