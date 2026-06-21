package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.BodyShape
import io.heckel.ntfy.ui.card.body.HeuristicKvParser
import io.heckel.ntfy.ui.message.ParserParityGoldenCorpus
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM tests for [HeuristicKvParser] — shape detection and line parsing (AC 1–11).
 *
 * Shape detection tests consume the Story 3.0 [ParserParityGoldenCorpus] `shapeCases` group
 * (AC 7). No shape-detection expected values are duplicated outside the shared fixture.
 *
 * Run:
 *   ./gradlew testPlayDebugUnitTest --tests '*HeuristicKvParserTest'
 *   ./gradlew testFdroidDebugUnitTest --tests '*HeuristicKvParserTest'
 */
class HeuristicKvParserTest {

    // -------------------------------------------------------------------------
    // Shape detection: golden corpus (AC 7 — no duplication of expected values)
    // -------------------------------------------------------------------------

    @Test
    fun `all shapeCases from golden corpus produce correct BodyShape`() {
        val corpus = ParserParityGoldenCorpus.load()
        val failures = mutableListOf<String>()

        for (case in corpus.shapeCases) {
            val actual = HeuristicKvParser.detectBodyShape(case.input.body)
            val expectedShape = when (case.expected) {
                ParserParityGoldenCorpus.ShapeResult.PARAGRAPH -> BodyShape.Paragraph
                ParserParityGoldenCorpus.ShapeResult.HEURISTIC_KV -> BodyShape.HeuristicKv
            }
            if (actual != expectedShape) {
                failures += "[${case.id}] body=${case.input.body.take(40).replace('\n','↵')} " +
                        "expected=$expectedShape actual=$actual"
            }
        }

        assertTrue("Shape corpus failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Shape detection: explicit edge matrix (AC 4–6)
    // -------------------------------------------------------------------------

    @Test
    fun `empty body is paragraph`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape(""))
    }

