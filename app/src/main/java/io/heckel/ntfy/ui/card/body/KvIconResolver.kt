package io.heckel.ntfy.ui.card.body

import java.util.Locale

/**
 * Canonical icon resolution for kv rows (AC 2, Story 3.0 parity).
 *
 * Resolution algorithm:
 *  1. Use non-null [icon] if present; otherwise use [key].
 *  2. Lowercase with Locale.ROOT.
 *  3. Exact match against the canonical map.
 *  4. First whitespace-delimited word, then exact match.
 *  5. Return fallback "·".
 *
 * The glyph map is byte-for-exact match with the Story 3.0 golden corpus.
 */
object KvIconResolver {

    const val FALLBACK = "·"

    // Canonical glyph map — exact values from message-format.md §4 and Story 3.0 corpus.
    private val GLYPH_MAP: Map<String, String> = mapOf(
        "cpu" to "⚙",
        "disk" to "💾",
        "memory" to "🧠",
        "mem" to "🧠",
        "ram" to "🧠",
        "load" to "📈",
        "uptime" to "⏱",
        "status" to "●",
        "name" to "●",
        "error" to "✕",
        "warning" to "⚠",
        "temp" to "🌡",
        "temperature" to "🌡",
        "version" to "#",
        "exit" to "⏎",
        "net" to "⇅",
        "network" to "⇅",
        "services" to "❏",
        "service" to "❏",
        "agent" to "▶",
        "host" to "🖥",
        "ping" to "◎",
        "speed" to "▶",
    )

    /**
     * Resolve the display glyph for a kv row.
     *
     * @param key   the row's key string (required)
     * @param icon  the row's explicit icon field (optional; overrides key when non-null)
     */
    fun resolve(key: String, icon: String?): String {
        val lookup = if (icon != null) icon else key
        val normalized = lookup.lowercase(Locale.ROOT)

        // Step 3: exact match
        GLYPH_MAP[normalized]?.let { return it }

        // Step 4: first whitespace-delimited word
        val firstWord = normalized.trim().split(Regex("\\s+")).firstOrNull()
        if (!firstWord.isNullOrEmpty()) {
            GLYPH_MAP[firstWord]?.let { return it }
        }

        // Step 5: fallback
        return FALLBACK
    }
}
