package io.heckel.ntfy.ui.structured

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory
import java.io.File

/**
 * Resource-level verifier for the inline meter component (Story 3.2, AC 7).
 *
 * Checks:
 *  - meter_ok / meter_warning / meter_critical / meter_track present in light and dark colors.xml
 *  - meter_track_height = 7dp in dimens.xml
 *  - radius_full present in dimens.xml (pill corners)
 *  - No raw hex or raw px literals in InlineMeterView.kt or MeterState.kt
 */
class MeterResourceTest {

    private val projectRoot = File(System.getProperty("user.dir", "."))
        .let { if (it.name == "app") it.parentFile else it }

    private val resValues = projectRoot.resolve("app/src/main/res/values")
    private val resNight = projectRoot.resolve("app/src/main/res/values-night")
    private val structuredSrc = projectRoot.resolve(
        "app/src/main/java/io/heckel/ntfy/ui/structured"
    )

    // -------------------------------------------------------------------------
    // Color token presence — light
    // -------------------------------------------------------------------------

    @Test fun light_meter_ok_defined() = assertColorDefined(resValues, "colors.xml", "meter_ok")
    @Test fun light_meter_track_defined() = assertColorDefined(resValues, "colors.xml", "meter_track")
    @Test fun light_meter_warning_defined() = assertColorDefined(resValues, "colors.xml", "meter_warning")
    @Test fun light_meter_critical_defined() = assertColorDefined(resValues, "colors.xml", "meter_critical")

    // -------------------------------------------------------------------------
    // Color token presence — dark
    // -------------------------------------------------------------------------

    @Test fun dark_meter_ok_defined() = assertColorDefined(resNight, "colors.xml", "meter_ok")
    @Test fun dark_meter_track_defined() = assertColorDefined(resNight, "colors.xml", "meter_track")
    @Test fun dark_meter_warning_defined() = assertColorDefined(resNight, "colors.xml", "meter_warning")
    @Test fun dark_meter_critical_defined() = assertColorDefined(resNight, "colors.xml", "meter_critical")

    // -------------------------------------------------------------------------
    // Dimension tokens
    // -------------------------------------------------------------------------

    @Test fun dimens_meter_track_height_is_7dp() {
        val value = readDimen(resValues, "dimens.xml", "meter_track_height")
        assertEquals("meter_track_height must be 7dp", "7dp", value)
    }

    @Test fun dimens_radius_full_defined() {
        val value = readDimen(resValues, "dimens.xml", "radius_full")
        assertNotNull("radius_full must be defined in dimens.xml", value)
    }

    // -------------------------------------------------------------------------
    // No raw literals in source files
    // -------------------------------------------------------------------------

    private val RAW_HEX = Regex("""#[0-9A-Fa-f]{3,8}\b""")
    private val RAW_PX = Regex("""\d+(\.\d+)?\.px\b|=\s*\d+f?\s*//\s*px""")

    @Test fun inlineMeterView_noRawHex() {
        val src = structuredSrc.resolve("InlineMeterView.kt").readText()
        val hits = RAW_HEX.findAll(src).map { it.value }.toList()
        assertTrue("InlineMeterView.kt must not contain raw hex literals: $hits", hits.isEmpty())
    }

    @Test fun meterState_noRawHex() {
        val src = structuredSrc.resolve("MeterState.kt").readText()
        val hits = RAW_HEX.findAll(src).map { it.value }.toList()
        assertTrue("MeterState.kt must not contain raw hex literals: $hits", hits.isEmpty())
    }

    @Test fun inlineMeterView_noRawPx() {
        val src = structuredSrc.resolve("InlineMeterView.kt").readText()
        val hits = RAW_PX.findAll(src).map { it.value }.toList()
        assertTrue("InlineMeterView.kt must not contain raw px literals: $hits", hits.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun parseXml(dir: File, fileName: String) =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(dir.resolve(fileName))

    private fun allElements(nodes: NodeList): List<Element> =
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }

    private fun assertColorDefined(dir: File, file: String, name: String) {
        val doc = parseXml(dir, file)
        val colors = allElements(doc.getElementsByTagName("color"))
        val found = colors.any { it.getAttribute("name") == name }
        assertTrue("Color '$name' not found in ${dir.name}/$file", found)
    }

    private fun readDimen(dir: File, file: String, name: String): String? {
        val doc = parseXml(dir, file)
        val dimens = allElements(doc.getElementsByTagName("dimen"))
        return dimens.firstOrNull { it.getAttribute("name") == name }?.textContent?.trim()
    }
}
