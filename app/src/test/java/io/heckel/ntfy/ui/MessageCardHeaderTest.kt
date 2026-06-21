package io.heckel.ntfy.ui

import io.heckel.ntfy.R
import io.heckel.ntfy.util.PRIORITY_DEFAULT
import io.heckel.ntfy.util.PRIORITY_HIGH
import io.heckel.ntfy.util.PRIORITY_LOW
import io.heckel.ntfy.util.PRIORITY_MAX
import io.heckel.ntfy.util.PRIORITY_MIN
import io.heckel.ntfy.util.toPriority
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM tests for Story 2.3a badge mapping, title selection, and reset contract.
 * All tests call pure companion-object functions; no Android runtime required.
 */
class MessageCardHeaderTest {

    // ── Badge mapping (AC 1, 2) ──────────────────────────────────────────────

    @Test
    fun `P1 min badge uses surface_2 bg and muted text`() {
        val spec = MessageCardBinder.badgeSpecForPriority(PRIORITY_MIN)
        assertEquals(R.string.notification_card_badge_min,    spec.labelRes)
        assertEquals(R.color.surface_2, spec.backgroundColorRes)
        assertEquals(R.color.muted,     spec.textColorRes)
    }

    @Test
    fun `P2 low badge uses surface_2 bg and muted text`() {
        val spec = MessageCardBinder.badgeSpecForPriority(PRIORITY_LOW)
        assertEquals(R.string.notification_card_badge_low,    spec.labelRes)
        assertEquals(R.color.surface_2, spec.backgroundColorRes)
        assertEquals(R.color.muted,     spec.textColorRes)
    }

    @Test
    fun `P3 normal badge uses surface_2 bg and text color`() {
        val spec = MessageCardBinder.badgeSpecForPriority(PRIORITY_DEFAULT)
        assertEquals(R.string.notification_card_badge_normal, spec.labelRes)
        assertEquals(R.color.surface_2, spec.backgroundColorRes)
        assertEquals(R.color.text,      spec.textColorRes)
    }

    @Test
    fun `P4 high badge uses priority_high bg and priority_high_on_surface text`() {
        val spec = MessageCardBinder.badgeSpecForPriority(PRIORITY_HIGH)
        assertEquals(R.string.notification_card_badge_high,         spec.labelRes)
        assertEquals(R.color.priority_high,            spec.backgroundColorRes)
        assertEquals(R.color.priority_high_on_surface, spec.textColorRes)
    }

    @Test
    fun `P5 max badge uses priority_max bg and priority_max_on_surface text`() {
        val spec = MessageCardBinder.badgeSpecForPriority(PRIORITY_MAX)
        assertEquals(R.string.notification_card_badge_max,          spec.labelRes)
        assertEquals(R.color.priority_max,             spec.backgroundColorRes)
        assertEquals(R.color.priority_max_on_surface,  spec.textColorRes)
    }

    @Test
    fun `invalid priority 0 normalizes to P3 via toPriority`() {
        val normalized = toPriority(0)
        assertEquals("invalid priority must normalize to PRIORITY_DEFAULT", PRIORITY_DEFAULT, normalized)
        val spec = MessageCardBinder.badgeSpecForPriority(normalized)
        assertEquals("normalized P3 badge label", R.string.notification_card_badge_normal, spec.labelRes)
    }

    @Test
    fun `null priority normalizes to P3 via toPriority`() {
        val normalized = toPriority(null)
        assertEquals("null priority must normalize to PRIORITY_DEFAULT", PRIORITY_DEFAULT, normalized)
        val spec = MessageCardBinder.badgeSpecForPriority(normalized)
        assertEquals("normalized P3 badge label", R.string.notification_card_badge_normal, spec.labelRes)
    }

    @Test
    fun `out-of-range priority 99 normalizes to P3 via toPriority`() {
        val normalized = toPriority(99)
        assertEquals(PRIORITY_DEFAULT, normalized)
        val spec = MessageCardBinder.badgeSpecForPriority(normalized)
        assertEquals(R.string.notification_card_badge_normal, spec.labelRes)
    }

    // ── All five priorities covered (table completeness) ─────────────────────

    @Test
    fun `all five badge label resources are distinct`() {
        val labels = (PRIORITY_MIN..PRIORITY_MAX).map { MessageCardBinder.badgeSpecForPriority(it).labelRes }
        assertEquals("each priority must have a distinct label resource", labels.distinct().size, 5)
    }

    // ── Badge backgroundColorRes reflects AC 1 token contract ────────────────

    @Test
    fun `P1 and P2 share surface_2 background`() {
        assertEquals(
            MessageCardBinder.badgeSpecForPriority(PRIORITY_MIN).backgroundColorRes,
            MessageCardBinder.badgeSpecForPriority(PRIORITY_LOW).backgroundColorRes,
        )
    }

    @Test
    fun `P4 and P5 do not share background`() {
        assertNotEquals(
            MessageCardBinder.badgeSpecForPriority(PRIORITY_HIGH).backgroundColorRes,
            MessageCardBinder.badgeSpecForPriority(PRIORITY_MAX).backgroundColorRes,
        )
    }

    // ── Glow token mapping (AC 6) ─────────────────────────────────────────────

    @Test
    fun `P4 glow token is priority_high`() {
        assertEquals(io.heckel.ntfy.ui.design.GlowToken.PRIORITY_HIGH, MessageCardBinder.priorityGlowToken(PRIORITY_HIGH))
    }

    @Test
    fun `P5 glow token is priority_max`() {
        assertEquals(io.heckel.ntfy.ui.design.GlowToken.PRIORITY_MAX, MessageCardBinder.priorityGlowToken(PRIORITY_MAX))
    }

    @Test
    fun `P1-P3 have no glow token`() {
        listOf(PRIORITY_MIN, PRIORITY_LOW, PRIORITY_DEFAULT).forEach { p ->
            assertNull("P$p must have no glow token", MessageCardBinder.priorityGlowToken(p))
        }
    }
}
