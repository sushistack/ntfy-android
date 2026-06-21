package io.heckel.ntfy.ui

import io.heckel.ntfy.ui.CardTagFormatter.GeneralTag
import io.heckel.ntfy.ui.CardTagFormatter.categorize
import io.heckel.ntfy.ui.CardTagFormatter.formatAbsoluteTimestamp
import io.heckel.ntfy.ui.CardTagFormatter.webHash
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * JVM unit tests for CardTagFormatter pure logic.
 *
 * EmojiManager uses Android's org.json in its static initializer, which is stubbed in JVM
 * unit tests. Tests that need emoji exclusion inject a known-alias set via the isEmoji lambda
 * rather than invoking the real EmojiManager. Tests for hash, ordering, and timestamp do not
 * need the emoji lookup at all.
 */
class CardTagFormatterTest {

    // Known emoji aliases used to drive the isEmoji seam in tests
    private val knownEmojis = setOf("warning", "skull", "white_check_mark", "+1", "tada")
    private val testIsEmoji: (String) -> Boolean = { it in knownEmojis }

    // Shorthand: categorize with emoji lookup disabled (most ordering/exclusion tests)
    private fun cat(rawTags: String?, topicName: String? = null) =
        categorize(rawTags, topicName, isEmoji = { false })

    // Shorthand: categorize with test emoji set
    private fun catWithEmoji(rawTags: String?, topicName: String? = null) =
        categorize(rawTags, topicName, isEmoji = testIsEmoji)

    // ── hash parity ──────────────────────────────────────────────────────────────

    @Test
    fun `webHash golden vectors match web implementation`() {
        assertEquals(4, webHash("warning"))
        assertEquals(3, webHash("skull"))
        assertEquals(1, webHash("deployment"))
        assertEquals(2, webHash("backend"))
        assertEquals(2, webHash("alpha"))
        assertEquals(0, webHash("서비스"))  // non-ASCII BMP — catches byte-based hashing
    }

    @Test
    fun `webHash stays in 0-5 range for arbitrary inputs`() {
        listOf("", "a", "test", "Hello World", "12345", "emoji_excluded", "zzzzzzzzzzzzz").forEach { s ->
            val idx = webHash(s)
            assertTrue("webHash($s)=$idx must be 0..5", idx in 0..5)
        }
    }

    // ── categorize — card marker exclusion ───────────────────────────────────────

    @Test
    fun `card exact marker excluded, card-substring retained`() {
        val result = cat("card,cardboard,card")
        assertEquals(listOf(GeneralTag("cardboard", webHash("cardboard"))), result.general)
        assertNull(result.topic)
        assertTrue(result.service.isEmpty())
    }

    // ── categorize — emoji exclusion (via isEmoji seam) ──────────────────────────

    @Test
    fun `emoji alias excluded, non-emoji general tag retained`() {
        // "warning" is in our test emoji set; "mywarning" is not
        val result = catWithEmoji("warning,mywarning")
        assertTrue("emoji alias 'warning' must be excluded", result.general.none { it.name == "warning" })
        assertTrue("non-emoji 'mywarning' must be retained", result.general.any { it.name == "mywarning" })
    }

    @Test
    fun `emoji alias +1 excluded`() {
        val result = catWithEmoji("+1,regular")
        assertTrue(result.general.none { it.name == "+1" })
        assertEquals(listOf(GeneralTag("regular", webHash("regular"))), result.general)
    }

    // ── categorize — zero / empty tags ───────────────────────────────────────────

    @Test
    fun `zero tags produces empty categories`() {
        val result = cat(null)
        assertNull(result.topic)
        assertTrue(result.service.isEmpty())
        assertTrue(result.general.isEmpty())
    }

    @Test
    fun `empty string tags produces empty categories`() {
        val result = cat("")
        assertTrue(result.service.isEmpty())
        assertTrue(result.general.isEmpty())
    }

    // ── categorize — service: prefix ─────────────────────────────────────────────

    @Test
    fun `service prefix stripped for display`() {
        val result = cat("service:database,service:cache")
        assertEquals(listOf("database", "cache"), result.service)
        assertTrue(result.general.isEmpty())
    }

    @Test
    fun `empty service remainder skipped`() {
        val result = cat("service:")
        assertTrue(result.service.isEmpty())
        assertTrue(result.general.isEmpty())
    }

    @Test
    fun `service prefix is case-sensitive`() {
        val result = cat("Service:foo")
        assertTrue(result.service.isEmpty())
        assertEquals(listOf(GeneralTag("Service:foo", webHash("Service:foo"))), result.general)
    }

    // ── categorize — ordering ────────────────────────────────────────────────────

