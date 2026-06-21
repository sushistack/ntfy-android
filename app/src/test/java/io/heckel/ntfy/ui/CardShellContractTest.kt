package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Layout/shell contract tests for fragment_detail_item.xml.
 * Runs on JVM by parsing the XML directly without Android runtime.
 * Asserts the structural guarantees required by the shell contract (AC 1, 3, 7).
 */
class CardShellContractTest {

    private fun loadShellXml(): org.w3c.dom.Document {
        val candidates = listOf(
            "app/src/main/res/layout/fragment_detail_item.xml",
            "../app/src/main/res/layout/fragment_detail_item.xml",
        )
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("fragment_detail_item.xml not found from ${File(".").absolutePath}")
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(file)
    }

    private fun org.w3c.dom.Document.findById(id: String): Element? {
        val all = getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as? Element ?: continue
            val attrId = el.getAttribute("android:id")
            if (attrId == "@+id/$id" || attrId == "@id/$id") return el
        }
        return null
    }

    @Test
    fun `detail_item_card outer element exists`() {
        val doc = loadShellXml()
        val card = doc.findById("detail_item_card")
        assertNotNull("detail_item_card must exist", card)
    }

    @Test
    fun `card corner radius is zero`() {
        val doc = loadShellXml()
        val card = doc.findById("detail_item_card") ?: fail("detail_item_card not found")
        val radius = (card as Element).getAttribute("app:cardCornerRadius")
        assertEquals("Card corner radius must be 0dp", "0dp", radius)
    }

    @Test
    fun `card compat padding is disabled`() {
        val doc = loadShellXml()
        val card = doc.findById("detail_item_card") ?: fail("detail_item_card not found")
        val compatPadding = (card as Element).getAttribute("app:cardUseCompatPadding")
        assertEquals("cardUseCompatPadding must be false", "false", compatPadding)
    }

    @Test
    fun `card background color is surface token`() {
        val doc = loadShellXml()
        val card = doc.findById("detail_item_card") ?: fail("detail_item_card not found")
        val bg = (card as Element).getAttribute("app:cardBackgroundColor")
        assertEquals("Card background must use @color/surface token", "@color/surface", bg)
    }

    @Test
    fun `card_priority_accent placeholder exists`() {
        val doc = loadShellXml()
        val accent = doc.findById("card_priority_accent")
        assertNotNull("card_priority_accent must exist in shell layout", accent)
    }

    @Test
    fun `card_priority_accent width is 4dp`() {
        val doc = loadShellXml()
        val accent = doc.findById("card_priority_accent") ?: fail("card_priority_accent not found")
        val width = (accent as Element).getAttribute("android:layout_width")
        assertTrue(
            "card_priority_accent width must reference the 4dp accent token",
            width == "@dimen/card_priority_accent_width" || width == "4dp"
        )
    }

    @Test
    fun `card_body ViewGroup exists`() {
        val doc = loadShellXml()
        val body = doc.findById("card_body")
        assertNotNull("card_body ViewGroup must exist in shell layout", body)
    }

    @Test
    fun `card_body is a ViewGroup tag`() {
        val doc = loadShellXml()
        val body = doc.findById("card_body") ?: fail("card_body not found")
        val tagName = (body as Element).tagName
        // Must be a layout-type ViewGroup; ConstraintLayout or LinearLayout are acceptable
        assertTrue(
            "card_body must be a ViewGroup (ConstraintLayout, LinearLayout, FrameLayout, etc.), got: $tagName",
            tagName.contains("Layout") || tagName.contains("ViewGroup")
        )
    }

    @Test
    fun `card is the single clickable and focusable target`() {
        val doc = loadShellXml()
        val card = doc.findById("detail_item_card") ?: fail("detail_item_card not found")
        assertEquals("Outer card must be clickable", "true", (card as Element).getAttribute("android:clickable"))
        assertEquals("Outer card must be focusable", "true", card.getAttribute("android:focusable"))
    }

    @Test
    fun `focus ring foreground is applied to card`() {
        val doc = loadShellXml()
        val card = doc.findById("detail_item_card") ?: fail("detail_item_card not found")
        val foreground = (card as Element).getAttribute("android:foreground")
        assertEquals(
            "Card must use card_shell_foreground drawable for focus ring",
            "@drawable/card_shell_foreground",
            foreground
        )
    }

    @Test
    fun `legacy message body view IDs are preserved`() {
        val doc = loadShellXml()
        val requiredIds = listOf(
            "detail_item_date_text",
            "detail_item_message_text",
            "detail_item_title_text",
            "detail_item_priority_image",
            "detail_item_new_dot",
            "detail_item_menu_button",
            "detail_item_icon",
            "detail_item_attachment_image",
            "detail_item_tags_text",
            "detail_item_attachment_file_box",
            "detail_item_attachment_file_icon",
            "detail_item_attachment_file_info",
            "detail_item_actions_wrapper",
            "detail_item_actions_flow",
        )
        for (id in requiredIds) {
            assertNotNull("Legacy view ID '$id' must be preserved in shell layout", doc.findById(id))
        }
    }
}
