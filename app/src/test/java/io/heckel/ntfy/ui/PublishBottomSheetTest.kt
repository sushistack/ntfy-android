package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests for Story 4.8: Publish FAB Bottom Sheet.
 *
 * Covers:
 * - Default priority chip is Normal (value=3) on open
 * - Priority constant declarations match AC values (Low=2, Normal=3, High=4, Urgent=5)
 * - Send button disabled/enabled logic contract (source inspection)
 * - String resources for publish sheet present and non-empty
 * - Layout file structure: drag handle, title, fields, chip group, footer
 * - PublishBottomSheet extends BottomSheetDialogFragment
 * - newInstance() factory exists and accepts initialTopic
 * - FAB wired in FeedActivity to show PublishBottomSheet
 * - No modification to PublishFragment or fragment_publish_dialog.xml
 */
class PublishBottomSheetTest {

    private fun readSource(relativePath: String): String {
        val candidates = listOf(relativePath, "../$relativePath")
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("Source not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private fun readLayout(name: String) =
        readSource("app/src/main/res/layout/$name")

    private fun readStrings() =
        readSource("app/src/main/res/values/strings.xml")

    private fun readBottomSheet() =
        readSource("app/src/main/java/io/heckel/ntfy/ui/PublishBottomSheet.kt")

    // ── Priority default value ──────────────────────────────────────────────

    @Test
    fun publishBottomSheet_defaultPriority_isNormal() {
        val source = readBottomSheet()
        assertTrue(
            "selectedPriority must default to PRIORITY_NORMAL (value 3)",
            source.contains("selectedPriority = PRIORITY_NORMAL")
        )
        assertTrue(
            "PRIORITY_NORMAL must be declared as 3",
            source.contains("PRIORITY_NORMAL = 3")
        )
    }

    @Test
    fun publishBottomSheet_priorityConstants_matchAcValues() {
        val source = readBottomSheet()
        assertTrue("PRIORITY_LOW must equal 2",    source.contains("PRIORITY_LOW    = 2") || source.contains("PRIORITY_LOW = 2"))
        assertTrue("PRIORITY_NORMAL must equal 3", source.contains("PRIORITY_NORMAL = 3"))
        assertTrue("PRIORITY_HIGH must equal 4",   source.contains("PRIORITY_HIGH   = 4") || source.contains("PRIORITY_HIGH = 4"))
        assertTrue("PRIORITY_URGENT must equal 5", source.contains("PRIORITY_URGENT = 5"))
    }

    @Test
    fun publishBottomSheet_chipGroup_hasSingleSelection() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue(
            "ChipGroup must have app:singleSelection=\"true\"",
            xml.contains("singleSelection=\"true\"")
        )
        assertTrue(
            "ChipGroup must have app:selectionRequired=\"true\"",
            xml.contains("selectionRequired=\"true\"")
        )
    }

    @Test
    fun publishBottomSheet_normalChip_defaultChecked() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue(
            "chip_priority_normal must have android:checked=\"true\" as default",
            xml.contains("chip_priority_normal") && xml.contains("android:checked=\"true\"")
        )
    }

    // ── Send button enabled/disabled contract ──────────────────────────────

