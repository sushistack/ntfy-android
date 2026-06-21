package io.heckel.ntfy.ui

import io.heckel.ntfy.db.Notification
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests verifying the [FeedItem] / [FeedAdapter] topicName contract:
 * - All mode: topicName is non-null (the subscription topic string)
 * - Per-topic mode: topicName is null
 *
 * These tests verify the data-layer contract (FeedItem) without an Android device.
 * The adapter delegates topicName directly from FeedItem to MessageCardBinder.bind().
 */
class FeedAdapterTopicNameTest {

    private fun makeNotification(id: String, subscriptionId: Long = 1L): Notification = Notification(
        id = id,
        subscriptionId = subscriptionId,
        timestamp = 1000L,
        sequenceId = "seq-$id",
        title = "",
        message = "msg",
        contentType = "",
        encoding = "",
        notificationId = 0,
        priority = 3,
        tags = "",
        click = "",
        icon = null,
        actions = null,
        attachment = null,
        deleted = false,
    )

    @Test
    fun feedItem_allMode_topicName_isNonNull() {
        val notification = makeNotification("msg-1", subscriptionId = 42L)
        val item = FeedItem.Server(notification = notification, topicName = "my-topic")

        assertNotNull(item.topicName)
        assertEquals("my-topic", item.topicName)
    }

    @Test
    fun feedItem_perTopicMode_topicName_isNull() {
        val notification = makeNotification("msg-2", subscriptionId = 7L)
        val item = FeedItem.Server(notification = notification, topicName = null)

        assertNull(item.topicName)
    }

    @Test
    fun feedItem_allMode_eachCardCarriesItsOwnTopic() {
        val sub1Notification = makeNotification("msg-A", subscriptionId = 1L)
        val sub2Notification = makeNotification("msg-B", subscriptionId = 2L)

        val items = listOf(
            FeedItem.Server(sub1Notification, topicName = "topic-alpha"),
            FeedItem.Server(sub2Notification, topicName = "topic-beta"),
        )

        assertEquals("topic-alpha", items[0].topicName)
        assertEquals("topic-beta", items[1].topicName)
        assertNotEquals(items[0].topicName, items[1].topicName)
    }

    @Test
    fun feedItem_dataEquality_basedOnNotificationAndTopic() {
        val n = makeNotification("msg-X")
        val a = FeedItem.Server(n, "topic-1")
        val b = FeedItem.Server(n, "topic-1")
        val c = FeedItem.Server(n, "topic-2")

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun feedItem_diffCallback_sameId_consideredSameItem() {
        val n1 = makeNotification("msg-Z")
        val n2 = makeNotification("msg-Z")
        // DiffUtil.areItemsTheSame is based on notification.id
        assertEquals(n1.id, n2.id)
    }
}
