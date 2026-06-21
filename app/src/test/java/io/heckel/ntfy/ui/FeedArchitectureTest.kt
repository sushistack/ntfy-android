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

    // Feed UI logic now lives in FeedFragment (FeedActivity is a thin host); deep-link
    // guards assert against the fragment.
    private val feedFragmentSource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/FeedFragment.kt")
    }

    private val feedAdapterSource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt")
    }

    @Test
    fun feed_doesNotReferenceDetailActivity() {
        assertFalse(
            "FeedActivity must not reference DetailActivity",
            feedActivitySource.contains("DetailActivity")
        )
        assertFalse(
            "FeedFragment must not reference DetailActivity",
            feedFragmentSource.contains("DetailActivity")
        )
    }

    @Test
    fun feed_doesNotCallStartActivityForNotificationTap() {
        // The feed surface performs no intent navigation on notification taps (it scrolls/marks read).
        assertFalse(
            "FeedFragment must not call startActivity for notification taps",
            feedFragmentSource.contains("startActivity(")
        )
    }

    @Test
    fun feed_handlesDeeepLinkViaSmoothScrollToPosition() {
        assertTrue(
            "FeedFragment must call smoothScrollToPosition for deep-link scroll",
            feedFragmentSource.contains("smoothScrollToPosition")
        )
    }

    @Test
    fun feed_consumesDeepLinkIdAfterScroll() {
        assertTrue(
            "FeedFragment must mark deep-link as consumed after first scroll",
            feedFragmentSource.contains("deepLinkConsumed")
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
    fun feedViewModel_exposesFeedItemSealedClass() {
        val vmSource = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt")
        // Story 4.9: FeedItem is now a sealed class with Server and Optimistic subtypes.
        assertTrue("FeedItem sealed class must exist in FeedViewModel file", vmSource.contains("sealed class FeedItem"))
        assertTrue("FeedItem.Server must carry topicName field", vmSource.contains("topicName"))
        assertTrue("FeedItem.Optimistic subtype must exist", vmSource.contains("Optimistic"))
    }
}
