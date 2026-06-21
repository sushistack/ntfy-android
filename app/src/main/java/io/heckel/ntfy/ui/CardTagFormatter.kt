package io.heckel.ntfy.ui

import io.heckel.ntfy.util.splitTags
import io.heckel.ntfy.util.toEmoji
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure tag categorization and absolute timestamp formatting for the card meta row.
 *
 * Design contract (Story 2.4):
 * - topic chip comes from binder input, not Notification.tags
 * - service: prefix stripped for display; empty remainder skipped
 * - "card" exact marker excluded; emoji aliases excluded via existing toEmoji()
 * - general tags hashed with web-compatible unsigned 32-bit hash → palette index 0..5
 * - timestamp: Unix seconds → local wall time, pattern yyyy-MM-dd HH:mm:ss, Locale.ROOT
 */
object CardTagFormatter {

    private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    private const val SERVICE_PREFIX = "service:"
    private const val CARD_MARKER = "card"

    data class CardTags(
        val topic: String?,
        val service: List<String>,
        val general: List<GeneralTag>,
    )

    data class GeneralTag(
        val name: String,
        val paletteIndex: Int,
    )

    /**
     * Categorize raw Notification.tags string into a CardTags model.
     *
     * @param rawTags  Notification.tags (comma-separated, may be null/empty)
     * @param topicName  nullable topic/display name injected by binder
     * @param isEmoji  override for testing; defaults to the installed EmojiManager lookup
     */
    @JvmOverloads
    fun categorize(
        rawTags: String?,
        topicName: String?,
        isEmoji: (String) -> Boolean = { tag -> toEmoji(tag) != null },
    ): CardTags {
        val all = splitTags(rawTags)
        val service = mutableListOf<String>()
        val general = mutableListOf<GeneralTag>()

        for (tag in all) {
            if (tag.isBlank()) continue
            when {
                tag == CARD_MARKER -> continue
                isEmoji(tag) -> continue
                tag.startsWith(SERVICE_PREFIX) -> {
                    val remainder = tag.removePrefix(SERVICE_PREFIX)
                    if (remainder.isNotBlank()) service.add(remainder)
                }
                else -> general.add(GeneralTag(tag, webHash(tag)))
            }
        }

        return CardTags(
            topic = topicName,
            service = service,
            general = general,
        )
    }

    /**
     * Web-compatible unsigned 32-bit hash over UTF-16 code units, modulo 6.
     * Matches JavaScript: h = h * 31 + charCodeAt(i), unsigned modulo.
     */
    fun webHash(name: String): Int {
        var h = 0u
        for (ch in name) {
            h = h * 31u + ch.code.toUInt()
        }
        return (h % 6u).toInt()
    }

    /**
     * Format Unix-second timestamp as absolute local wall-clock time.
     * Pattern: yyyy-MM-dd HH:mm:ss, Locale.ROOT (calendar year, not week-based).
     */
    fun formatAbsoluteTimestamp(timestampSecs: Long): String {
        return TIMESTAMP_FMT.format(
            Instant.ofEpochSecond(timestampSecs).atZone(ZoneId.systemDefault())
        )
    }
}
