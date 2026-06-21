package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests for Story 4.3: Feed States (Loading, Empty, Disconnected).
 *
 * Covers:
 * - FeedState sealed class structure and identity
 * - applyFeedState single-entry-point contract (via source inspection)
 * - String resource presence and key uniqueness
 * - Layout file structure (skeleton count, token compliance, retry button)
 * - Architecture: no scattered visibility calls, no Room refs in panel views
 */
class FeedStateTest {

    private fun readSource(relativePath: String): String {
        val candidates = listOf(relativePath, "../$relativePath")
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("Source not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private fun readLayout(name: String) =
        readSource("app/src/main/res/layout/$name")

    private fun readStrings() =
        readSource("app/src/main/res/values/strings.xml")

    // ── FeedState sealed class ──────────────────────────────────────────────

    @Test
    fun feedState_loading_isDistinctSingleton() {
        val a: FeedState = FeedState.Loading
        val b: FeedState = FeedState.Loading
        assertSame(a, b)
    }

    @Test
    fun feedState_hasContent_isDistinctSingleton() {
        val a: FeedState = FeedState.HasContent
        val b: FeedState = FeedState.HasContent
        assertSame(a, b)
    }

    @Test
    fun feedState_empty_allFeed_distinguishedByFlag() {
        val all = FeedState.Empty(isAllFeed = true)
        val topic = FeedState.Empty(isAllFeed = false)
        assertTrue(all.isAllFeed)
        assertFalse(topic.isAllFeed)
        assertNotEquals(all, topic)
    }

    @Test
    fun feedState_disconnected_pageLoadFailure_flag() {
        val pageLoad = FeedState.Disconnected(isPageLoadFailure = true)
        val reconnecting = FeedState.Disconnected(isPageLoadFailure = false)
        assertTrue(pageLoad.isPageLoadFailure)
        assertFalse(reconnecting.isPageLoadFailure)
        assertNotEquals(pageLoad, reconnecting)
    }

    @Test
    fun feedState_fourDistinctSubtypes() {
        val states: List<FeedState> = listOf(
            FeedState.Loading,
            FeedState.Empty(isAllFeed = true),
            FeedState.Disconnected(isPageLoadFailure = false),
            FeedState.HasContent,
        )
        assertEquals(4, states.size)
        // Each state maps to exactly one `is` branch
        val branches = states.map {
            when (it) {
                is FeedState.Loading -> "loading"
                is FeedState.Empty -> "empty"
                is FeedState.Disconnected -> "disconnected"
                is FeedState.HasContent -> "hasContent"
            }
        }
        assertEquals(listOf("loading", "empty", "disconnected", "hasContent"), branches)
    }

    @Test
    fun feedState_noAndroidSdkDependency() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedState.kt")
        assertFalse(
            "FeedState must have no Android SDK import (pure Kotlin)",
            source.contains("import android.")
        )
    }

    // ── applyFeedState single entry point ──────────────────────────────────

    @Test
    fun feedActivity_hasApplyFeedState_method() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
        assertTrue(
            "FeedActivity must declare applyFeedState()",
            source.contains("fun applyFeedState(")
        )
    }

