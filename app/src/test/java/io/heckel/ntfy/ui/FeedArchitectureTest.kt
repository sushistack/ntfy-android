package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Architecture guard tests for FeedActivity and FeedAdapter (Story 4.1).
 *
 * Verifies:
 * - FeedActivity does not reference DetailActivity (no startActivity(DetailActivity))
 * - FeedAdapter inflates fragment_detail_item (the Epic 2 card layout)
 * - FeedAdapter delegates bind to MessageCardBinder (does not re-implement binding)
 * - FeedActivity handles deep-link via smoothScrollToPosition
 */
class FeedArchitectureTest {

    private fun readSource(relativePath: String): String {
        val candidates = listOf(relativePath, "../$relativePath")
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("Source not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private val feedActivitySource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
    }

    private val feedAdapterSource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt")
    }

    @Test
    fun feedActivity_doesNotReferenceDetailActivity() {
        assertFalse(
            "FeedActivity must not reference DetailActivity",
            feedActivitySource.contains("DetailActivity")
        )
    }

    @Test
    fun feedActivity_doesNotCallStartActivityForNotificationTap() {
        // startActivity is only forbidden in the tap-path; a generic check is sufficient
        // because FeedActivity has no intent navigation at all in Story 4.1
        assertFalse(
            "FeedActivity must not call startActivity for notification taps",
            feedActivitySource.contains("startActivity(")
        )
    }

    @Test
    fun feedActivity_handlesDeeepLinkViaSmoothScrollToPosition() {
        assertTrue(
            "FeedActivity must call smoothScrollToPosition for deep-link scroll",
            feedActivitySource.contains("smoothScrollToPosition")
        )
    }

    @Test
    fun feedActivity_consumesDeepLinkIdAfterScroll() {
        assertTrue(
            "FeedActivity must mark deep-link as consumed after first scroll",
            feedActivitySource.contains("deepLinkConsumed")
        )
    }

    @Test
    fun feedAdapter_inflatesFragmentDetailItemLayout() {
        assertTrue(
            "FeedAdapter must inflate fragment_detail_item (Epic 2 card layout)",
            feedAdapterSource.contains("fragment_detail_item")
        )
    }

    @Test
    fun feedAdapter_delegatesToMessageCardBinder() {
        assertTrue(
            "FeedAdapter must use MessageCardBinder for binding",
            feedAdapterSource.contains("MessageCardBinder")
        )
        assertFalse(
            "FeedAdapter must not re-implement card binding logic (no inline tag/priority rendering)",
            feedAdapterSource.contains("renderHeader") || feedAdapterSource.contains("renderPriority")
        )
    }

    @Test
    fun feedViewModel_exposesListAll_and_listForSubscription() {
        val vmSource = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt")
        assertTrue("FeedViewModel must expose listAll()", vmSource.contains("fun listAll()"))
        assertTrue("FeedViewModel must expose listForSubscription(", vmSource.contains("fun listForSubscription("))
    }

    @Test
    fun feedViewModel_exposesFeedItemDataClass() {
        val vmSource = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt")
        assertTrue("FeedItem data class must exist in FeedViewModel file", vmSource.contains("data class FeedItem"))
        assertTrue("FeedItem must carry topicName field", vmSource.contains("topicName"))
    }
}
