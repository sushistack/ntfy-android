package io.heckel.ntfy.util

import java.net.URI

/**
 * Destination policy for card markdown rendering.
 *
 * Parses via [URI] (not substring/prefix matching) and compares schemes
 * locale-independently and case-insensitively.
 *
 * Link allowlist:  http, https, mailto
 * Image allowlist: http, https
 *
 * Missing, relative, protocol-relative, and all other schemes are rejected.
 * Markwon 4.3+ defaults schemeless links to HTTPS — this policy intentionally
 * requires an explicit allowed scheme, so it must be enforced before Markwon's
 * default resolver runs.
 */
internal object MarkwonLinkPolicy {

    private val LINK_ALLOWED = setOf("http", "https", "mailto")
    private val IMAGE_ALLOWED = setOf("http", "https")

    fun isLinkAllowed(destination: String): Boolean = schemeOf(destination) in LINK_ALLOWED

    fun isImageAllowed(destination: String): Boolean = schemeOf(destination) in IMAGE_ALLOWED

    private fun schemeOf(destination: String): String? {
        if (destination.isBlank()) return null
        return try {
            URI(destination).scheme?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