    @Test
    fun `body of only blank lines is paragraph`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape("   \n\n  \n"))
    }

    @Test
    fun `single non-empty line without colon is paragraph`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape("Hello world"))
    }

    @Test
    fun `single non-empty line with colon is paragraph not heuristic-kv (AC 4)`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape("CPU: 50%"))
    }

    @Test
    fun `two lines both kv-shaped is heuristic-kv`() {
        assertEquals(BodyShape.HeuristicKv, HeuristicKvParser.detectBodyShape("CPU: 50%\nMemory: 30%"))
    }

    @Test
    fun `blank lines between valid kv lines are ignored (AC 5)`() {
        assertEquals(BodyShape.HeuristicKv, HeuristicKvParser.detectBodyShape("CPU: 50%\n\nMemory: 30%"))
    }

    @Test
    fun `mixed lines one non-matching is paragraph (AC 5)`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape("CPU: 50%\nThis is a sentence"))
    }

    @Test
    fun `line starting with colon empty key is paragraph`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape(": no key here\nMemory: 30%"))
    }

    @Test
    fun `value containing extra colon is kv-matching (URL case)`() {
        // "URL: https://example.com" — key has no colon, so it matches
        assertEquals(BodyShape.HeuristicKv, HeuristicKvParser.detectBodyShape("URL: https://example.com\nStatus: ok"))
    }

    @Test
    fun `kv empty value is kv-matching`() {
        // "CPU:" — key exists, value is empty string after trim
        assertEquals(BodyShape.HeuristicKv, HeuristicKvParser.detectBodyShape("CPU:\nMemory: 30%"))
    }

    @Test
    fun `three kv lines with mixed content is heuristic-kv`() {
        val body = "CPU: 78%\nMemory: 45%\nDisk: 95%"
        assertEquals(BodyShape.HeuristicKv, HeuristicKvParser.detectBodyShape(body))
    }

    @Test
    fun `multiline plain text is paragraph`() {
        assertEquals(BodyShape.Paragraph, HeuristicKvParser.detectBodyShape("Line one\nLine two\nLine three"))
    }

    // -------------------------------------------------------------------------
    // Line parser: per-row meter and status (AC 2, 3)
    // -------------------------------------------------------------------------

    @Test
    fun `CPU 78 percent - key value meter status`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("CPU: 78%\nDisk: 17%")
        val row = spec.rows.first()
        assertEquals("CPU", row.key)
        assertEquals("78%", row.value)
        assertEquals(78.0, row.meter!!, 0.001)
        assertNull(row.status)
    }

    @Test
    fun `Memory 45 bare integer - meter populated`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Memory: 45\nDisk: 17")
        val row = spec.rows.first()
        assertEquals("Memory", row.key)
        assertEquals("45", row.value)
        assertEquals(45.0, row.meter!!, 0.001)
        assertNull(row.status)
    }

    @Test
    fun `trailing decimal value - meter populated`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Load: 1.5\nStatus: ok")
        val row = spec.rows.first()
        assertEquals(1.5, row.meter!!, 0.001)
    }

    @Test
    fun `Error count 3 - meter and status error`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Error count: 3\nStatus: ok")
        val row = spec.rows.first()
        assertEquals("Error count", row.key)
        assertEquals("3", row.value)
        assertEquals(3.0, row.meter!!, 0.001)
        assertEquals("error", row.status)
    }

    @Test
    fun `Failure rate 95 percent - meter and status error`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Failure rate: 95%\nStatus: ok")
        val row = spec.rows.first()
        assertEquals("Failure rate", row.key)
        assertEquals("95%", row.value)
        assertEquals(95.0, row.meter!!, 0.001)
        assertEquals("error", row.status)
    }

    @Test
    fun `Uptime 22 hours - no meter mid-string number`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Uptime: 22 hours\nStatus: ok")
        val row = spec.rows.first()
        assertEquals("Uptime", row.key)
        assertEquals("22 hours", row.value)
        assertNull("mid-string number must not produce meter", row.meter)
        assertNull(row.status)
    }

    @Test
    fun `Status running - no meter no status`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Status: running\nCPU: 50%")
        val row = spec.rows.first()
        assertEquals("Status", row.key)
        assertEquals("running", row.value)
        assertNull(row.meter)
        assertNull(row.status)
    }

    @Test
    fun `Load Avg 0_11 0_12 0_18 - no meter three numbers mid-string`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Load Avg: 0.11 0.12 0.18\nCPU: 50%")
        val row = spec.rows.first()
        assertEquals("Load Avg", row.key)
        assertEquals("0.11 0.12 0.18", row.value)
        assertNull("space-separated numbers must not produce meter", row.meter)
    }

    @Test
    fun `URL with colon in value splits on first colon only`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("URL: https://example.com\nStatus: ok")
        val row = spec.rows.first()
        assertEquals("URL", row.key)
        assertEquals("https://example.com", row.value)
        assertNull(row.meter)
        assertNull(row.status)
    }

    @Test
    fun `key matching err substring gets status error`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Stderr: 0\nStatus: ok")
        val row = spec.rows.first()
        assertEquals("error", row.status)
    }

    @Test
    fun `key matching fail gets status error`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("fail count: 2\nStatus: ok")
        val row = spec.rows.first()
        assertEquals("error", row.status)
    }

    @Test
    fun `column count is always 1`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("CPU: 78%\nMemory: 45%")
        assertEquals(1, spec.columns)
    }

    @Test
    fun `icon is always null in heuristic rows`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("CPU: 78%\nMemory: 45%")
        spec.rows.forEach { row ->
            assertNull("heuristic rows must have null icon, got non-null for key=${row.key}", row.icon)
        }
    }

    @Test
    fun `blank lines between kv pairs are excluded from rows`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("CPU: 78%\n\nMemory: 45%")
        assertEquals(2, spec.rows.size)
        assertEquals("CPU", spec.rows[0].key)
        assertEquals("Memory", spec.rows[1].key)
    }

    @Test
    fun `empty value after colon produces empty string value`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("CPU:\nMemory: 45%")
        val row = spec.rows.first()
        assertEquals("CPU", row.key)
        assertEquals("", row.value)
        assertNull(row.meter)
    }

    @Test
    fun `key and value are trimmed of leading trailing whitespace`() {
        val spec = HeuristicKvParser.parseHeuristicKvSpec("  CPU  :  78%  \nMemory: 45%")
        val row = spec.rows.first()
        assertEquals("CPU", row.key)
        assertEquals("78%", row.value)
    }

    @Test
    fun `meter and status are composable on same row (AC 3)`() {
        // error key + trailing number → both meter and status set
        val spec = HeuristicKvParser.parseHeuristicKvSpec("Error rate: 42%\nStatus: ok")
        val row = spec.rows.first()
        assertEquals(42.0, row.meter!!, 0.001)
        assertEquals("error", row.status)
    }

    @Test
    fun `canonical monitoring example renders three rows with meters`() {
        val body = "CPU: 78%\nMemory: 45%\nDisk: 95%"
        val spec = HeuristicKvParser.parseHeuristicKvSpec(body)
        assertEquals(3, spec.rows.size)
        assertEquals(78.0, spec.rows[0].meter!!, 0.001)
        assertEquals(45.0, spec.rows[1].meter!!, 0.001)
        assertEquals(95.0, spec.rows[2].meter!!, 0.001)
    }
}
