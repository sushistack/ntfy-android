package io.heckel.ntfy.ui.message

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * Corpus integrity test (AC 8, 9, 12).
 *
 * Validates that parser-parity-golden.json is well-formed, complete, and consistent.
 * This test does NOT implement any production parser logic — it only checks the fixture
 * itself is a valid, non-self-fulfilling contract for later Epic 3 consumer tests.
 *
 * Run:
 *   ./gradlew testPlayDebugUnitTest --tests '*ParserParityGoldenCorpusTest'
 *   ./gradlew testFdroidDebugUnitTest --tests '*ParserParityGoldenCorpusTest'
 */
class ParserParityGoldenCorpusTest {

    companion object {
        private lateinit var corpus: ParserParityGoldenCorpus.Corpus

        // All canonical icon aliases from message-format.md §4 icon map.
        private val REQUIRED_ICON_ALIASES = setOf(
            "cpu", "disk", "memory", "mem", "ram",
            "load", "uptime", "status", "name",
            "error", "warning", "temp", "temperature",
            "version", "exit", "net", "network",
            "services", "service", "agent", "host", "ping", "speed"
        )

        // Required glyphs — assert UTF-8 code-point preservation (AC 3 / testing §).
        private val REQUIRED_GLYPHS = setOf(
            "⚙", "💾", "🧠", "📈", "⏱", "●", "✕", "⚠", "🌡",
            "#", "⏎", "⇅", "❏", "▶", "🖥", "◎", "·"
        )

        // Required meter boundary values from AC 4.
        private val REQUIRED_METER_VALUES = setOf(64.0, 65.0, 89.0, 90.0, 91.0)

        // Required card gate IDs: at minimum one pass per type + key failure modes.
        private val REQUIRED_GATE_IDS = setOf(
            "gate-pass-kv", "gate-pass-list", "gate-pass-chart", "gate-pass-sections",
            "gate-pass-extra-tags",
            "gate-fail-no-tag", "gate-fail-wrong-tag", "gate-fail-uppercase-tag",
            "gate-fail-invalid-json", "gate-fail-json-array", "gate-fail-missing-type",
            "gate-fail-unknown-type", "gate-fail-markdown-toplevel",
            "gate-fail-case-mismatch-type"
        )

        // Required markdown link IDs: http, https, mailto live; javascript, data inert.
        private val REQUIRED_LINK_IDS = setOf(
            "md-link-http-live", "md-link-https-live", "md-link-mailto-live",
            "md-link-javascript-inert", "md-link-data-inert"
        )

        // Required markdown image IDs: http, https render; data, file, relative drop.
        private val REQUIRED_IMAGE_IDS = setOf(
            "md-img-http-render", "md-img-https-render",
            "md-img-data-drop", "md-img-file-drop", "md-img-relative-drop",
            "md-img-mailto-drop"
        )

        // Required shape IDs: paragraph and heuristic-kv boundaries.
        private val REQUIRED_SHAPE_IDS = setOf(
            "shape-empty-body", "shape-single-line-plain", "shape-single-line-colon",
            "shape-kv-two-lines", "shape-kv-with-blank-lines"
        )

        @BeforeClass
        @JvmStatic
        fun loadCorpus() {
            corpus = ParserParityGoldenCorpus.load()
        }
    }

    // -------------------------------------------------------------------------
    // Schema version
    // -------------------------------------------------------------------------

    @Test
    fun `schema version is 1`() {
        assertEquals("schemaVersion must be 1", 1, corpus.schemaVersion)
    }

    // -------------------------------------------------------------------------
    // All groups are non-empty
    // -------------------------------------------------------------------------

    @Test
    fun `iconCases group is non-empty`() {
        assertTrue("iconCases must not be empty", corpus.iconCases.isNotEmpty())
    }

    @Test
    fun `meterCases group is non-empty`() {
        assertTrue("meterCases must not be empty", corpus.meterCases.isNotEmpty())
    }

    @Test
    fun `cardGateCases group is non-empty`() {
        assertTrue("cardGateCases must not be empty", corpus.cardGateCases.isNotEmpty())
    }

