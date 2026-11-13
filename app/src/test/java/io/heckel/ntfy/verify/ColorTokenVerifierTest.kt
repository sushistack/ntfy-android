package io.heckel.ntfy.verify

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import java.io.File

/**
 * Negative-fixture tests for the color token parity verification logic.
 * Tests AC 4 (parity check) and AC 5 (no-raw-color check) by running
 * the same structural XML check logic against known-bad fixture files.
 */
class ColorTokenVerifierTest {

    companion object {
        private val LIGHT = mapOf(
            "bg" to "#F2F4F6", "surface" to "#FFFFFF", "surface_2" to "#F2F4F6",
            "surface_active" to "#F2F4F6", "border" to "#E5E8EB", "control_border" to "#848E99",
            "text" to "#191F28", "muted" to "#636E7B", "accent_text" to "#1B64DA",
            "accent_ui" to "#1D6BE5", "accent_on_surface" to "#FFFFFF",
            "priority_high" to "#C96800", "priority_max" to "#E5404D", "priority_urgent" to "#D22030",
            "priority_high_on_surface" to "#241403", "priority_max_on_surface" to "#1A0E0E",
            "meter_ok" to "#06804F", "meter_track" to "#E5E8EB", "meter_warning" to "#C96800",
            "meter_critical" to "#E5404D", "topic_chip_bg" to "#E8F3FF", "topic_chip_text" to "#1B64DA",
            "button_fill" to "#1D6BE5", "button_fill_text" to "#FFFFFF", "focus_ring" to "#1D6BE5",
        )
        private val DARK = mapOf(
            "bg" to "#17171C", "surface" to "#202027", "surface_2" to "#2C2C35",
            "surface_active" to "#2C2C35", "border" to "#2C2C35", "control_border" to "#7E838F",
            "text" to "#ECECF0", "muted" to "#9EA3AD", "accent_text" to "#64A8FF",
            "accent_ui" to "#2870E6", "accent_on_surface" to "#FFFFFF",
            "priority_high" to "#FFA940", "priority_max" to "#FF6370", "priority_urgent" to "#FF6370",
            "priority_high_on_surface" to "#241403", "priority_max_on_surface" to "#1A0E0E",
            "meter_ok" to "#2FCA86", "meter_track" to "#383842", "meter_warning" to "#FFA940",
            "meter_critical" to "#FF6370", "topic_chip_bg" to "#1B2A44", "topic_chip_text" to "#8DBCFF",
            "button_fill" to "#1D6BE5", "button_fill_text" to "#FFFFFF", "focus_ring" to "#2870E6",
        )
        private val RAW_HEX = Regex("""#[0-9A-Fa-f]{3}(?:[0-9A-Fa-f](?:[0-9A-Fa-f]{2}(?:[0-9A-Fa-f]{2})?)?)?(?![0-9A-Fa-f])""")

        private fun fixture(name: String): File {
            val url = ColorTokenVerifierTest::class.java.classLoader!!
                .getResource("verify/fixtures/$name")
                ?: error("Fixture not found: $name")
            return File(url.toURI())
        }

        private fun parseColors(file: File, canonicalKeys: Set<String>): Pair<Map<String, String>, List<String>> {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            doc.documentElement.normalize()
            val seen = mutableMapOf<String, String>()
            val duplicates = mutableListOf<String>()
            val nodes = doc.getElementsByTagName("color")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val name = el.getAttribute("name")
                val value = el.textContent.trim()
                if (name in canonicalKeys) {
                    if (seen.containsKey(name)) duplicates += name else seen[name] = value
                }
            }
            return seen to duplicates
        }

        private fun checkTokens(file: File, expected: Map<String, String>): List<String> {
            val (parsed, dups) = parseColors(file, expected.keys)
            val errors = mutableListOf<String>()
            errors += dups.map { "duplicate canonical key: $it" }
            for ((key, expectedHex) in expected) {
                val actual = parsed[key]
                when {
                    actual == null -> errors += "missing token '$key'"
                    actual.uppercase() != expectedHex.uppercase() ->
                        errors += "token '$key': expected ${expectedHex.uppercase()} but was ${actual.uppercase()}"
                }
            }
            return errors
        }

