package io.heckel.ntfy.ui.card.body

import java.util.Locale

/**
 * Line icon for a kv row, same table and lookup as ntfy-web StructuredCard KvIcon:
 * `icon ?: key`, lowercased, exact match first, then the first word. Returns 24×24 SVG path data
 * (2px round stroke), or null for the neutral dot.
 */
object KvIconResolver {
    private val P = mapOf(
        "cpu" to "M9 3v2M15 3v2M9 19v2M15 19v2M3 9h2M3 15h2M19 9h2M19 15h2M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM9 9h6v6H9z",
        "disk" to "M4 7c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3Zm0 0v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3",
        "memory" to "M6 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2ZM8 7v10M12 7v10M16 7v10",
        "load" to "M4 19h16M6 15l4-4 3 3 5-6",
        "uptime" to "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z",
        "status" to "M22 12h-4l-3 8L9 4l-3 8H2",
        "error" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM15 9l-6 6M9 9l6 6",
        "warning" to "M10.3 4 2.6 18a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 4a2 2 0 0 0-3.4 0ZM12 9v4M12 17h.01",
        "temp" to "M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0Z",
        "ping" to "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01",
        "speed" to "M12 14l4-4M3.5 17a9 9 0 1 1 17 0",
        "version" to "M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8ZM7.5 7.5h.01",
        "exit" to "M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4 2.5-2.5Z",
        "net" to "M7 17V4M3 8l4-4 4 4M17 7v13M21 16l-4 4-4-4",
        "service" to "M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3H4V6ZM4 13h16v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-5ZM8 6.5h.01M8 16.5h.01",
        "agent" to "M12 8V4H8M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2ZM9 13v1M15 13v1",
        "host" to "M4 5h16v11H4zM8 20h8M12 16v4",
        "name" to "M4 7h16M4 12h10M4 17h7",
        "path" to "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z",
    )
    private val ICONS = P + mapOf(
        "mem" to P.getValue("memory"), "ram" to P.getValue("memory"), "temperature" to P.getValue("temp"),
        "network" to P.getValue("net"), "services" to P.getValue("service"), "fix" to P.getValue("exit"),
        "newest" to P.getValue("uptime"), "age" to P.getValue("uptime"), "monitor" to P.getValue("status"),
        "url" to P.getValue("ping"), "target" to P.getValue("ping"),
    )

    fun resolve(key: String, icon: String?): String? {
        val k = (icon ?: key).lowercase(Locale.ROOT)
        return ICONS[k] ?: ICONS[k.split(Regex("\\s+"))[0]]
    }

    /** ntfy-web looksLikePath: no spaces, contains / or :, longer than 12 → shown as a code chip. */
    fun looksLikePath(v: String): Boolean = v.none { it.isWhitespace() } && (v.contains('/') || v.contains(':')) && v.length > 12
}
