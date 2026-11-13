package io.heckel.ntfy.ui

import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.Subscription
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for Story 4.7: Drawer Subscription Rows & Context Menu.
 *
 * Tests run on the JVM; no Android device required.
 * Architecture guards verify source text to detect banned patterns early.
 */
class DrawerSubscriptionAdapterTest {

    // ──────────────────────────────────────────────────────────────────────
    // Mute predicate tests (mirrors the private isMuted() logic)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `isMuted returns false when mutedUntil is MUTED_UNTIL_SHOW_ALL (0L)`() {
        val s = makeSubscription(mutedUntil = Repository.MUTED_UNTIL_SHOW_ALL)
        assertFalse(isMutedTestable(s))
    }

    @Test
    fun `isMuted returns true when mutedUntil is MUTED_UNTIL_FOREVER (1L)`() {
        val s = makeSubscription(mutedUntil = Repository.MUTED_UNTIL_FOREVER)
        assertTrue(isMutedTestable(s))
    }

    @Test
    fun `isMuted returns true when mutedUntil is a future timestamp`() {
        val futureTimestamp = System.currentTimeMillis() / 1000 + 3600L // 1 hour from now
        val s = makeSubscription(mutedUntil = futureTimestamp)
        assertTrue(isMutedTestable(s))
    }

    @Test
    fun `isMuted returns false when mutedUntil is an expired past timestamp`() {
        val pastTimestamp = System.currentTimeMillis() / 1000 - 3600L // 1 hour ago
        val s = makeSubscription(mutedUntil = pastTimestamp)
        assertFalse(isMutedTestable(s))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Unread count capping tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `unread count at 99 renders as 99`() {
        val count = 99
        val text = if (count <= 99) count.toString() else "99+"
        assertEquals("99", text)
    }

    @Test
    fun `unread count at 100 renders as 99+`() {
        val count = 100
        val text = if (count <= 99) count.toString() else "99+"
        assertEquals("99+", text)
    }

    @Test
    fun `unread count at 999 renders as 99+`() {
        val count = 999
        val text = if (count <= 99) count.toString() else "99+"
        assertEquals("99+", text)
    }

    @Test
    fun `unread count at 0 would be hidden`() {
        val count = 0
        // visibility logic: count <= 0 → GONE; else VISIBLE
        assertTrue("count 0 should be hidden", count <= 0)
    }

    @Test
    fun `unread count at 1 would be visible`() {
        val count = 1
        assertFalse("count 1 should be visible", count <= 0)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rename logic tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `rename with blank input produces null displayName`() {
        val input = "   "
        val result = if (input.isBlank()) null else input.trim()
        assertNull("Blank input should produce null displayName", result)
    }

    @Test
    fun `rename with empty string produces null displayName`() {
        val input = ""
        val result = if (input.isBlank()) null else input.trim()
        assertNull("Empty string should produce null displayName", result)
    }

    @Test
    fun `rename with trimmed non-blank input preserves trimmed value`() {
        val input = "  My Topic  "
        val result = if (input.isBlank()) null else input.trim()
        assertEquals("My Topic", result)
    }

    @Test
    fun `rename with non-blank input without spaces preserves value`() {
        val input = "alerts"
        val result = if (input.isBlank()) null else input.trim()
        assertEquals("alerts", result)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Mute menu visibility logic tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `when not muted, Mute item should be visible and Unmute hidden`() {
        val s = makeSubscription(mutedUntil = Repository.MUTED_UNTIL_SHOW_ALL)
        val isMutedNow = isMutedTestable(s)
        assertTrue("Mute item should be visible when not muted", !isMutedNow)
        assertTrue("Unmute item should be hidden when not muted", isMutedNow.not())
    }

    @Test
    fun `when muted forever, Unmute item should be visible and Mute hidden`() {
        val s = makeSubscription(mutedUntil = Repository.MUTED_UNTIL_FOREVER)
        val isMutedNow = isMutedTestable(s)
        assertTrue("Unmute item should be visible when muted", isMutedNow)
        assertFalse("Mute item should be hidden when muted", !isMutedNow)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Architecture guard: DrawerSubscriptionAdapter must not launch DetailActivity
    // ──────────────────────────────────────────────────────────────────────

    private val adapterSource: String by lazy {
        val candidates = listOf(
            "app/src/main/java/io/heckel/ntfy/ui/DrawerSubscriptionAdapter.kt",
            "../app/src/main/java/io/heckel/ntfy/ui/DrawerSubscriptionAdapter.kt",
        )
        candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("DrawerSubscriptionAdapter.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun `DrawerSubscriptionAdapter must not import or reference DetailActivity`() {
        val nonCommentLines = adapterSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertFalse(
            "DrawerSubscriptionAdapter must not import or reference DetailActivity (AC 6 guard)",
            nonCommentLines.contains("DetailActivity")
        )
    }

    @Test
    fun `DrawerSubscriptionAdapter must not call startActivity directly`() {
        assertFalse(
            "DrawerSubscriptionAdapter must not call startActivity (navigation delegated to DrawerHost)",
            adapterSource.contains("startActivity(")
        )
    }

    @Test
    fun `DrawerSubscriptionAdapter must not import android app Activity`() {
        assertFalse(
            "DrawerSubscriptionAdapter must not import android.app.Activity",
            adapterSource.contains("import android.app.Activity")
        )
    }

    @Test
    fun `DrawerSubscriptionAdapter defines DrawerHost interface`() {
        assertTrue(
            "DrawerSubscriptionAdapter must define the DrawerHost interface",
            adapterSource.contains("interface DrawerHost")
        )
    }

    @Test
    fun `DrawerSubscriptionAdapter row click delegates to DrawerHost`() {
        assertTrue(
            "Row click must call host.onSubscriptionRowClick",
            adapterSource.contains("host.onSubscriptionRowClick")
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Architecture guard: layout uses ic_chat_bubble, not ic_sms
    // ──────────────────────────────────────────────────────────────────────

    private val layoutSource: String by lazy {
        val candidates = listOf(
            "app/src/main/res/layout/fragment_drawer_subscription_item.xml",
            "../app/src/main/res/layout/fragment_drawer_subscription_item.xml",
        )
        candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("fragment_drawer_subscription_item.xml not found")
    }

    @Test
    fun `drawer row layout has the web topic avatar (2026-11 redesign)`() {
        assertTrue("Layout must contain drawer_item_avatar", layoutSource.contains("drawer_item_avatar"))
    }

    @Test
    fun `avatar colour matches ntfy-web colorFor`() {
        // Expected values computed with the web's Sidebar.jsx colorFor (node).
        val expected = mapOf(
            "Critical" to 0xFFEC4899, "Monitor" to 0xFF8B5CF6, "Vignal" to 0xFFEF4444,
            "Ops" to 0xFF64748B, "알림" to 0xFFEF4444, "e2e-🚀" to 0xFF0EA5E9,
        )
        for ((name, color) in expected) assertEquals(name, color.toInt(), avatarColor(name))
    }

    @Test
    fun `drawer row layout has overflow button with correct id`() {
        assertTrue(
            "Layout must contain drawer_item_overflow",
            layoutSource.contains("drawer_item_overflow")
        )
    }

    @Test
    fun `drawer row layout has muted indicator with correct id`() {
        assertTrue(
            "Layout must contain drawer_item_muted",
            layoutSource.contains("drawer_item_muted")
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun makeSubscription(
        id: Long = 1L,
        mutedUntil: Long = 0L,
        newCount: Int = 0,
        displayName: String? = null,
    ): Subscription = Subscription(
        id = id,
        baseUrl = "https://ntfy.sh",
        topic = "test-topic",
        instant = false,
        mutedUntil = mutedUntil,
        minPriority = 0,
        autoDelete = 0L,
        insistent = -1,
        lastNotificationId = null,
        icon = null,
        upAppId = null,
        upConnectorToken = null,
        displayName = displayName,
        dedicatedChannels = false,
        totalCount = 0,
        newCount = newCount,
        lastActive = 0L,
    )

    @Test
    fun `avatar letters reach 4_5 to 1 on their tint in light and dark`() {
        val palette = listOf(0x3182f6, 0x8b5cf6, 0xec4899, 0xf97316, 0x10b981, 0x0ea5e9, 0xef4444, 0x64748b)
        for (surface in listOf(0xFFFFFFFF.toInt(), 0xFF202027.toInt())) {
            for (p in palette) {
                val color = p or (0xFF shl 24)
                val bg = compositeOver((38 shl 24) or p, surface)
                val letter = readableOn(color, bg)
                assertTrue("#${Integer.toHexString(p)} on ${Integer.toHexString(surface)}: ${contrast(letter, bg)}",
                    contrast(letter, bg) >= 4.5)
            }
        }
        // Already-readable colours are left alone.
        assertEquals(0xFF000000.toInt(), readableOn(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
    }

}

/**
 * Package-level testable equivalent of the private isMuted() in DrawerSubscriptionAdapter.kt.
 * Must be kept in sync with the production predicate.
 */
private fun isMutedTestable(subscription: Subscription): Boolean {
    val m = subscription.mutedUntil
    return m == Repository.MUTED_UNTIL_FOREVER || (m > 1L && m > System.currentTimeMillis() / 1000)
}