    @Test
    fun publishBottomSheet_sendButton_disabledByDefault() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue(
            "Send button must default to android:enabled=\"false\"",
            xml.contains("publish_sheet_send") && xml.contains("android:enabled=\"false\"")
        )
    }

    @Test
    fun publishBottomSheet_validation_checksTopicAndMessage() {
        val source = readBottomSheet()
        assertTrue(
            "updateSendEnabled must check topicText for blank",
            source.contains("topicText") && (source.contains("isNullOrBlank") || source.contains("isEmpty"))
        )
        assertTrue(
            "updateSendEnabled must check messageText for blank",
            source.contains("messageText") && (source.contains("isNullOrBlank") || source.contains("isEmpty"))
        )
    }

    @Test
    fun publishBottomSheet_textWatchers_addedForTopicAndMessage() {
        val source = readBottomSheet()
        assertTrue(
            "TextWatcher must be added to topicText",
            source.contains("topicText.addTextChangedListener") || source.contains("addTextChangedListener")
        )
    }

    // ── Send calls ApiService ──────────────────────────────────────────────

    @Test
    fun publishBottomSheet_onSend_callsApiPublish() {
        val source = readBottomSheet()
        assertTrue(
            "onSendClick must call api.publish()",
            source.contains("api.publish(")
        )
    }

    @Test
    fun publishBottomSheet_onSend_passesSelectedPriority() {
        val source = readBottomSheet()
        assertTrue(
            "api.publish() call must pass selectedPriority",
            source.contains("priority  = selectedPriority") || source.contains("priority = selectedPriority")
        )
    }

    @Test
    fun publishBottomSheet_onSuccess_dismisses() {
        val source = readBottomSheet()
        assertTrue(
            "On publish success the sheet must call dismiss()",
            source.contains("dismiss()")
        )
    }

    @Test
    fun publishBottomSheet_onFailure_showsError() {
        val source = readBottomSheet()
        assertTrue(
            "On publish failure the sheet must call showError()",
            source.contains("showError(")
        )
    }

    // ── Fragment class and factory ─────────────────────────────────────────

    @Test
    fun publishBottomSheet_extendsBottomSheetDialogFragment() {
        val source = readBottomSheet()
        assertTrue(
            "PublishBottomSheet must extend BottomSheetDialogFragment",
            source.contains(": BottomSheetDialogFragment()")
        )
    }

    @Test
    fun publishBottomSheet_newInstance_factoryExists() {
        val source = readBottomSheet()
        assertTrue(
            "companion object must declare newInstance()",
            source.contains("fun newInstance(")
        )
    }

    @Test
    fun publishBottomSheet_newInstance_acceptsInitialTopic() {
        val source = readBottomSheet()
        assertTrue(
            "newInstance() must accept initialTopic parameter",
            source.contains("initialTopic")
        )
    }

    @Test
    fun publishBottomSheet_newInstance_passesTopicViaBundle() {
        val source = readBottomSheet()
        assertTrue(
            "newInstance() must put initialTopic into a Bundle",
            source.contains("Bundle()") && source.contains("putString")
        )
    }

    // ── FAB wiring in FeedActivity ─────────────────────────────────────────

    @Test
    fun feedActivity_fab_defined() {
        val xml = readLayout("activity_feed.xml")
        assertTrue(
            "activity_feed.xml must declare the feed FAB with id feed_fab",
            xml.contains("feed_fab")
        )
        assertTrue(
            "FAB must be a FloatingActionButton",
            xml.contains("FloatingActionButton")
        )
    }

    @Test
    fun feedActivity_fab_wiredToPublishBottomSheet() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
        assertTrue(
            "FeedActivity must call PublishBottomSheet.newInstance()",
            source.contains("PublishBottomSheet.newInstance(")
        )
        assertTrue(
            "FeedActivity must show PublishBottomSheet on FAB click",
            source.contains(".show(supportFragmentManager")
        )
    }

    @Test
    fun feedActivity_fab_usesTokenColors() {
        val xml = readLayout("activity_feed.xml")
        assertTrue(
            "FAB backgroundTint must use @color/accent_ui token",
            xml.contains("@color/accent_ui")
        )
    }

    // ── String resources ──────────────────────────────────────────────────

    @Test
    fun strings_allPublishSheetKeysPresent() {
        val xml = readStrings()
        val requiredKeys = listOf(
            "publish_sheet_title",
            "publish_sheet_hint_topic",
            "publish_sheet_hint_title",
            "publish_sheet_hint_message",
            "publish_sheet_hint_tags",
            "publish_sheet_btn_close",
            "publish_sheet_btn_send",
            "publish_sheet_chip_low",
            "publish_sheet_chip_normal",
            "publish_sheet_chip_high",
            "publish_sheet_chip_urgent",
            "publish_sheet_error_send",
        )
        requiredKeys.forEach { key ->
            assertTrue("strings.xml must contain $key", xml.contains(key))
        }
    }

    @Test
    fun strings_publishSheetErrorSend_hasFormatPlaceholder() {
        val xml = readStrings()
        val block = xml.substringAfter("publish_sheet_error_send").substringBefore("</string>")
        assertTrue(
            "publish_sheet_error_send must contain %s format placeholder",
            block.contains("%s")
        )
    }

    @Test
    fun strings_existingPublishDialogKeysUntouched() {
        val xml = readStrings()
        assertTrue("publish_dialog_title must still exist", xml.contains("publish_dialog_title"))
        assertTrue("publish_dialog_docs_text must still exist", xml.contains("publish_dialog_docs_text"))
    }

    // ── Layout file structure ─────────────────────────────────────────────

    @Test
    fun publishSheetLayout_hasAllRequiredFields() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue("Layout must contain publish_sheet_topic", xml.contains("publish_sheet_topic"))
        assertTrue("Layout must contain publish_sheet_title_text", xml.contains("publish_sheet_title_text"))
        assertTrue("Layout must contain publish_sheet_message", xml.contains("publish_sheet_message"))
        assertTrue("Layout must contain publish_sheet_tags", xml.contains("publish_sheet_tags"))
        assertTrue("Layout must contain publish_sheet_priority_chips", xml.contains("publish_sheet_priority_chips"))
        assertTrue("Layout must contain publish_sheet_close", xml.contains("publish_sheet_close"))
        assertTrue("Layout must contain publish_sheet_send", xml.contains("publish_sheet_send"))
        assertTrue("Layout must contain publish_sheet_error", xml.contains("publish_sheet_error"))
    }

    @Test
    fun publishSheetLayout_hasFourPriorityChips() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue("Layout must contain chip_priority_low",    xml.contains("chip_priority_low"))
        assertTrue("Layout must contain chip_priority_normal", xml.contains("chip_priority_normal"))
        assertTrue("Layout must contain chip_priority_high",   xml.contains("chip_priority_high"))
        assertTrue("Layout must contain chip_priority_urgent", xml.contains("chip_priority_urgent"))
    }

    @Test
    fun publishSheetLayout_usesTokenColors() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertFalse("Layout must not contain raw hex colors", xml.contains("#"))
        assertTrue("Layout must use @color/surface_2 for field backgrounds", xml.contains("@color/surface_2"))
        assertTrue("Layout must use @color/control_border for stroke", xml.contains("@color/control_border"))
    }

    @Test
    fun publishSheetLayout_usesTokenRadius() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue("Layout must use @dimen/radius_sm for corner radius", xml.contains("@dimen/radius_sm"))
    }

    @Test
    fun publishSheetLayout_messageField_isMultiLine() {
        val xml = readLayout("fragment_publish_bottom_sheet.xml")
        assertTrue(
            "Message field must use textMultiLine inputType",
            xml.contains("textMultiLine")
        )
    }

    // ── Non-regression: existing publish flow untouched ────────────────────

    @Test
    fun publishFragment_notModified_stillExists() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/PublishFragment.kt")
        assertTrue("PublishFragment.kt must still exist and be non-empty", source.isNotEmpty())
        assertTrue(
            "PublishFragment must still extend DialogFragment",
            source.contains("DialogFragment")
        )
    }

    @Test
    fun fragmentPublishDialog_notModified() {
        val xml = readLayout("fragment_publish_dialog.xml")
        assertTrue("fragment_publish_dialog.xml must still exist and be non-empty", xml.isNotEmpty())
        assertTrue("fragment_publish_dialog.xml must still contain publish_dialog_toolbar",
            xml.contains("publish_dialog_toolbar"))
    }
}
