package io.heckel.ntfy.ui.accessibility

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Structural contract tests for focus_indicator.xml and its FocusIndicator style.
 * Runs on JVM by parsing XML directly — no Android runtime required.
 * Asserts AC 2: 2dp @color/focus_ring stroke, foreground overlay, separate transparent default.
 */
class FocusIndicatorContractTest {

    private fun parseXml(vararg candidates: String): org.w3c.dom.Document {
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("File not found. Tried: ${candidates.toList()} from ${File(".").absolutePath}")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    // ── focus_indicator.xml ────────────────────────────────────────────────

    @Test
    fun `focus_indicator xml has a focused state item`() {
        val doc = parseXml(
            "app/src/main/res/drawable/focus_indicator.xml",
            "../app/src/main/res/drawable/focus_indicator.xml",
        )
        val items = doc.getElementsByTagName("item")
        val focusedItem = (0 until items.length)
            .map { items.item(it) as? Element }
            .firstOrNull { it?.getAttribute("android:state_focused") == "true" }
        assertNotNull("focus_indicator.xml must contain an item with android:state_focused=\"true\"", focusedItem)
    }

    @Test
    fun `focused state uses focus_ring color`() {
        val doc = parseXml(
            "app/src/main/res/drawable/focus_indicator.xml",
            "../app/src/main/res/drawable/focus_indicator.xml",
        )
        val strokes = doc.getElementsByTagName("stroke")
        assertTrue("Focused state must define a <stroke>", strokes.length > 0)
        val stroke = strokes.item(0) as? Element
        assertEquals(
            "Stroke color must reference @color/focus_ring",
            "@color/focus_ring",
            stroke?.getAttribute("android:color"),
        )
    }

    @Test
    fun `focused state stroke is 2dp`() {
        val doc = parseXml(
            "app/src/main/res/drawable/focus_indicator.xml",
            "../app/src/main/res/drawable/focus_indicator.xml",
        )
        val strokes = doc.getElementsByTagName("stroke")
        assertTrue("Focused state must define a <stroke>", strokes.length > 0)
        val stroke = strokes.item(0) as? Element
        assertEquals(
            "Stroke width must reference the 2dp token @dimen/card_focus_ring_width",
            "@dimen/card_focus_ring_width",
            stroke?.getAttribute("android:width"),
        )
    }

    @Test
    fun `default state is transparent so content background is preserved`() {
        val doc = parseXml(
            "app/src/main/res/drawable/focus_indicator.xml",
            "../app/src/main/res/drawable/focus_indicator.xml",
        )
        val items = doc.getElementsByTagName("item")
        // The default item (no state attributes) must exist and be transparent.
        val defaultItem = (0 until items.length)
            .map { items.item(it) as? Element }
            .firstOrNull { el ->
                el != null &&
                el.getAttribute("android:state_focused").isEmpty() &&
                el.getAttribute("android:state_pressed").isEmpty()
            }
        assertNotNull("focus_indicator.xml must have a default (no-state) item to preserve backgrounds", defaultItem)

        val solids = defaultItem!!.getElementsByTagName("solid")
        if (solids.length > 0) {
            val color = (solids.item(0) as? Element)?.getAttribute("android:color") ?: ""
            assertEquals(
                "Default item must use @android:color/transparent",
                "@android:color/transparent",
                color,
            )
        }
    }

    // ── FocusIndicator style in themes.xml ────────────────────────────────

    @Test
    fun `FocusIndicator style exists in themes xml`() {
        val doc = parseXml(
            "app/src/main/res/values/themes.xml",
            "../app/src/main/res/values/themes.xml",
        )
        val styles = doc.getElementsByTagName("style")
        val focusStyle = (0 until styles.length)
            .map { styles.item(it) as? Element }
            .firstOrNull { it?.getAttribute("name") == "FocusIndicator" }
        assertNotNull("themes.xml must declare a style named \"FocusIndicator\"", focusStyle)
    }

    @Test
    fun `FocusIndicator style sets foreground to focus_indicator drawable`() {
        val doc = parseXml(
            "app/src/main/res/values/themes.xml",
            "../app/src/main/res/values/themes.xml",
        )
        val styles = doc.getElementsByTagName("style")
        val focusStyleOrNull = (0 until styles.length)
            .map { styles.item(it) as? Element }
            .firstOrNull { it?.getAttribute("name") == "FocusIndicator" }
        assertNotNull("themes.xml must declare a style named \"FocusIndicator\"", focusStyleOrNull)
        val focusStyle = focusStyleOrNull!!

        val items = focusStyle.getElementsByTagName("item")
        val foregroundItem = (0 until items.length)
            .map { items.item(it) as? Element }
            .firstOrNull { it?.getAttribute("name") == "android:foreground" }
        assertNotNull("FocusIndicator style must set android:foreground", foregroundItem)
        assertEquals(
            "android:foreground must reference @drawable/focus_indicator",
            "@drawable/focus_indicator",
            foregroundItem?.textContent?.trim(),
        )
    }
}