    @Test
    fun `category order is topic then service then general`() {
        val result = cat("general1,service:svc1,general2,service:svc2", topicName = "mytopic")
        assertEquals("mytopic", result.topic)
        assertEquals(listOf("svc1", "svc2"), result.service)
        assertEquals(
            listOf(GeneralTag("general1", webHash("general1")), GeneralTag("general2", webHash("general2"))),
            result.general,
        )
    }

    @Test
    fun `order within service category preserved`() {
        val result = cat("service:z,service:a,service:m")
        assertEquals(listOf("z", "a", "m"), result.service)
    }

    @Test
    fun `order within general category preserved`() {
        val result = cat("z,a,m")
        assertEquals(listOf("z", "a", "m"), result.general.map { it.name })
    }

    // ── categorize — topic input ─────────────────────────────────────────────────

    @Test
    fun `topic null when binder passes null`() {
        val result = cat("tag1")
        assertNull(result.topic)
    }

    @Test
    fun `topic set when binder passes non-null`() {
        val result = cat("tag1", topicName = "alerts")
        assertEquals("alerts", result.topic)
    }

    @Test
    fun `topic is not sourced from Notification tags field`() {
        val result = cat("alerts", topicName = null)
        assertNull(result.topic)
        assertEquals(listOf(GeneralTag("alerts", webHash("alerts"))), result.general)
    }

    // ── categorize — duplicate / mixed ───────────────────────────────────────────

    @Test
    fun `duplicate tags preserved in order`() {
        val result = cat("foo,bar,foo")
        assertEquals(listOf("foo", "bar", "foo"), result.general.map { it.name })
    }

    @Test
    fun `mixed service and general tags categorized correctly`() {
        val result = cat("tagA,service:svcA,tagB,service:svcB,tagC")
        assertEquals(listOf("svcA", "svcB"), result.service)
        assertEquals(listOf("tagA", "tagB", "tagC"), result.general.map { it.name })
    }

    // ── timestamp ────────────────────────────────────────────────────────────────

    private val savedDefault: TimeZone = TimeZone.getDefault()

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(savedDefault)
    }

    @Test
    fun `formatAbsoluteTimestamp epoch in UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("1970-01-01 00:00:00", formatAbsoluteTimestamp(0L))
    }

    @Test
    fun `formatAbsoluteTimestamp known instant in UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // 2024-06-21 12:00:00 UTC
        assertEquals("2024-06-21 12:00:00", formatAbsoluteTimestamp(1718971200L))
    }

    @Test
    fun `formatAbsoluteTimestamp shifts for non-UTC zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))  // UTC+9
        // 2024-06-21 12:00:00 UTC → 2024-06-21 21:00:00 KST
        assertEquals("2024-06-21 21:00:00", formatAbsoluteTimestamp(1718971200L))
    }

    @Test
    fun `formatAbsoluteTimestamp DST-sensitive zone winter`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        // 2024-01-15 12:00:00 UTC → EST (UTC-5) → 2024-01-15 07:00:00
        assertEquals("2024-01-15 07:00:00", formatAbsoluteTimestamp(1705320000L))
    }

    @Test
    fun `formatAbsoluteTimestamp DST-sensitive zone summer`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        // 2024-07-15 12:00:00 UTC → EDT (UTC-4) → 2024-07-15 08:00:00
        assertEquals("2024-07-15 08:00:00", formatAbsoluteTimestamp(1721044800L))
    }

    @Test
    fun `formatAbsoluteTimestamp uses calendar year not week-based year`() {
        // 2021-01-01 is in ISO week 53 of 2020 — YYYY would produce 2020, yyyy must produce 2021
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = formatAbsoluteTimestamp(1609459200L) // 2021-01-01 00:00:00 UTC
        assertTrue("Must start with calendar year 2021, got: $result", result.startsWith("2021-"))
    }

    @Test
    fun `formatAbsoluteTimestamp input is Unix seconds not milliseconds`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = formatAbsoluteTimestamp(1_000_000L) // ~11.57 days after epoch
        assertTrue("Seconds-based: $result", result.startsWith("1970-01-12"))
    }

    @Test
    fun `formatAbsoluteTimestamp output uses ASCII digits regardless of locale`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = formatAbsoluteTimestamp(1718971200L)
        assertTrue(result.all { it.isDigit() || it == '-' || it == ' ' || it == ':' })
    }

    // ── palette pairs ────────────────────────────────────────────────────────────

    @Test
    fun `webHash 0 maps to first palette entry for known vector`() {
        assertEquals(0, webHash("서비스"))
    }

    @Test
    fun `palette index range for all golden vectors`() {
        val vectors = mapOf(
            "warning" to 4,
            "skull" to 3,
            "deployment" to 1,
            "backend" to 2,
            "alpha" to 2,
            "서비스" to 0,
        )
        vectors.forEach { (tag, expected) ->
            assertEquals("webHash($tag)", expected, webHash(tag))
        }
    }
}
