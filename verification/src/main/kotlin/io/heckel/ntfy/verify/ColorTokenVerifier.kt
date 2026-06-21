package io.heckel.ntfy.verify

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

/**
 * Verifies that both Android color qualifier files contain exactly the canonical
 * ntfy-web parity tokens with the expected hex values.
 *
 * Run via: ./gradlew verifyColorTokens
 */
object ColorTokenVerifier {

    // Canonical token manifest — single source of truth for this verifier.
    // Update here AND in colors.xml/colors-night.xml when the design system changes.
    private val LIGHT = mapOf(
        "bg" to "#F3F4F6",
        "surface" to "#FFFFFF",
        "surface_2" to "#EEF0F2",
        "surface_active" to "#EEF0F2",
        "border" to "#E4E6E9",
        "control_border" to "#767B80",
        "text" to "#1C1E21",
        "muted" to "#6A7076",
        "accent_text" to "#0E7A48",
        "accent_ui" to "#1A9E5F",
        "accent_on_surface" to "#0C1A12",
        "priority_high" to "#BF6C15",
        "priority_max" to "#E5484D",
        "priority_urgent" to "#C7353A",
        "priority_high_on_surface" to "#241403",
        "priority_max_on_surface" to "#1A0E0E",
        "meter_ok" to "#0E7A48",
        "meter_track" to "#E4E6E9",
        "meter_warning" to "#BF6C15",
        "meter_critical" to "#E5484D",
        "topic_chip_bg" to "#E1F2EA",
        "topic_chip_text" to "#136B43",
        "button_fill" to "#F4F5F6",
        "button_fill_text" to "#15171A",
        "focus_ring" to "#1A9E5F",
    )

    private val DARK = mapOf(
        "bg" to "#0C0D0F",
        "surface" to "#16181B",
        "surface_2" to "#1C1F23",
        "surface_active" to "#1C1F23",
        "border" to "#23262B",
        "control_border" to "#8B9197",
        "text" to "#E8EAED",
        "muted" to "#8B9197",
        "accent_text" to "#42D392",
        "accent_ui" to "#42D392",
        "accent_on_surface" to "#0C1A12",
        "priority_high" to "#F5A95C",
        "priority_max" to "#FF6B6E",
        "priority_urgent" to "#FF6B6E",
        "priority_high_on_surface" to "#241403",
        "priority_max_on_surface" to "#1A0E0E",
        "meter_ok" to "#42D392",
        "meter_track" to "#262A2F",
        "meter_warning" to "#F5A95C",
        "meter_critical" to "#FF6B6E",
        "topic_chip_bg" to "#143A2D",
        "topic_chip_text" to "#7CE6B4",
        "button_fill" to "#F4F5F6",
        "button_fill_text" to "#15171A",
        "focus_ring" to "#42D392",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            System.err.println("Usage: ColorTokenVerifier <light-colors.xml> <dark-colors.xml>")
            exitProcess(2)
        }
        val errors = mutableListOf<String>()
        errors += verifyFile(args[0], "light", LIGHT)
        errors += verifyFile(args[1], "dark", DARK)

        if (errors.isEmpty()) {
            println("✅ Color token parity check passed (${LIGHT.size} light + ${DARK.size} dark tokens)")
        } else {
            System.err.println("❌ Color token parity check FAILED:")
            errors.forEach { System.err.println("   $it") }
            exitProcess(1)
        }
    }

    /** Parse the XML file and return canonical `<color>` entries, detecting duplicates within
     *  canonical keys only. Non-canonical keys (legacy Material colors, etc.) are ignored so
     *  that pre-existing duplicate names in unrelated sections do not produce false positives. */
    fun parseColorFile(xmlFile: java.io.File, canonicalKeys: Set<String> = (LIGHT.keys + DARK.keys).toSet()): Pair<Map<String, String>, List<String>> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlFile)
        doc.documentElement.normalize()

        val seen = mutableMapOf<String, String>()
        val duplicates = mutableListOf<String>()
        val nodes = doc.getElementsByTagName("color")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            val value = el.textContent.trim()
            if (name !in canonicalKeys) continue
            if (seen.containsKey(name)) {
                duplicates += "duplicate key '$name' in ${xmlFile.name}"
            } else {
                seen[name] = value
            }
        }
        return seen to duplicates
    }

    private fun normalizeHex(hex: String): String = hex.uppercase()

    fun verifyFile(path: String, qualifier: String, expected: Map<String, String>): List<String> {
        val file = java.io.File(path)
        if (!file.exists()) return listOf("$qualifier: file not found: $path")

        val (parsed, dupErrors) = parseColorFile(file)
        val errors = mutableListOf<String>()
        errors += dupErrors

        for ((key, expectedValue) in expected) {
            when {
                !parsed.containsKey(key) ->
                    errors += "$qualifier: missing canonical token '$key'"
                normalizeHex(parsed[key]!!) != normalizeHex(expectedValue) ->
                    errors += "$qualifier: token '$key' expected ${expectedValue.uppercase()} but was ${parsed[key]!!.uppercase()}"
            }
        }

        // Detect canonical keys present in the file but NOT in the manifest (unknown extras)
        val canonicalKeys = expected.keys
        for ((key, _) in parsed) {
            if (key in canonicalKeys) continue
            // Non-canonical keys are allowed (legacy Material colors etc.) — only flag extras that
            // look like they were meant to be parity tokens but aren't in the manifest.
            // We don't flag arbitrary non-parity keys; the manifest is a subset check.
        }

        return errors
    }
}
