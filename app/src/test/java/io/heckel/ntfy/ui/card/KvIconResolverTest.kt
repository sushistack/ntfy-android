package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.KvIconResolver
import io.heckel.ntfy.ui.message.ParserParityGoldenCorpus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [KvIconResolver] (AC 2, Story 3.0 golden corpus).
 *
 * Covers:
 * - All Story 3.0 golden corpus icon cases (exact match, case normalization, first-word, override, fallback)
 * - Explicit-icon override
 * - Whitespace and blank key fallback
 */
class KvIconResolverTest {

    // -------------------------------------------------------------------------
    // Story 3.0 golden corpus — all iconCases run here (AC 2)
    // -------------------------------------------------------------------------

    @Test fun goldenCorpus_allIconCases() {
        val corpus = ParserParityGoldenCorpus.load()
        for (case in corpus.iconCases) {
            val actual = KvIconResolver.resolve(case.input.key, case.input.icon)
            assertEquals("Case ${case.id}: key=${case.input.key}, icon=${case.input.icon}", case.expected, actual)
        }
    }

    // -------------------------------------------------------------------------
    // Exact matches — spot-check canonical glyphs
    // -------------------------------------------------------------------------

    @Test fun exactMatch_cpu() = assertEquals("⚙", KvIconResolver.resolve("cpu", null))
    @Test fun exactMatch_disk() = assertEquals("💾", KvIconResolver.resolve("disk", null))
    @Test fun exactMatch_memory() = assertEquals("🧠", KvIconResolver.resolve("memory", null))
    @Test fun exactMatch_mem() = assertEquals("🧠", KvIconResolver.resolve("mem", null))
    @Test fun exactMatch_ram() = assertEquals("🧠", KvIconResolver.resolve("ram", null))
    @Test fun exactMatch_load() = assertEquals("📈", KvIconResolver.resolve("load", null))
    @Test fun exactMatch_uptime() = assertEquals("⏱", KvIconResolver.resolve("uptime", null))
    @Test fun exactMatch_status() = assertEquals("●", KvIconResolver.resolve("status", null))
    @Test fun exactMatch_name() = assertEquals("●", KvIconResolver.resolve("name", null))
    @Test fun exactMatch_error() = assertEquals("✕", KvIconResolver.resolve("error", null))
    @Test fun exactMatch_warning() = assertEquals("⚠", KvIconResolver.resolve("warning", null))
    @Test fun exactMatch_temp() = assertEquals("🌡", KvIconResolver.resolve("temp", null))
    @Test fun exactMatch_temperature() = assertEquals("🌡", KvIconResolver.resolve("temperature", null))
    @Test fun exactMatch_version() = assertEquals("#", KvIconResolver.resolve("version", null))
    @Test fun exactMatch_exit() = assertEquals("⏎", KvIconResolver.resolve("exit", null))
    @Test fun exactMatch_net() = assertEquals("⇅", KvIconResolver.resolve("net", null))
    @Test fun exactMatch_network() = assertEquals("⇅", KvIconResolver.resolve("network", null))
    @Test fun exactMatch_services() = assertEquals("❏", KvIconResolver.resolve("services", null))
    @Test fun exactMatch_service() = assertEquals("❏", KvIconResolver.resolve("service", null))
    @Test fun exactMatch_agent() = assertEquals("▶", KvIconResolver.resolve("agent", null))
    @Test fun exactMatch_host() = assertEquals("🖥", KvIconResolver.resolve("host", null))
    @Test fun exactMatch_ping() = assertEquals("◎", KvIconResolver.resolve("ping", null))
    @Test fun exactMatch_speed() = assertEquals("▶", KvIconResolver.resolve("speed", null))

    // -------------------------------------------------------------------------
    // Case normalization
    // -------------------------------------------------------------------------

    @Test fun caseNorm_CPU_uppercase() = assertEquals("⚙", KvIconResolver.resolve("CPU", null))
    @Test fun caseNorm_Disk_mixed() = assertEquals("💾", KvIconResolver.resolve("Disk", null))
    @Test fun caseNorm_MEMORY_uppercase() = assertEquals("🧠", KvIconResolver.resolve("MEMORY", null))
    @Test fun caseNorm_TEMP_uppercase() = assertEquals("🌡", KvIconResolver.resolve("TEMP", null))

    // -------------------------------------------------------------------------
    // First-word match
    // -------------------------------------------------------------------------

    @Test fun firstWord_loadAvg() = assertEquals("📈", KvIconResolver.resolve("Load Avg", null))
    @Test fun firstWord_cpuUsage() = assertEquals("⚙", KvIconResolver.resolve("CPU Usage", null))
    @Test fun firstWord_diskIO() = assertEquals("💾", KvIconResolver.resolve("Disk I/O", null))
    @Test fun firstWord_memoryUsed() = assertEquals("🧠", KvIconResolver.resolve("Memory Used", null))
    @Test fun firstWord_netSpeed() = assertEquals("⇅", KvIconResolver.resolve("Net Speed", null))

    // -------------------------------------------------------------------------
    // Explicit icon field override
    // -------------------------------------------------------------------------

    @Test fun iconOverride_agentOverridesCpu() = assertEquals("▶", KvIconResolver.resolve("cpu", "agent"))
    @Test fun iconOverride_pingOverridesDisk() = assertEquals("◎", KvIconResolver.resolve("disk", "ping"))
    @Test fun iconOverride_unknownIconFallback() = assertEquals("·", KvIconResolver.resolve("cpu", "UNKNOWN_XYZ"))

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    @Test fun fallback_unknownKey() = assertEquals("·", KvIconResolver.resolve("foobar", null))
    @Test fun fallback_blankKey() = assertEquals("·", KvIconResolver.resolve("", null))
    @Test fun fallback_whitespaceKey() = assertEquals("·", KvIconResolver.resolve("   ", null))
    @Test fun fallback_unknownMultiWord() = assertEquals("·", KvIconResolver.resolve("Unknown Thing", null))

    // -------------------------------------------------------------------------
    // Fallback constant
    // -------------------------------------------------------------------------

    @Test fun fallbackConstant_isMiddleDot() = assertEquals("·", KvIconResolver.FALLBACK)
}
