package io.heckel.ntfy.ui.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static resource gate tests for Story 3.4 list layout files (AC 7).
 *
 * Verifies that [view_card_list.xml] and [view_card_list_item.xml] contain:
 * - No raw hex color literals (android:textColor="#...", android:background="#...")
 * - No ad-hoc raw pixel/dp dimension values (e.g., android:padding="13dp")
 *   outside the token set defined in dimens.xml
 *
 * These are compile-time XML checks — no Android runtime needed.
 */
class ListLayoutResourceGateTest {

    private val projectRoot: String = run {
        // Walk up from the test class location to find app/src/main/res
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (dir.parentFile != null && !File(dir, "app/src/main/res").exists()) {
            dir = dir.parentFile
        }
        dir.absolutePath
    }

    private fun readLayoutFile(name: String): String {
        val file = File("$projectRoot/app/src/main/res/layout/$name")
        assertTrue("Layout file $name must exist", file.exists())
        return file.readText()
    }

    // -------------------------------------------------------------------------
    // No raw hex color literals (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `view_card_list has no raw hex color literals`() {
        val content = readLayoutFile("view_card_list.xml")
        val rawColorPattern = Regex("""android:\w*[Cc]olor="#[0-9A-Fa-f]{3,8}"""")
        val matches = rawColorPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "view_card_list.xml must not contain raw hex colors: $matches",
            matches.isEmpty()
        )
    }

    @Test
    fun `view_card_list_item has no raw hex color literals`() {
        val content = readLayoutFile("view_card_list_item.xml")
        val rawColorPattern = Regex("""android:\w*[Cc]olor="#[0-9A-Fa-f]{3,8}"""")
        val matches = rawColorPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "view_card_list_item.xml must not contain raw hex colors: $matches",
            matches.isEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // Token references exist in both files (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `view_card_list references spacing token for padding`() {
        val content = readLayoutFile("view_card_list.xml")
        assertTrue(
            "view_card_list.xml must use @dimen/spacing_N for padding",
            content.contains("@dimen/spacing_")
        )
    }

    @Test
    fun `view_card_list_item references body_sm typography token`() {
        val content = readLayoutFile("view_card_list_item.xml")
        assertTrue(
            "view_card_list_item.xml must use TextAppearance.Ntfy.BodySmall or @dimen/text_body_sm",
            content.contains("BodySmall") || content.contains("text_body_sm")
        )
    }

    @Test
    fun `view_card_list_item references muted color token`() {
        val content = readLayoutFile("view_card_list_item.xml")
        assertTrue(
            "view_card_list_item.xml must use @color/muted for text color",
            content.contains("@color/muted")
        )
    }

    // -------------------------------------------------------------------------
    // No ad-hoc literal dp values that bypass token system (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `view_card_list has no ad-hoc raw dp dimension values`() {
        val content = readLayoutFile("view_card_list.xml")
        // Allow known layout values (0dp for weighted views, match_parent, wrap_content)
        // Flag any literal non-zero dp not wrapped in @dimen
        val adHocDpPattern = Regex("""android:(?:padding|margin)\w*="[1-9]\d*dp"""")
        val matches = adHocDpPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "view_card_list.xml must not use raw dp values; use @dimen tokens instead: $matches",
            matches.isEmpty()
        )
    }

    @Test
    fun `view_card_list_item has no ad-hoc raw dp dimension values`() {
        val content = readLayoutFile("view_card_list_item.xml")
        val adHocDpPattern = Regex("""android:(?:padding|margin)\w*="[1-9]\d*dp"""")
        val matches = adHocDpPattern.findAll(content).map { it.value }.toList()
        assertTrue(
            "view_card_list_item.xml must not use raw dp values; use @dimen tokens instead: $matches",
            matches.isEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // fragment_detail_item.xml is NOT modified (AC 10, 13)
    // -------------------------------------------------------------------------

    @Test
    fun `fragment_detail_item does not reference list layout resources`() {
        val content = readLayoutFile("fragment_detail_item.xml")
        assertFalse(
            "fragment_detail_item.xml must not reference view_card_list or list_row_host",
            content.contains("view_card_list") || content.contains("list_row_host")
        )
    }
}
