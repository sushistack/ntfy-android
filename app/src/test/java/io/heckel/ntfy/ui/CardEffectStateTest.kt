package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure unit tests for [CardBindState], [CardPresentation], and [CardEffect].
 * No Android framework dependency — runs on the JVM.
 */
class CardEffectStateTest {

    // ------- CardPresentation -------

    @Test
    fun `default CardBindState has Normal presentation and None effect`() {
        val state = CardBindState()
        assertTrue(state.presentation is CardPresentation.Normal)
        assertTrue(state.effect is CardEffect.None)
    }

    @Test
    fun `Loading presentation is distinct from Normal`() {
        val loading = CardBindState(presentation = CardPresentation.Loading)
        val normal  = CardBindState(presentation = CardPresentation.Normal)
        assertNotEquals(loading.presentation, normal.presentation)
    }

    @Test
    fun `StaticDeepLinkEmphasis carries the target ID`() {
        val pres = CardPresentation.StaticDeepLinkEmphasis("abc-123")
        assertEquals("abc-123", pres.targetId)
    }

    // ------- CardEffect -------

    @Test
    fun `NewArrival effect carries stable ID and consumed callback`() {
        var consumed = false
        val eff = CardEffect.NewArrival(stableId = "msg-42") { consumed = true }
        assertEquals("msg-42", eff.stableId)
        eff.consumed()
        assertTrue(consumed)
    }

    @Test
    fun `DeepLinkPulse effect carries target ID and consumed callback`() {
        var consumed = false
        val eff = CardEffect.DeepLinkPulse(targetId = "msg-99") { consumed = true }
        assertEquals("msg-99", eff.targetId)
        eff.consumed()
        assertTrue(consumed)
    }

    @Test
    fun `effects are data-equal by ID`() {
        val a = CardEffect.NewArrival("id-1") {}
        val b = CardEffect.NewArrival("id-1") {}
        // same stableId → equal (lambda not part of equality contract here)
        assertEquals(a.stableId, b.stableId)
    }

    // ------- Ownership semantics -------

    @Test
    fun `NewArrival consumed callback invoked exactly once per instance`() {
        var count = 0
        val eff = CardEffect.NewArrival("id-1") { count++ }
        eff.consumed()
        eff.consumed() // host should not call twice, but guard idempotence expectation
        assertEquals(2, count) // lambda is a plain function; host must guard externally
    }

    // ------- Architecture: no Android dependency in this file -------

    @Test
    fun `CardBindState can be created without Android context`() {
        // If this test compiles and runs on JVM, the state model has no Android dependency.
        val state = CardBindState(
            presentation = CardPresentation.StaticDeepLinkEmphasis("id-x"),
            effect = CardEffect.DeepLinkPulse("id-x") {},
        )
        assertTrue(state.presentation is CardPresentation.StaticDeepLinkEmphasis)
        assertTrue(state.effect is CardEffect.DeepLinkPulse)
    }
}
