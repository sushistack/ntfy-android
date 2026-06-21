package io.heckel.ntfy.ui

import io.heckel.ntfy.ui.CardTagFormatter.categorize
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Contract tests for Story 4.4: All-vs-per-topic topic chip visibility.
 *
 * The chip VISIBLE/GONE decision is driven entirely by whether topicName is non-null
 * in [CardTagFormatter.CardTags.topic]. These tests verify:
 *   1. Non-null topicName → CardTags.topic is non-null → binder adds the chip (VISIBLE)
 *   2. Null topicName    → CardTags.topic is null     → binder skips the chip (GONE)
 *   3. DetailAdapter always passes null (per-topic regression guard)
 *   4. FeedAdapter delegates topicName from FeedItem without mode-detection logic
 */
class TopicChipVisibilityContractTest {

    // ── CardTagFormatter chip-guard contract ──────────────────────────────────

    @Test
    fun nonNullTopicName_producesNonNullTopic_chipIsVisible() {
        val tags = categorize(rawTags = null, topicName = "server-alerts")
        assertNotNull(
            "Non-null topicName must produce non-null CardTags.topic (chip VISIBLE)",
            tags.topic
        )
        assertEquals("server-alerts", tags.topic)
    }

    @Test
    fun nullTopicName_producesNullTopic_chipIsGone() {
        val tags = categorize(rawTags = null, topicName = null)
        assertNull(
            "Null topicName must produce null CardTags.topic (chip GONE)",
            tags.topic
        )
    }

    @Test
    fun blankTopicName_treatedAsNoChip() {
        val tags = categorize(rawTags = null, topicName = "   ")
        // isNullOrBlank() guard in renderMetaRow: blank → no chip
        assertTrue(
            "Blank topicName must not produce a non-blank CardTags.topic",
            tags.topic.isNullOrBlank()
        )
    }

    @Test
    fun topicNamePreservedVerbatim() {
        val name = "my-alerts/prod"
        // isEmoji = { false } avoids Android EmojiManager (not available in JVM tests)
        val tags = categorize(rawTags = "warning,service:db", topicName = name, isEmoji = { false })
        assertEquals(
            "CardTags.topic must equal topicName verbatim",
            name,
            tags.topic
        )
    }

    @Test
    fun topicNameNotSourcedFromRawTags() {
        // Even if a raw tag value matches a topic name, topic comes only from the parameter
        // isEmoji = { false } avoids Android EmojiManager (not available in JVM tests)
        val tags = categorize(rawTags = "server-alerts", topicName = null, isEmoji = { false })
        assertNull(
            "CardTags.topic must be null when topicName=null, regardless of raw tags",
            tags.topic
        )
        assertEquals(1, tags.general.size)
        assertEquals("server-alerts", tags.general[0].name)
    }

    // ── DetailAdapter source-level null contract ──────────────────────────────

    private fun readSource(relativePath: String): String {
        val candidates = listOf(relativePath, "../$relativePath")
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("Source not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    @Test
    fun detailAdapter_alwaysPassesNullTopicName() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt")
        assertTrue(
            "DetailAdapter must explicitly pass topicName = null to binder",
            source.contains("topicName = null")
        )
    }

    @Test
    fun detailAdapter_doesNotPassNonNullTopicName() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/DetailAdapter.kt")
        assertFalse(
            "DetailAdapter must not pass a non-null topicName (per-topic feed never shows topic chip)",
            source.contains("topicName = item.topicName") || source.contains("topicName = subscription")
        )
    }

    // ── FeedAdapter source-level delegation contract ──────────────────────────

    @Test
    fun feedAdapter_delegatesTopicNameFromFeedItem() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt")
        assertTrue(
            "FeedAdapter must pass item.topicName to binder (All-feed path)",
            source.contains("topicName = item.topicName")
        )
    }

    @Test
    fun feedAdapter_containsNoModeDetectionLogic() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt")
        assertFalse(
            "FeedAdapter must not contain mode-detection (subscriptionId lookup, ViewModel reference)",
            source.contains("repository.getSubscription") ||
                source.contains("subscriptionId ==") ||
                source.contains("ALL_SUBSCRIPTIONS_ID")
        )
    }

    @Test
    fun feedViewModel_allMode_passesNonNullTopicName() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt")
        assertTrue(
            "FeedViewModel All-mode path must resolve topicName from subscriptionMap",
            source.contains("subscriptionMap[n.subscriptionId]") ||
                source.contains("subscriptionMap[item.notification.subscriptionId]")
        )
    }

    @Test
    fun feedViewModel_perTopicMode_passesNullTopicName() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedViewModel.kt")
        assertTrue(
            "FeedViewModel per-topic mode must pass null topicName to FeedItem",
            source.contains("FeedItem(n, null)") || source.contains("FeedItem(notification, null)")
        )
    }

    // ── No sticky header guard ────────────────────────────────────────────────

    @Test
    fun feedActivity_noStickyHeaderDecoration() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedActivity.kt")
        assertFalse(
            "FeedActivity must not use a sticky-header ItemDecoration",
            source.contains("StickyHeader") || source.contains("StickyHeaderDecoration")
        )
    }

    @Test
    fun feedAdapter_noHeaderViewType() {
        val source = readSource("app/src/main/java/io/heckel/ntfy/ui/FeedAdapter.kt")
        assertFalse(
            "FeedAdapter must not define a header viewType",
            source.contains("VIEW_TYPE_HEADER") || source.contains("viewType.*header".toRegex())
        )
        assertFalse(
            "FeedAdapter must not use ConcatAdapter for a header",
            source.contains("ConcatAdapter")
        )
    }
}
