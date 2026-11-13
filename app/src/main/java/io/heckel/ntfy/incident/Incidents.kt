package io.heckel.ntfy.incident

import io.heckel.ntfy.db.Notification
import java.time.Instant
import java.time.ZoneId

/**
 * Groups raw ntfy notifications into "incidents" for the board and history screens.
 *
 * Kotlin port of ntfy-web src/app/incidents.js. Both run the same fixtures
 * (app/src/test/resources/incidents.fixtures.json = ntfy-web src/app/incidents.fixtures.json),
 * so change the web module and the fixtures first, then mirror here.
 *
 * ntfy has no alert lifecycle, so one is inferred from what senders do:
 * - ops-alerter re-publishes every 15 min while a condition holds and goes silent when it clears.
 * - Uptime Kuma sends "<name> DOWN" then "<name> UP".
 * - Everything else is one-off.
 * ponytail: grouping is topic + normalized title (same as web); an explicit `key:` tag from senders is the upgrade path.
 */
object Incidents {
    const val SLOT_SECONDS = 15 * 60L // one ops-alerter poll
    const val SLOTS_PER_DAY = 96
    const val QUIET_AFTER_SECONDS = 35 * 60L // two missed polls + slack
    const val RUN_GAP_SECONDS = 60 * 60L // a longer silence starts a new run ("since" resets)

