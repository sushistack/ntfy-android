package io.heckel.ntfy.incident

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.heckel.ntfy.incident.Incidents.Message
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * Runs the shared parity fixtures: src/test/resources/incidents.fixtures.json is a byte-for-byte copy of
 * ntfy-web src/app/incidents.fixtures.json (web runs it in incidents.fixtures.test.js). Never edit the copy here;
 * update it in ntfy-web and copy it over, e.g.
 *   gh api repos/sushistack/ntfy-web/contents/src/app/incidents.fixtures.json -H "Accept: application/vnd.github.raw" > app/src/test/resources/incidents.fixtures.json
 */
class IncidentsFixtureTest {
    private val fixtures: JsonObject =
        JsonParser.parseString(javaClass.classLoader!!.getResource("incidents.fixtures.json")!!.readText()).asJsonObject

    @Test
    fun constantsMatch() {
        val c = fixtures.getAsJsonObject("constants")
        assertEquals(c["SLOT_SECONDS"].asLong, Incidents.SLOT_SECONDS)
        assertEquals(c["SLOTS_PER_DAY"].asInt, Incidents.SLOTS_PER_DAY)
        assertEquals(c["QUIET_AFTER_SECONDS"].asLong, Incidents.QUIET_AFTER_SECONDS)
        assertEquals(c["RUN_GAP_SECONDS"].asLong, Incidents.RUN_GAP_SECONDS)
    }

    @Test
    fun normalizeTitle() {
        for (e in fixtures.getAsJsonArray("normalizeTitle").map { it.asJsonObject }) {
            assertEquals(e["in"].asString, e["out"].asString, Incidents.normalizeTitle(e["in"].asString))
        }
    }

    @Test
    fun incidentId() {
        for (e in fixtures.getAsJsonArray("incidentId").map { it.asJsonObject }) {
            assertEquals(e["key"].asString, e["id"].asString, Incidents.incidentId(e["key"].asString))
        }
    }

    @Test
    fun cases() {
        val cases = fixtures.getAsJsonArray("cases").map { it.asJsonObject }
        assert(cases.isNotEmpty())
        for (c in cases) {
            val now = c["now"].asLong
            val today = c["today"].asLong
            val messages = c.getAsJsonArray("notifications").map { it.asJsonObject }.map { n ->
                Message(
                    id = n["id"].asString,
                    subscriptionId = n["subscriptionId"].asString,
                    time = n["time"].asLong,
                    title = n["title"].asString,
                    message = n["message"].asString,
                    priority = n["priority"].asInt,
                    unread = n["new"].asInt == 1,
                )
            }
            val actual = Incidents.group(messages, now) { it }.map { view(it, today) }
            val expected = c.getAsJsonArray("expected").map { it.asJsonObject }
            assertEquals(c["name"].asString, expected.map { it.toString() }, actual.map { it.toString() })
        }
    }

    @Test
    fun dayStartUsesLocalMidnight() {
        val seoul = ZoneId.of("Asia/Seoul") // 2027-01-15 08:00 UTC = 17:00 KST → midnight KST = 2027-01-14 15:00 UTC
        assertEquals(1_799_938_800L, Incidents.dayStart(1_800_000_000L, zone = seoul))
        assertEquals(1_799_938_800L - 6 * 86_400, Incidents.dayStart(1_800_000_000L, -6, seoul))
    }

    // Same shape and key order as the fixture's `expected` entries (and the web test's `view`).
    private fun view(inc: Incidents.Incident<Message>, today: Long) = JsonObject().apply {
        addProperty("id", inc.id)
        addProperty("key", inc.key)
        addProperty("subscriptionId", inc.subscriptionId)
        addProperty("title", inc.title)
        addProperty("state", inc.state.name.lowercase())
        add("firings", longs(inc.firings))
        addProperty("first", inc.first)
        addProperty("since", inc.since)
        addProperty("last", inc.last)
        if (inc.resolvedAt == null) add("resolvedAt", com.google.gson.JsonNull.INSTANCE) else addProperty("resolvedAt", inc.resolvedAt)
        addProperty("priority", inc.priority)
        addProperty("unread", inc.unread)
        addProperty("latestId", inc.latest.id)
        add("notificationIds", com.google.gson.JsonArray().apply { inc.notifications.forEach { add(it.id) } })
        add("todaySlots", longs(Incidents.slotsForDay(inc.firings, today).sorted().map { it.toLong() }))
        addProperty("sentToday", Incidents.sentToday(inc, today))
    }

    private fun longs(values: List<Long>) = com.google.gson.JsonArray().apply { values.forEach { add(it) } }
}
