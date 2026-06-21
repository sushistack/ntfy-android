package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM contract tests for the X-delete flow (Story 2.3b).
 *
 * All tests run without the Android runtime by:
 *  - Parsing XML files directly (layout, colors, strings)
 *  - Reading Kotlin source as text for architecture guards
 *
 * Tests cover AC 1, 2, 3, 6, 7, 8.
 */
class NotificationDeleteContractTest {

    // ---- XML / source helpers ----

    private fun loadXml(vararg candidates: String): org.w3c.dom.Document {
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("XML not found. Tried: ${candidates.toList()} from ${File(".").absolutePath}")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
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

    private fun readSource(vararg candidates: String): String {
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("Source file not found. Tried: ${candidates.toList()}")
        return file.readText()
    }

    private val layoutCandidates = arrayOf(
        "app/src/main/res/layout/fragment_detail_item.xml",
        "../app/src/main/res/layout/fragment_detail_item.xml",
    )
    private val colorsCandidates = arrayOf(
        "app/src/main/res/values/colors.xml",
        "../app/src/main/res/values/colors.xml",
    )
    private val stringsCandidates = arrayOf(
        "app/src/main/res/values/strings.xml",
        "../app/src/main/res/values/strings.xml",
    )
    private val binderCandidates = arrayOf(
        "app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt",
        "../app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt",
    )
    private val actionsCandidates = arrayOf(
        "app/src/main/java/io/heckel/ntfy/ui/MessageCardActions.kt",
        "../app/src/main/java/io/heckel/ntfy/ui/MessageCardActions.kt",
    )
    private val confirmationCandidates = arrayOf(
        "app/src/main/java/io/heckel/ntfy/ui/NotificationDeleteConfirmation.kt",
        "../app/src/main/java/io/heckel/ntfy/ui/NotificationDeleteConfirmation.kt",
    )

    // ---- AC 1: Layout — card_delete_button exists and has 48dp touch target ----

    @Test
    fun `card_delete_button exists in layout`() {
        val doc = loadXml(*layoutCandidates)
        val btn = doc.findById("card_delete_button")
        assertNotNull("card_delete_button must be declared in fragment_detail_item.xml", btn)
    }

    @Test
    fun `card_delete_button has minimum 48dp touch target`() {
        val doc = loadXml(*layoutCandidates)
        val btn = doc.findById("card_delete_button") as Element
        val w = btn.getAttribute("android:layout_width")
        val h = btn.getAttribute("android:layout_height")
        assertTrue(
            "card_delete_button width must be ≥48dp (got $w)",
            w == "48dp" || w.endsWith("dp") && w.dropLast(2).toIntOrNull()?.let { it >= 48 } == true
        )
        assertTrue(
            "card_delete_button height must be ≥48dp (got $h)",
            h == "48dp" || h.endsWith("dp") && h.dropLast(2).toIntOrNull()?.let { it >= 48 } == true
        )
    }

    @Test
    fun `card_delete_button has localized content description`() {
        val doc = loadXml(*layoutCandidates)
        val btn = doc.findById("card_delete_button") as Element
        val cd = btn.getAttribute("android:contentDescription")
        assertTrue(
            "card_delete_button must have a @string content description, got: '$cd'",
            cd.startsWith("@string/")
        )
    }

    @Test
    fun `card_delete_button is focusable and clickable`() {
        val doc = loadXml(*layoutCandidates)
        val btn = doc.findById("card_delete_button") as Element
        assertEquals("card_delete_button must be focusable", "true", btn.getAttribute("android:focusable"))
        assertEquals("card_delete_button must be clickable", "true", btn.getAttribute("android:clickable"))
    }

    // ---- AC 2: Destructive tint uses priority_max, not raw color ----

    @Test
    fun `card_delete_button_tint references priority_max in color selector`() {
        val candidates = arrayOf(
            "app/src/main/res/color/card_delete_button_tint.xml",
            "../app/src/main/res/color/card_delete_button_tint.xml",
        )
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("card_delete_button_tint.xml not found")
        val content = file.readText()
        assertTrue(
            "card_delete_button_tint must reference @color/priority_max for destructive states",
            content.contains("@color/priority_max")
        )
        assertFalse(
            "card_delete_button_tint must not hard-code a hex color",
            Regex("""#[0-9A-Fa-f]{3,8}\b""").containsMatchIn(content)
        )
    }

    @Test
    fun `card_delete_button tint attribute references color selector resource`() {
        val doc = loadXml(*layoutCandidates)
        val btn = doc.findById("card_delete_button") as Element
        val tint = btn.getAttribute("app:tint")
        assertTrue(
            "card_delete_button app:tint must reference a @color resource, got: '$tint'",
            tint.startsWith("@color/")
        )
        assertFalse(
            "card_delete_button app:tint must not hard-code a hex value",
            tint.startsWith("#")
        )
    }

    @Test
    fun `priority_max color token is defined in colors xml`() {
        val doc = loadXml(*colorsCandidates)
        val all = doc.getElementsByTagName("color")
        var found = false
        for (i in 0 until all.length) {
            val el = all.item(i) as? Element ?: continue
            if (el.getAttribute("name") == "priority_max") { found = true; break }
        }
        assertTrue("@color/priority_max must be defined in colors.xml", found)
    }

    // ---- AC 3: Strings are dedicated, not reusing subscription/multi-delete copy ----

    @Test
    fun `dedicated notification_delete strings exist in strings xml`() {
        val doc = loadXml(*stringsCandidates)
        val all = doc.getElementsByTagName("string")
        val found = mutableSetOf<String>()
        for (i in 0 until all.length) {
            val el = all.item(i) as? Element ?: continue
            found.add(el.getAttribute("name"))
        }
        assertTrue("notification_delete_dialog_message must exist", "notification_delete_dialog_message" in found)
        assertTrue("notification_delete_dialog_delete must exist", "notification_delete_dialog_delete" in found)
        assertTrue("notification_delete_dialog_cancel must exist", "notification_delete_dialog_cancel" in found)
        assertTrue("card_delete_button_content_description must exist", "card_delete_button_content_description" in found)
    }

    // ---- AC 6: Event is consumed; binder does not open dialog or mutate Room ----

    @Test
    fun `MessageCardBinder does not reference Repository`() {
        val src = readSource(*binderCandidates)
        val constructorBlock = src.substringAfter("class MessageCardBinder(").substringBefore(") {")
        assertFalse("MessageCardBinder constructor must not take Repository", constructorBlock.contains("Repository"))
    }

    @Test
    fun `MessageCardBinder does not reference FragmentManager or AlertDialog`() {
        val src = readSource(*binderCandidates)
        assertFalse("MessageCardBinder must not use FragmentManager", src.contains("FragmentManager"))
        assertFalse("MessageCardBinder must not show dialogs directly", src.contains("AlertDialog"))
        assertFalse("MessageCardBinder must not show dialogs directly", src.contains("MaterialAlertDialogBuilder"))
    }

    @Test
    fun `MessageCardBinder does not use GlobalScope`() {
        val src = readSource(*binderCandidates)
        assertFalse("MessageCardBinder must not use GlobalScope", src.contains("GlobalScope"))
    }

    // ---- AC 7: MessageCardActions interface declares onDeleteRequested ----

    @Test
    fun `MessageCardActions declares onDeleteRequested`() {
        val src = readSource(*actionsCandidates)
        assertTrue(
            "MessageCardActions must declare onDeleteRequested",
            src.contains("fun onDeleteRequested")
        )
    }

    // ---- AC 8: NotificationDeleteConfirmation is independent of DetailAdapter ----

    @Test
    fun `NotificationDeleteConfirmation file exists`() {
        val file = confirmationCandidates.map { File(it) }.firstOrNull { it.exists() }
        assertNotNull("NotificationDeleteConfirmation.kt must exist", file)
    }

    @Test
    fun `NotificationDeleteConfirmation does not reference DetailAdapter`() {
        val src = readSource(*confirmationCandidates)
        val codeLines = src.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertFalse(
            "NotificationDeleteConfirmation must not reference DetailAdapter",
            codeLines.contains("DetailAdapter")
        )
    }

    @Test
    fun `NotificationDeleteConfirmation uses MaterialAlertDialogBuilder`() {
        val src = readSource(*confirmationCandidates)
        assertTrue(
            "NotificationDeleteConfirmation must use MaterialAlertDialogBuilder for token-backed styling",
            src.contains("MaterialAlertDialogBuilder")
        )
    }

    @Test
    fun `NotificationDeleteConfirmation uses priority_max for destructive action`() {
        val src = readSource(*confirmationCandidates)
        assertTrue(
            "NotificationDeleteConfirmation must tint Delete action with R.color.priority_max",
            src.contains("priority_max")
        )
    }

    @Test
    fun `NotificationDeleteConfirmation guards against double-confirmation`() {
        val src = readSource(*confirmationCandidates)
        assertTrue(
            "NotificationDeleteConfirmation must guard against duplicate confirmations (confirmed flag)",
            src.contains("confirmed")
        )
    }

    // ---- Layout: legacy IDs still present ----

    @Test
    fun `legacy body view IDs are preserved in layout`() {
        val doc = loadXml(*layoutCandidates)
        val requiredIds = listOf(
            "detail_item_date_text",
            "detail_item_message_text",
            "detail_item_title_text",
            "detail_item_menu_button",
            "detail_item_icon",
            "detail_item_attachment_image",
            // Story 2.4: tag row replaced with chip group + timestamp
            "card_tag_chip_group",
            "card_meta_timestamp",
            "detail_item_attachment_file_box",
            "detail_item_attachment_file_info",
            "detail_item_actions_wrapper",
            "detail_item_actions_flow",
        )
        for (id in requiredIds) {
            assertNotNull("Legacy view ID '$id' must be preserved", doc.findById(id))
        }
    }
}