    // JS `\s` (Unicode spaces + line terminators + BOM) spelled out: Java's `\s` is ASCII-only,
    // and (?U) would also widen `\d`, which JS keeps ASCII-only.
    private const val WS = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"
    // `\z`, not `$`: Java's `$` also matches before a trailing line break, JS's does not.
    private val RECOVERY = Regex("$WS+up$WS*\\z", RegexOption.IGNORE_CASE)
    private val FAILURE = Regex("$WS+down$WS*\\z", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("[0-9]+([.,][0-9]+)?")
    private val WS_RUN = Regex("$WS+")
    private val TRIM = Regex("^$WS+|$WS+\\z")

    private fun jsTrim(s: String) = s.replace(TRIM, "")

    /** Title, else first message line; trimmed and capped at 120 UTF-16 units (JS `slice(0, 120)`). */
    fun titleOf(m: Message): String {
        val raw = m.title.ifEmpty { m.message.split("\n")[0] } // JS `||`: only "" falls back, "   " does not
        return jsTrim(raw).take(120)
    }

    fun isRecovery(m: Message) = RECOVERY.containsMatchIn(titleOf(m))

    /** Stable per-topic grouping key: drop Kuma's UP/DOWN suffix and any numbers (82% → #%). */
    fun normalizeTitle(title: String): String =
        jsTrim(
            title.replace(RECOVERY, "").replace(FAILURE, "")
                .lowercase()
                .replace(NUMBER, "#")
                .replace(WS_RUN, " ")
        )

    fun incidentKey(m: Message) = "${m.subscriptionId}|${normalizeTitle(titleOf(m))}"

    /** Short URL-safe id for a key (FNV-1a 32-bit over UTF-16 units, base36) — same ids as web URLs. */
    fun incidentId(key: String): String {
        var h = 0x811c9dc5.toInt()
        for (c in key) h = (h xor c.code) * 0x01000193
        return (h.toLong() and 0xffffffffL).toString(36)
    }

    /** Earliest firing of the latest run: walk back while gaps stay within RUN_GAP_SECONDS. */
    private fun runStart(firings: List<Long>): Long? {
        if (firings.isEmpty()) return null
        var i = firings.size - 1
        while (i > 0 && firings[i] - firings[i - 1] <= RUN_GAP_SECONDS) i--
        return firings[i]
    }

    /** Groups [items] (any order) into incidents, newest first. [message] adapts an item to the fields grouping needs. */
    fun <T> group(items: List<T>, now: Long, message: (T) -> Message): List<Incident<T>> {
        val groups = LinkedHashMap<String, MutableList<Pair<T, Message>>>()
        for (item in items) {
            val m = message(item)
            groups.getOrPut(incidentKey(m)) { mutableListOf() }.add(item to m)
        }
        val incidents = groups.map { (key, entries) ->
            // Whole-second times: on a tie a recovery is the later message (UP follows DOWN). Stable, like JS sort.
            val list = entries.sortedWith(compareByDescending<Pair<T, Message>> { it.second.time }.thenByDescending { isRecovery(it.second) })
            val latest = list[0]
            val failures = list.filter { !isRecovery(it.second) }
            val firings = failures.map { it.second.time }.sorted()
            val priority = list.maxOf { it.second.priority }
            val hasPair = list.any { isRecovery(it.second) } || list.any { FAILURE.containsMatchIn(titleOf(it.second)) }
            val state = when {
                isRecovery(latest.second) -> State.RESOLVED
                priority <= 3 && !hasPair -> State.INFO
                now - latest.second.time <= QUIET_AFTER_SECONDS -> State.OPEN
                else -> State.QUIET
            }
            val display = failures.firstOrNull() ?: latest // show "X DOWN", not "X UP"
            Incident(
                key = key,
                id = incidentId(key),
                subscriptionId = latest.second.subscriptionId,
                title = titleOf(display.second),
                state = state,
                firings = firings,
                first = firings.firstOrNull() ?: latest.second.time,
                since = runStart(firings) ?: latest.second.time,
                last = firings.lastOrNull() ?: latest.second.time,
                resolvedAt = if (state == State.RESOLVED) latest.second.time else null,
                priority = priority,
                unread = list.count { it.second.unread },
                latest = display.first,
                latestTime = latest.second.time,
                notifications = list.map { it.first },
            )
        }
        return incidents.sortedByDescending { it.latestTime }
    }

    /** Board/history entry point for Room notifications. Unread = popup not yet cleared (notificationId != 0), as in the official app's newCount. */
    fun group(notifications: List<Notification>, now: Long): List<Incident<Notification>> =
        group(notifications, now) { it.toMessage() }

    fun Notification.toMessage() = Message(
        id = id,
        subscriptionId = subscriptionId.toString(),
        time = timestamp,
        title = title,
        message = message,
        priority = priority,
        unread = notificationId != 0,
    )

    /** Local-midnight unix seconds for the day containing [t], shifted by [dayOffset] days. */
    fun dayStart(t: Long, dayOffset: Int = 0, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochSecond(t).atZone(zone).toLocalDate().plusDays(dayOffset.toLong()).atStartOfDay(zone).toEpochSecond()

    /** Slot indices (0..95) within the day starting at [start] that contain a firing. */
    fun slotsForDay(times: List<Long>, start: Long): Set<Int> {
        val end = start + SLOTS_PER_DAY * SLOT_SECONDS
        return times.filter { it in start until end }.map { ((it - start) / SLOT_SECONDS).toInt() }.toSet()
    }

    fun slotOf(t: Long, start: Long): Int = Math.floorDiv(t - start, SLOT_SECONDS).toInt()

    /** The board's "N times today" — counts the same firings the day strip draws. */
    fun sentToday(incident: Incident<*>, today: Long) = incident.firings.count { it >= today }

    /** The fields grouping needs; [subscriptionId] is a string so web and Android keys/ids match. */
    data class Message(
        val id: String,
        val subscriptionId: String,
        val time: Long,
        val title: String,
        val message: String,
        val priority: Int,
        val unread: Boolean,
    )

    enum class State { OPEN, QUIET, RESOLVED, INFO }

    data class Incident<T>(
        val key: String,
        val id: String,
        val subscriptionId: String,
        val title: String,
        val state: State,
        val firings: List<Long>, // ascending
        val first: Long,
        val since: Long, // start of the current unbroken run
        val last: Long,
        val resolvedAt: Long?,
        val priority: Int,
        val unread: Int,
        val latest: T,
        val latestTime: Long,
        val notifications: List<T>, // newest first
    )
}
