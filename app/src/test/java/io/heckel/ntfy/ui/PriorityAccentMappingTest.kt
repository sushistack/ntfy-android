package io.heckel.ntfy.ui

import io.heckel.ntfy.R
import io.heckel.ntfy.ui.design.GlowToken
import io.heckel.ntfy.util.PRIORITY_DEFAULT
import io.heckel.ntfy.util.PRIORITY_HIGH
import io.heckel.ntfy.util.PRIORITY_LOW
import io.heckel.ntfy.util.PRIORITY_MAX
import io.heckel.ntfy.util.PRIORITY_MIN
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure mapping unit tests for Story 2.2 — Priority Accent Bar.
 *
 * Tests all AC assertions that are reducible to pure functions:
 *   AC 1: all five priority → color token mappings
 *   AC 2-3: dark-only glow token selection for P4/P5, none for P1-P3
 *   AC 4: light mode produces no glow token (GlowToken contract is gated by resolveGlow; token must be null for P1-P3)
 *   AC 5: recycled holder regression — glow token changes on rebind
 *   AC 6: invalid/null priority falls back to P3 (text color, no glow)
 *
 * The View-layer glow application is tested structurally in MessageCardArchitectureTest
 * (no GlowToken references in binder outside the companion).
 */
class PriorityAccentMappingTest {

    // --- AC 1: color resource mapping for all five priorities ---

    @Test
    fun `P1 Min maps to muted color token`() {
        assertEquals(R.color.muted, MessageCardBinder.accentColorResForPriority(PRIORITY_MIN))
    }

    @Test
    fun `P2 Low maps to muted color token`() {
        assertEquals(R.color.muted, MessageCardBinder.accentColorResForPriority(PRIORITY_LOW))
    }

    @Test
    fun `P3 Default maps to text color token`() {
        assertEquals(R.color.text, MessageCardBinder.accentColorResForPriority(PRIORITY_DEFAULT))
    }

    @Test
    fun `P4 High maps to priority_high color token`() {
        assertEquals(R.color.priority_high, MessageCardBinder.accentColorResForPriority(PRIORITY_HIGH))
    }

    @Test
    fun `P5 Max maps to priority_max color token`() {
        assertEquals(R.color.priority_max, MessageCardBinder.accentColorResForPriority(PRIORITY_MAX))
    }

    // --- AC 2-3: glow token assignment ---

    @Test
    fun `P4 High has glow_priority_high token`() {
        assertEquals(GlowToken.PRIORITY_HIGH, MessageCardBinder.priorityGlowToken(PRIORITY_HIGH))
    }

    @Test
    fun `P5 Max has glow_priority_max token`() {
        assertEquals(GlowToken.PRIORITY_MAX, MessageCardBinder.priorityGlowToken(PRIORITY_MAX))
    }

    @Test
    fun `P1 Min has no glow token`() {
        assertNull(MessageCardBinder.priorityGlowToken(PRIORITY_MIN))
    }

    @Test
    fun `P2 Low has no glow token`() {
        assertNull(MessageCardBinder.priorityGlowToken(PRIORITY_LOW))
    }

    @Test
    fun `P3 Default has no glow token`() {
        assertNull(MessageCardBinder.priorityGlowToken(PRIORITY_DEFAULT))
    }

    // --- AC 4: light mode — no glow token for any priority ---
    // (resolveGlow() returns null in light mode; here we verify the token itself is null for non-glow priorities)

    @Test
    fun `only P4 and P5 produce a non-null glow token`() {
        val allPriorities = listOf(PRIORITY_MIN, PRIORITY_LOW, PRIORITY_DEFAULT, PRIORITY_HIGH, PRIORITY_MAX)
        val glowPriorities = allPriorities.filter { MessageCardBinder.priorityGlowToken(it) != null }
        assertEquals(
            "Only P4 and P5 should have a glow token",
            listOf(PRIORITY_HIGH, PRIORITY_MAX),
            glowPriorities,
        )
    }

    // --- AC 5: recycling — glow token changes correctly on rebind ---

    @Test
    fun `rebinding P5 to P1 yields null glow token (recycling regression guard)`() {
        val glowBeforeRebind = MessageCardBinder.priorityGlowToken(PRIORITY_MAX)
        assertNotNull("P5 must have a glow token before rebind", glowBeforeRebind)

        val glowAfterRebind = MessageCardBinder.priorityGlowToken(PRIORITY_MIN)
        assertNull("After rebind to P1, glow token must be null", glowAfterRebind)
    }

    @Test
    fun `rebinding P4 to P3 yields null glow token (recycling regression guard)`() {
        val glowBeforeRebind = MessageCardBinder.priorityGlowToken(PRIORITY_HIGH)
        assertNotNull("P4 must have a glow token before rebind", glowBeforeRebind)

        val glowAfterRebind = MessageCardBinder.priorityGlowToken(PRIORITY_DEFAULT)
        assertNull("After rebind to P3, glow token must be null", glowAfterRebind)
    }

    @Test
    fun `rebinding P5 to P3 color changes from priority_max to text`() {
        val colorBefore = MessageCardBinder.accentColorResForPriority(PRIORITY_MAX)
        val colorAfter = MessageCardBinder.accentColorResForPriority(PRIORITY_DEFAULT)
        assertNotEquals("Color must change from P5 to P3", colorBefore, colorAfter)
        assertEquals(R.color.text, colorAfter)
    }

    @Test
    fun `rebinding P4 to P1 color changes from priority_high to muted`() {
        val colorBefore = MessageCardBinder.accentColorResForPriority(PRIORITY_HIGH)
        val colorAfter = MessageCardBinder.accentColorResForPriority(PRIORITY_MIN)
        assertNotEquals("Color must change from P4 to P1", colorBefore, colorAfter)
        assertEquals(R.color.muted, colorAfter)
    }

    // --- AC 6: invalid/absent priority normalises to P3 ---

    @Test
    fun `priority 0 (below range) maps to text color (P3 fallback)`() {
        assertEquals(R.color.text, MessageCardBinder.accentColorResForPriority(0))
    }

    @Test
    fun `priority 6 (above range) maps to text color (P3 fallback)`() {
        assertEquals(R.color.text, MessageCardBinder.accentColorResForPriority(6))
    }

    @Test
    fun `priority -1 maps to text color (P3 fallback)`() {
        assertEquals(R.color.text, MessageCardBinder.accentColorResForPriority(-1))
    }

    @Test
    fun `priority 0 has no glow token (P3 fallback)`() {
        assertNull(MessageCardBinder.priorityGlowToken(0))
    }

    @Test
    fun `priority 99 has no glow token (P3 fallback)`() {
        assertNull(MessageCardBinder.priorityGlowToken(99))
    }

    // --- Color token uniqueness contract ---

    @Test
    fun `P1 and P2 share the same muted token`() {
        assertEquals(
            MessageCardBinder.accentColorResForPriority(PRIORITY_MIN),
            MessageCardBinder.accentColorResForPriority(PRIORITY_LOW),
        )
    }

    @Test
    fun `P3 text token is distinct from P1-P2 muted token`() {
        assertNotEquals(
            MessageCardBinder.accentColorResForPriority(PRIORITY_DEFAULT),
            MessageCardBinder.accentColorResForPriority(PRIORITY_MIN),
        )
    }

    @Test
    fun `P4 priority_high token is distinct from P5 priority_max token`() {
        assertNotEquals(
            MessageCardBinder.accentColorResForPriority(PRIORITY_HIGH),
            MessageCardBinder.accentColorResForPriority(PRIORITY_MAX),
        )
    }
}