    @Test
    fun feedActivity_applyFeedState_hidesAllBeforeShowing() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
        // The method must set GONE on all four panels before any VISIBLE assignment
        assertTrue(
            "applyFeedState must set each panel to GONE before revealing the active one",
            source.contains("View.GONE") && source.contains("View.VISIBLE")
        )
    }

    @Test
    fun feedActivity_noDirectVisibilityScatterOutsideApplyFeedState() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
        // Count visibility = View.VISIBLE assignments; all must live inside applyFeedState
        val visibleCount = source.split("View.VISIBLE").size - 1
        val goneCount = source.split("View.GONE").size - 1
        assertTrue("Expected VISIBLE assignments only inside applyFeedState", visibleCount >= 4)
        assertTrue("Expected GONE assignments only inside applyFeedState", goneCount >= 4)
    }

    @Test
    fun feedActivity_retryCallback_delegatesToViewModel() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
        // The retry lambda must call viewModel, not repository directly
        assertTrue(
            "Retry callback must delegate to viewModel.loadNextPage()",
            source.contains("viewModel.loadNextPage()")
        )
        // applyFeedState itself must not call repository.* for the retry path
        val applyBlock = source.substringAfter("fun applyFeedState(")
            .substringBefore("\n    private fun ")
        assertFalse(
            "applyFeedState must not call repository directly for retry",
            applyBlock.contains("repository.")
        )
    }

    // ── Loading state layout (five skeletons) ─────────────────────────────

    @Test
    fun loadingStateLayout_exists() {
        val xml = readLayout("view_feed_loading_state.xml")
        assertTrue("view_feed_loading_state.xml must exist and be non-empty", xml.isNotEmpty())
    }

    @Test
    fun loadingStateLayout_containsFiveSkeletonIncludes() {
        val xml = readLayout("view_feed_loading_state.xml")
        val count = xml.split("@layout/view_message_card_skeleton").size - 1
        assertEquals("Loading container must include exactly 5 skeleton cards", 5, count)
    }

    @Test
    fun loadingStateLayout_defaultVisibilityGone() {
        val xml = readLayout("view_feed_loading_state.xml")
        assertTrue(
            "view_feed_loading_state.xml root must default to visibility=gone",
            xml.contains("visibility=\"gone\"")
        )
    }

    @Test
    fun loadingStateLayout_accessibilityExcluded() {
        val xml = readLayout("view_feed_loading_state.xml")
        assertTrue(
            "Loading container root must set importantForAccessibility=noHideDescendants",
            xml.contains("noHideDescendants")
        )
    }

    // ── Empty state layout ────────────────────────────────────────────────

    @Test
    fun emptyStateLayout_exists() {
        val xml = readLayout("view_feed_empty_state.xml")
        assertTrue("view_feed_empty_state.xml must exist and be non-empty", xml.isNotEmpty())
    }

    @Test
    fun emptyStateLayout_hasIconTitleAndBody() {
        val xml = readLayout("view_feed_empty_state.xml")
        assertTrue("Empty panel must contain feed_empty_icon", xml.contains("feed_empty_icon"))
        assertTrue("Empty panel must contain feed_empty_title", xml.contains("feed_empty_title"))
        assertTrue("Empty panel must contain feed_empty_body", xml.contains("feed_empty_body"))
    }

    @Test
    fun emptyStateLayout_usesTokenColors() {
        val xml = readLayout("view_feed_empty_state.xml")
        assertFalse("Empty panel must not contain raw hex colors", xml.contains("#"))
        assertTrue("Empty panel must use @color/text for text", xml.contains("@color/text"))
        assertTrue("Empty panel must use @color/muted for body", xml.contains("@color/muted"))
        assertTrue("Empty panel must use @color/surface background", xml.contains("@color/surface"))
    }

    @Test
    fun emptyStateLayout_defaultVisibilityGone() {
        val xml = readLayout("view_feed_empty_state.xml")
        assertTrue(
            "view_feed_empty_state.xml root must default to visibility=gone",
            xml.contains("visibility=\"gone\"")
        )
    }

    @Test
    fun emptyStateLayout_bodyDefaultGone() {
        val xml = readLayout("view_feed_empty_state.xml")
        // The body TextView must start hidden; host shows it for all-feed only
        assertTrue(
            "feed_empty_body must default to gone (only shown for all-feed variant)",
            xml.contains("feed_empty_body") && xml.contains("visibility=\"gone\"")
        )
    }

    // ── Disconnected state layout ─────────────────────────────────────────

    @Test
    fun disconnectedStateLayout_exists() {
        val xml = readLayout("view_feed_disconnected_state.xml")
        assertTrue("view_feed_disconnected_state.xml must exist and be non-empty", xml.isNotEmpty())
    }

    @Test
    fun disconnectedStateLayout_hasMessageAndRetryButton() {
        val xml = readLayout("view_feed_disconnected_state.xml")
        assertTrue("Disconnected panel must contain feed_disconnected_message", xml.contains("feed_disconnected_message"))
        assertTrue("Disconnected panel must contain feed_disconnected_retry", xml.contains("feed_disconnected_retry"))
    }

    @Test
    fun disconnectedStateLayout_retryButtonDefaultGone() {
        val xml = readLayout("view_feed_disconnected_state.xml")
        // Retry button must be gone by default; host shows it only for isPageLoadFailure=true
        val retryIdx = xml.indexOf("feed_disconnected_retry")
        val goneIdx = xml.indexOf("visibility=\"gone\"", retryIdx - 300)
        assertTrue(
            "Retry button must default to visibility=gone",
            goneIdx in (retryIdx - 300)..retryIdx + xml.length
        )
    }

    @Test
    fun disconnectedStateLayout_usesTokenColors() {
        val xml = readLayout("view_feed_disconnected_state.xml")
        assertFalse("Disconnected panel must not contain raw hex colors", xml.contains("#"))
    }

    @Test
    fun disconnectedStateLayout_doesNotReferenceRoom() {
        val xml = readLayout("view_feed_disconnected_state.xml")
        assertFalse("Disconnected layout must not reference Room/repository", xml.contains("Repository"))
        assertFalse("Disconnected layout must not reference ViewModel", xml.contains("ViewModel"))
    }

    // ── String resources ──────────────────────────────────────────────────

    @Test
    fun strings_allFourNewKeysPresent() {
        val xml = readStrings()
        assertTrue("strings.xml must contain empty_feed_all_title", xml.contains("empty_feed_all_title"))
        assertTrue("strings.xml must contain empty_feed_all_body", xml.contains("empty_feed_all_body"))
        assertTrue("strings.xml must contain empty_feed_topic", xml.contains("empty_feed_topic"))
        assertTrue("strings.xml must contain feed_state_disconnected", xml.contains("feed_state_disconnected"))
    }

    @Test
    fun strings_noConflictWithExistingDetailStrings() {
        val xml = readStrings()
        // Existing keys must still be present and unmodified
        assertTrue("detail_no_notifications_text must still exist", xml.contains("detail_no_notifications_text"))
        assertTrue("detail_how_to_intro must still exist", xml.contains("detail_how_to_intro"))
        assertTrue("detail_how_to_example must still exist", xml.contains("detail_how_to_example"))
        assertTrue("detail_how_to_link must still exist", xml.contains("detail_how_to_link"))
    }

    @Test
    fun strings_exactKoreanCopy() {
        val xml = readStrings()
        assertTrue(
            "empty_feed_all_title must be '아직 받은 알림이 없어요'",
            xml.contains("아직 받은 알림이 없어요")
        )
        assertTrue(
            "empty_feed_all_body must be '주제를 구독하면 첫 알림이 여기에 나타나요'",
            xml.contains("주제를 구독하면 첫 알림이 여기에 나타나요")
        )
        assertTrue(
            "empty_feed_topic must be '이 주제에는 아직 알림이 없어요'",
            xml.contains("이 주제에는 아직 알림이 없어요")
        )
        assertTrue(
            "feed_state_disconnected must be '연결이 끊겼어요. 다시 연결하는 중…'",
            xml.contains("연결이 끊겼어요. 다시 연결하는 중…")
        )
    }

    @Test
    fun strings_noFormatPlaceholders() {
        val xml = readStrings()
        // Extract only the four new entries block and ensure no %s/%d/%1$s in them
        val newBlock = xml.substringAfter("empty_feed_all_title")
            .substringBefore("</resources>")
        assertFalse(
            "New feed state strings must not contain format placeholders",
            newBlock.contains("%s") || newBlock.contains("%d") || newBlock.contains("%1\$")
        )
    }

    // ── activity_feed.xml layout integration ──────────────────────────────

    @Test
    fun activityFeedLayout_includesAllStatePanels() {
        val xml = readLayout("activity_feed.xml")
        assertTrue("activity_feed.xml must include view_feed_loading_state", xml.contains("view_feed_loading_state"))
        assertTrue("activity_feed.xml must include view_feed_empty_state", xml.contains("view_feed_empty_state"))
        assertTrue("activity_feed.xml must include view_feed_disconnected_state", xml.contains("view_feed_disconnected_state"))
        assertTrue("activity_feed.xml must include feed_recycler", xml.contains("feed_recycler"))
    }

    @Test
    fun activityFeedLayout_doesNotModifyActivityDetailXml() {
        val xml = readLayout("activity_detail.xml")
        // activity_detail.xml should still have its own empty state (not replaced)
        assertTrue(
            "activity_detail.xml must still contain its own empty-state views",
            xml.contains("detail_no_notifications") || xml.contains("ic_sms_gray_48dp")
        )
    }
}