        private fun findRawHexViolations(content: String, allowlist: Set<String> = emptySet()): List<String> {
            return RAW_HEX.findAll(content)
                .map { it.value }
                .filter { it !in allowlist }
                .toList()
        }
    }

    // -------------------------------------------------------------------------
    // Positive test: checked-in production resource files must match the manifest
    // -------------------------------------------------------------------------

    @Test
    fun `production light colors_xml matches full manifest`() {
        val lightFile = File("src/main/res/values/colors.xml")
        assertTrue("values/colors.xml must exist", lightFile.exists())
        val errors = checkTokens(lightFile, LIGHT)
        assertTrue("Light color token mismatches:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    @Test
    fun `production dark colors_xml matches full manifest`() {
        val darkFile = File("src/main/res/values-night/colors.xml")
        assertTrue("values-night/colors.xml must exist", darkFile.exists())
        val errors = checkTokens(darkFile, DARK)
        assertTrue("Dark color token mismatches:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    @Test
    fun `manifest contains exactly 25 light tokens and 25 dark tokens`() {
        assertEquals("canonical light token count", 25, LIGHT.size)
        assertEquals("canonical dark token count", 25, DARK.size)
    }

    // -------------------------------------------------------------------------
    // Negative tests: fixture files that must trigger failures
    // -------------------------------------------------------------------------

    @Test
    fun `missing token triggers failure`() {
        val errors = checkTokens(fixture("colors_missing_key.xml"), LIGHT)
        assertTrue("Expected missing-key error but got: $errors",
            errors.any { it.contains("missing token 'bg'") })
    }

    @Test
    fun `wrong hex value triggers failure`() {
        val errors = checkTokens(fixture("colors_wrong_hex.xml"), LIGHT)
        assertTrue("Expected wrong-hex error but got: $errors",
            errors.any { it.contains("'bg'") && it.contains("F2F4F6") })
    }

    @Test
    fun `duplicate canonical key triggers failure`() {
        val errors = checkTokens(fixture("colors_duplicate_key.xml"), LIGHT)
        assertTrue("Expected duplicate-key error but got: $errors",
            errors.any { it.contains("duplicate canonical key: bg") })
    }

    // -------------------------------------------------------------------------
    // Negative tests: raw hex in parity UI files
    // -------------------------------------------------------------------------

    @Test
    fun `raw hex literal in parity UI file triggers failure`() {
        val content = """<TextView android:textColor="#FF0000" />"""
        val violations = findRawHexViolations(content)
        assertTrue("Raw #FF0000 should be flagged", violations.contains("#FF0000"))
    }

    @Test
    fun `RGB shorthand raw hex is flagged`() {
        val content = """<color name="foo">#FFF</color>"""
        val violations = findRawHexViolations(content)
        assertTrue("RGB shorthand #FFF should be flagged", violations.any { it.startsWith("#FFF") })
    }

    @Test
    fun `ARGB raw hex is flagged`() {
        val content = """android:background="#80FF0000""""
        val violations = findRawHexViolations(content)
        assertTrue("ARGB #80FF0000 should be flagged", violations.contains("#80FF0000"))
    }

    @Test
    fun `resource reference at_color is not flagged as raw hex`() {
        val content = """android:textColor="@color/accent_text""""
        val violations = findRawHexViolations(content)
        assertTrue("@color/ references must not be flagged", violations.isEmpty())
    }

    @Test
    fun `allowlisted Story 1_2 palette literal is permitted`() {
        val allowedLiteral = "#FF6600"
        val content = """<color name="tag_orange">$allowedLiteral</color>"""
        val violations = findRawHexViolations(content, allowlist = setOf(allowedLiteral))
        assertTrue("Allowlisted Story 1.2 literal must not produce a violation", violations.isEmpty())
    }
}
