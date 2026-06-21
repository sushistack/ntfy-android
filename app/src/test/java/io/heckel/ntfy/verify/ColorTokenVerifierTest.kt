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
            "bg" to "#F3F4F6", "surface" to "#FFFFFF", "surface_2" to "#EEF0F2",
            "surface_active" to "#EEF0F2", "border" to "#E4E6E9", "control_border" to "#767B80",
            "text" to "#1C1E21", "muted" to "#6A7076", "accent_text" to "#0E7A48",
            "accent_ui" to "#1A9E5F", "accent_on_surface" to "#0C1A12",
            "priority_high" to "#BF6C15", "priority_max" to "#E5484D", "priority_urgent" to "#C7353A",
            "priority_high_on_surface" to "#241403", "priority_max_on_surface" to "#1A0E0E",
            "meter_ok" to "#0E7A48", "meter_track" to "#E4E6E9", "meter_warning" to "#BF6C15",
            "meter_critical" to "#E5484D", "topic_chip_bg" to "#E1F2EA", "topic_chip_text" to "#136B43",
            "button_fill" to "#F4F5F6", "button_fill_text" to "#15171A", "focus_ring" to "#1A9E5F",
        )
        private val DARK = mapOf(
            "bg" to "#0C0D0F", "surface" to "#16181B", "surface_2" to "#1C1F23",
            "surface_active" to "#1C1F23", "border" to "#23262B", "control_border" to "#8B9197",
            "text" to "#E8EAED", "muted" to "#8B9197", "accent_text" to "#42D392",
            "accent_ui" to "#42D392", "accent_on_surface" to "#0C1A12",
            "priority_high" to "#F5A95C", "priority_max" to "#FF6B6E", "priority_urgent" to "#FF6B6E",
            "priority_high_on_surface" to "#241403", "priority_max_on_surface" to "#1A0E0E",
            "meter_ok" to "#42D392", "meter_track" to "#262A2F", "meter_warning" to "#F5A95C",
            "meter_critical" to "#FF6B6E", "topic_chip_bg" to "#143A2D", "topic_chip_text" to "#7CE6B4",
            "button_fill" to "#F4F5F6", "button_fill_text" to "#15171A", "focus_ring" to "#42D392",
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
            errors.any { it.contains("'bg'") && it.contains("F3F4F6") })
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