    @Test
    fun `markdownDestinationCases group is non-empty`() {
        assertTrue("markdownDestinationCases must not be empty", corpus.markdownDestinationCases.isNotEmpty())
    }

    @Test
    fun `shapeCases group is non-empty`() {
        assertTrue("shapeCases must not be empty", corpus.shapeCases.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Case IDs are non-blank and unique within each group
    // -------------------------------------------------------------------------

    @Test
    fun `icon case IDs are non-blank`() {
        corpus.iconCases.forEach { c ->
            assertTrue("iconCases: blank id found", c.id.isNotBlank())
        }
    }

    @Test
    fun `icon case IDs are unique`() {
        val ids = corpus.iconCases.map { it.id }
        assertEquals("iconCases: duplicate IDs: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
    }

    @Test
    fun `meter case IDs are non-blank and unique`() {
        val ids = corpus.meterCases.map { it.id }
        corpus.meterCases.forEach { c -> assertTrue("meterCases: blank id found", c.id.isNotBlank()) }
        assertEquals("meterCases: duplicate IDs: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
    }

    @Test
    fun `card gate case IDs are non-blank and unique`() {
        val ids = corpus.cardGateCases.map { it.id }
        corpus.cardGateCases.forEach { c -> assertTrue("cardGateCases: blank id found", c.id.isNotBlank()) }
        assertEquals("cardGateCases: duplicate IDs: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
    }

    @Test
    fun `markdown destination case IDs are non-blank and unique`() {
        val ids = corpus.markdownDestinationCases.map { it.id }
        corpus.markdownDestinationCases.forEach { c -> assertTrue("markdownDestinationCases: blank id found", c.id.isNotBlank()) }
        assertEquals("markdownDestinationCases: duplicate IDs: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
    }

    @Test
    fun `shape case IDs are non-blank and unique`() {
        val ids = corpus.shapeCases.map { it.id }
        corpus.shapeCases.forEach { c -> assertTrue("shapeCases: blank id found", c.id.isNotBlank()) }
        assertEquals("shapeCases: duplicate IDs: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
    }

    // -------------------------------------------------------------------------
    // Icon: every canonical alias appears exactly once as an exact lookup input
    // -------------------------------------------------------------------------

    @Test
    fun `every canonical icon alias appears at least once as exact-lookup input`() {
        val exactInputs = corpus.iconCases
            .filter { it.input.icon == null }
            .map { it.input.key.lowercase() }
            .toSet()
        val missing = REQUIRED_ICON_ALIASES - exactInputs
        assertTrue("Missing canonical icon aliases in iconCases: $missing", missing.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Icon: required glyphs are preserved as UTF-8 strings
    // -------------------------------------------------------------------------

    @Test
    fun `all required glyphs appear as expected values in iconCases`() {
        val presentGlyphs = corpus.iconCases.map { it.expected }.toSet()
        val missing = REQUIRED_GLYPHS - presentGlyphs
        assertTrue("Missing required glyphs in iconCases.expected: $missing", missing.isEmpty())
    }

    @Test
    fun `middle-dot fallback glyph is U+00B7`() {
        val fallbackCase = corpus.iconCases.firstOrNull { it.id == "icon-fallback-unknown-key" }
        assertNotNull("Expected case 'icon-fallback-unknown-key' to exist", fallbackCase)
        val glyph = fallbackCase!!.expected
        assertEquals("Fallback glyph must be middle dot U+00B7 (·)", '·', glyph.single())
    }

    @Test
    fun `cpu glyph is U+2699`() {
        val case = corpus.iconCases.firstOrNull { it.id == "icon-exact-cpu" }
        assertNotNull("Expected case 'icon-exact-cpu'", case)
        assertEquals("cpu glyph must be ⚙ U+2699", "⚙", case!!.expected)
    }

    @Test
    fun `disk glyph is U+1F4BE`() {
        val case = corpus.iconCases.firstOrNull { it.id == "icon-exact-disk" }
        assertNotNull("Expected case 'icon-exact-disk'", case)
        assertEquals("disk glyph must be 💾 U+1F4BE", "💾", case!!.expected)
    }

    @Test
    fun `memory glyph is U+1F9E0`() {
        val case = corpus.iconCases.firstOrNull { it.id == "icon-exact-memory" }
        assertNotNull("Expected case 'icon-exact-memory'", case)
        assertEquals("memory glyph must be 🧠 U+1F9E0", "🧠", case!!.expected)
    }

    // -------------------------------------------------------------------------
    // Icon: override and first-word cases present
    // -------------------------------------------------------------------------

    @Test
    fun `icon override case exists and icon field wins over key field`() {
        val overrideCase = corpus.iconCases.firstOrNull { it.id == "icon-override-icon-field" }
        assertNotNull("Expected case 'icon-override-icon-field' to exist", overrideCase)
        assertNotNull("Override case must have non-null icon field", overrideCase!!.input.icon)
        assertEquals("icon=agent must produce ▶, not the cpu key glyph ⚙", "▶", overrideCase.expected)
    }

    @Test
    fun `first-word fallback case for Load Avg exists`() {
        val case = corpus.iconCases.firstOrNull { it.id == "icon-first-word-load-avg" }
        assertNotNull("Expected case 'icon-first-word-load-avg'", case)
        assertEquals("Load Avg first-word must resolve to 📈", "📈", case!!.expected)
    }

    @Test
    fun `multi-word key with no exact match uses first-word fallback`() {
        val case = corpus.iconCases.firstOrNull { it.id == "icon-first-word-status-name" }
        assertNotNull("Expected adversarial case 'icon-first-word-status-name'", case)
        assertEquals("'status name' has no exact match so first-word 'status' must resolve to ●",
            "●", case!!.expected)
    }

    // -------------------------------------------------------------------------
    // Meter: all five required boundary values are present
    // -------------------------------------------------------------------------

    @Test
    fun `all five required meter boundary values are present`() {
        val presentValues = corpus.meterCases.map { it.input.value }.toSet()
        val missing = REQUIRED_METER_VALUES - presentValues
        assertTrue("Missing required meter boundary values: $missing", missing.isEmpty())
    }

    @Test
    fun `meter boundary semantics match AC 4`() {
        val byValue = corpus.meterCases.associateBy { it.input.value }
        listOf(64.0, 65.0, 89.0, 90.0, 91.0).forEach { v ->
            assertNotNull("meter boundary case for value $v is missing from fixture", byValue[v])
        }
        assertEquals("[64] must be ok",       ParserParityGoldenCorpus.MeterClass.OK,       byValue[64.0]!!.expected)
        assertEquals("[65] must be warning",  ParserParityGoldenCorpus.MeterClass.WARNING,  byValue[65.0]!!.expected)
        assertEquals("[89] must be warning",  ParserParityGoldenCorpus.MeterClass.WARNING,  byValue[89.0]!!.expected)
        assertEquals("[90] must be critical", ParserParityGoldenCorpus.MeterClass.CRITICAL, byValue[90.0]!!.expected)
        assertEquals("[91] must be critical", ParserParityGoldenCorpus.MeterClass.CRITICAL, byValue[91.0]!!.expected)
    }

    // -------------------------------------------------------------------------
    // Card gate: required IDs and all four types pass only when fully gated
    // -------------------------------------------------------------------------

    @Test
    fun `all required card gate case IDs are present`() {
        val presentIds = corpus.cardGateCases.map { it.id }.toSet()
        val missing = REQUIRED_GATE_IDS - presentIds
        assertTrue("Missing required card gate case IDs: $missing", missing.isEmpty())
    }

    @Test
    fun `all four known types pass with exact card tag and valid JSON object`() {
        val passCases = corpus.cardGateCases.filter { it.id.startsWith("gate-pass-") }
        val passingTypes = passCases.filter { it.expected == ParserParityGoldenCorpus.CardGateResult.STRUCTURED }
        val typesPassing = passingTypes.map { c ->
            try { com.google.gson.JsonParser.parseString(c.input.body).asJsonObject["type"]?.asString } catch (e: Exception) { null }
        }.filterNotNull().toSet()
        assertTrue("kv must pass card gate", "kv" in typesPassing)
        assertTrue("list must pass card gate", "list" in typesPassing)
        assertTrue("chart must pass card gate", "chart" in typesPassing)
        assertTrue("sections must pass card gate", "sections" in typesPassing)
    }

    @Test
    fun `top-level markdown type falls back`() {
        val case = corpus.cardGateCases.firstOrNull { it.id == "gate-fail-markdown-toplevel" }
        assertNotNull("Expected 'gate-fail-markdown-toplevel'", case)
        assertEquals("top-level markdown must fall back",
            ParserParityGoldenCorpus.CardGateResult.FALLBACK, case!!.expected)
    }

    @Test
    fun `differently-cased card tag falls back`() {
        val case = corpus.cardGateCases.firstOrNull { it.id == "gate-fail-uppercase-tag" }
        assertNotNull("Expected 'gate-fail-uppercase-tag'", case)
        assertEquals("uppercase Card tag must fall back",
            ParserParityGoldenCorpus.CardGateResult.FALLBACK, case!!.expected)
    }

    // -------------------------------------------------------------------------
    // Markdown: required link and image IDs present
    // -------------------------------------------------------------------------

    @Test
    fun `all required markdown link case IDs are present`() {
        val presentIds = corpus.markdownDestinationCases
            .filter { it.input.kind == ParserParityGoldenCorpus.MarkdownDestinationKind.LINK }
            .map { it.id }.toSet()
        val missing = REQUIRED_LINK_IDS - presentIds
        assertTrue("Missing required markdown link case IDs: $missing", missing.isEmpty())
    }

    @Test
    fun `all required markdown image case IDs are present`() {
        val presentIds = corpus.markdownDestinationCases
            .filter { it.input.kind == ParserParityGoldenCorpus.MarkdownDestinationKind.IMAGE }
            .map { it.id }.toSet()
        val missing = REQUIRED_IMAGE_IDS - presentIds
        assertTrue("Missing required markdown image case IDs: $missing", missing.isEmpty())
    }

    @Test
    fun `http https mailto links are live`() {
        val byId = corpus.markdownDestinationCases.associateBy { it.id }
        assertEquals("http link must be live",   ParserParityGoldenCorpus.MarkdownDestinationResult.LIVE,   byId["md-link-http-live"]!!.expected)
        assertEquals("https link must be live",  ParserParityGoldenCorpus.MarkdownDestinationResult.LIVE,   byId["md-link-https-live"]!!.expected)
        assertEquals("mailto link must be live", ParserParityGoldenCorpus.MarkdownDestinationResult.LIVE,   byId["md-link-mailto-live"]!!.expected)
    }

    @Test
    fun `javascript and data links are inert`() {
        val byId = corpus.markdownDestinationCases.associateBy { it.id }
        assertEquals("javascript link must be inert", ParserParityGoldenCorpus.MarkdownDestinationResult.INERT, byId["md-link-javascript-inert"]!!.expected)
        assertEquals("data link must be inert",       ParserParityGoldenCorpus.MarkdownDestinationResult.INERT, byId["md-link-data-inert"]!!.expected)
    }

    @Test
    fun `http and https images render`() {
        val byId = corpus.markdownDestinationCases.associateBy { it.id }
        assertEquals("http image must render",  ParserParityGoldenCorpus.MarkdownDestinationResult.RENDER, byId["md-img-http-render"]!!.expected)
        assertEquals("https image must render", ParserParityGoldenCorpus.MarkdownDestinationResult.RENDER, byId["md-img-https-render"]!!.expected)
    }

    @Test
    fun `data file relative images are dropped`() {
        val byId = corpus.markdownDestinationCases.associateBy { it.id }
        assertEquals("data image must drop",     ParserParityGoldenCorpus.MarkdownDestinationResult.DROP, byId["md-img-data-drop"]!!.expected)
        assertEquals("file image must drop",     ParserParityGoldenCorpus.MarkdownDestinationResult.DROP, byId["md-img-file-drop"]!!.expected)
        assertEquals("relative image must drop", ParserParityGoldenCorpus.MarkdownDestinationResult.DROP, byId["md-img-relative-drop"]!!.expected)
    }

    @Test
    fun `link and image cases have independent expectations for same URI`() {
        // Verify that link and image policies are tracked separately (same https:// produces
        // live for link and render for image — they are independent, not coupled).
        val linkCase  = corpus.markdownDestinationCases.firstOrNull { it.id == "md-link-https-live" }
        val imageCase = corpus.markdownDestinationCases.firstOrNull { it.id == "md-img-https-render" }
        assertNotNull("md-link-https-live must exist", linkCase)
        assertNotNull("md-img-https-render must exist", imageCase)
        assertEquals(ParserParityGoldenCorpus.MarkdownDestinationKind.LINK, linkCase!!.input.kind)
        assertEquals(ParserParityGoldenCorpus.MarkdownDestinationKind.IMAGE, imageCase!!.input.kind)
    }

    // -------------------------------------------------------------------------
    // Shape: required IDs present, paragraph and heuristic-kv boundaries covered
    // -------------------------------------------------------------------------

    @Test
    fun `all required shape case IDs are present`() {
        val presentIds = corpus.shapeCases.map { it.id }.toSet()
        val missing = REQUIRED_SHAPE_IDS - presentIds
        assertTrue("Missing required shape case IDs: $missing", missing.isEmpty())
    }

    @Test
    fun `empty body selects paragraph`() {
        val case = corpus.shapeCases.firstOrNull { it.id == "shape-empty-body" }
        assertNotNull("Expected 'shape-empty-body'", case)
        assertEquals(ParserParityGoldenCorpus.ShapeResult.PARAGRAPH, case!!.expected)
    }

    @Test
    fun `single plain line selects paragraph`() {
        val case = corpus.shapeCases.firstOrNull { it.id == "shape-single-line-plain" }
        assertNotNull("Expected 'shape-single-line-plain'", case)
        assertEquals("single plain line must select paragraph",
            ParserParityGoldenCorpus.ShapeResult.PARAGRAPH, case!!.expected)
    }

    @Test
    fun `single line with colon selects paragraph not heuristic-kv`() {
        val case = corpus.shapeCases.firstOrNull { it.id == "shape-single-line-colon" }
        assertNotNull("Expected 'shape-single-line-colon'", case)
        assertEquals("single kv line must still be paragraph",
            ParserParityGoldenCorpus.ShapeResult.PARAGRAPH, case!!.expected)
    }

    @Test
    fun `two kv lines select heuristic-kv`() {
        val case = corpus.shapeCases.firstOrNull { it.id == "shape-kv-two-lines" }
        assertNotNull("Expected 'shape-kv-two-lines'", case)
        assertEquals(ParserParityGoldenCorpus.ShapeResult.HEURISTIC_KV, case!!.expected)
    }

    @Test
    fun `kv with blank lines selects heuristic-kv (blank lines ignored)`() {
        val case = corpus.shapeCases.firstOrNull { it.id == "shape-kv-with-blank-lines" }
        assertNotNull("Expected 'shape-kv-with-blank-lines'", case)
        assertEquals("blank lines between kv pairs must not break heuristic",
            ParserParityGoldenCorpus.ShapeResult.HEURISTIC_KV, case!!.expected)
    }

    @Test
    fun `line with no key before colon makes body paragraph`() {
        val case = corpus.shapeCases.firstOrNull { it.id == "shape-para-no-key-before-colon" }
        assertNotNull("Expected 'shape-para-no-key-before-colon'", case)
        assertEquals(ParserParityGoldenCorpus.ShapeResult.PARAGRAPH, case!!.expected)
    }

    // -------------------------------------------------------------------------
    // Story 2.4 tag-hash corpus independence: this fixture must NOT contain
    // any tag-hash / tag-color cases (those belong to Story 2.4).
    // -------------------------------------------------------------------------

    @Test
    fun `corpus contains no tag-hash or tag-color vector groups`() {
        // Verify that all top-level groups are exactly the five owned by this story.
        // Must check raw JSON: Gson silently drops unknown fields, so a parsed Corpus
        // object cannot detect accidental tagHashCases additions.
        val stream = ParserParityGoldenCorpus::class.java
            .getResourceAsStream("/io/heckel/ntfy/ui/message/parser-parity-golden.json")
        assertNotNull("Golden corpus fixture must be on classpath", stream)
        val json = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertFalse("corpus must not contain tagHashCases", json.contains("tagHashCases"))
        assertFalse("corpus must not contain tagColorCases", json.contains("tagColorCases"))
    }
}
