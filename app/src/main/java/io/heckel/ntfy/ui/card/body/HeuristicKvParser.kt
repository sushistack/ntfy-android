package io.heckel.ntfy.ui.card.body

import java.util.Locale

/**
 * Pure heuristic parser for untagged `key: value` message bodies (Story 3.8).
 *
 * No Android dependencies. No JSON. No side effects.
 */
object HeuristicKvParser {

    private val KV_LINE_REGEX = Regex("""^[^:]+:\s*.*$""")

    // The entire trimmed value must be a number optionally followed by '%'.
    // Intentionally does NOT match values with non-numeric content (e.g. "22 hours",
    // "0.11 0.12 0.18", "0.18.7"). The group captures the numeric part without the '%'.
    private val METER_REGEX = Regex("""^(\d+(?:\.\d+)?)\s*%?$""")

    private val STATUS_ERROR_REGEX = Regex("error|fail|err")

    /**
     * Returns [BodyShape.HeuristicKv] when every non-empty line in [decoded] matches
     * the `key: value` pattern and there are at least 2 non-empty lines.
     * Returns [BodyShape.Paragraph] otherwise (including empty bodies and single-line bodies).
     */
    fun detectBodyShape(decoded: String): BodyShape {
        val normalized = decoded.replace("\r\n", "\n").replace("\r", "\n")
        val nonEmptyLines = normalized.lines().filter { it.isNotBlank() }
        if (nonEmptyLines.size <= 1) return BodyShape.Paragraph
        return if (nonEmptyLines.all { it.matches(KV_LINE_REGEX) }) BodyShape.HeuristicKv
        else BodyShape.Paragraph
    }

    /**
     * Converts every non-empty line of [decoded] into a [KvRow] and returns a [KvSpec].
     *
     * Split is on the FIRST `:` only. Key and value are trimmed.
     * Meter and status rules are composable (both can apply to the same row).
     * Columns is always 1 (mobile UX-DR5). Icon is always null (resolved by key in KvBlockRenderer).
     */
    fun parseHeuristicKvSpec(decoded: String): KvSpec {
        val normalized = decoded.replace("\r\n", "\n").replace("\r", "\n")
        val rows = normalized.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val colonIdx = line.indexOf(':')
                if (colonIdx < 0) return@mapNotNull null
                val key = line.substring(0, colonIdx).trim()
                if (key.isEmpty()) return@mapNotNull null
                val rawValue = line.substring(colonIdx + 1).trim()

                val meterMatch = METER_REGEX.find(rawValue)
                val meter: Double? = meterMatch?.groupValues?.get(1)?.toDoubleOrNull()

                val status: String? =
                    if (key.lowercase(Locale.ROOT).contains(STATUS_ERROR_REGEX)) "error" else null

                KvRow(key = key, value = rawValue, meter = meter, status = status, icon = null)
            }
        return KvSpec(columns = 1, rows = rows)
    }
}

/** Body shape result from [HeuristicKvParser.detectBodyShape]. */
enum class BodyShape { Paragraph, HeuristicKv }
