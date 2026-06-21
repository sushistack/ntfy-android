package io.heckel.ntfy.msg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun `parseWithTopic with sequence_id persists exact server value`() {
        val json = """
            {
              "id": "msg001",
              "time": 1700000000,
              "sequence_id": "opaque-sequence-002",
              "event": "message",
              "topic": "test-topic",
              "message": "hello"
            }
        """.trimIndent()

        val result = parser.parseWithTopic(json, subscriptionId = 1L, baseUrl = "https://ntfy.sh")

        assertNotNull(result)
        assertEquals("opaque-sequence-002", result!!.notification.sequenceId)
        assertEquals("msg001", result.notification.id)
        assertNull(result.notification.serverSequence)
    }

    @Test
    fun `parseWithTopic without sequence_id falls back to message id`() {
        val json = """
            {
              "id": "msg002",
              "time": 1700000001,
              "event": "message",
              "topic": "test-topic",
              "message": "hello without sequence"
            }
        """.trimIndent()

        val result = parser.parseWithTopic(json, subscriptionId = 1L, baseUrl = "https://ntfy.sh")

        assertNotNull(result)
        assertEquals("msg002", result!!.notification.sequenceId)
        assertEquals("msg002", result.notification.id)
        assertNull(result.notification.serverSequence)
    }

    @Test
    fun `parseWithTopic with sequence_id does not use message id as sequenceId`() {
        val json = """
            {
              "id": "msg003",
              "time": 1700000002,
              "sequence_id": "server-seq-99",
              "event": "message",
              "topic": "test-topic",
              "message": "another message"
            }
        """.trimIndent()

        val result = parser.parseWithTopic(json, subscriptionId = 2L, baseUrl = "https://ntfy.sh")

        assertNotNull(result)
        assertEquals("server-seq-99", result!!.notification.sequenceId)
        assert(result.notification.sequenceId != result.notification.id)
        assertNull(result.notification.serverSequence)
    }

    @Test
    fun `parseWithTopic with null-valued sequence_id falls back to message id`() {
        // Gson maps absent fields to null for nullable types; explicit null behaves the same
        val json = """
            {
              "id": "msg004",
              "time": 1700000003,
              "sequence_id": null,
              "event": "message",
              "topic": "test-topic",
              "message": "explicit null sequence"
            }
        """.trimIndent()

        val result = parser.parseWithTopic(json, subscriptionId = 1L, baseUrl = "https://ntfy.sh")

        assertNotNull(result)
        assertEquals("msg004", result!!.notification.sequenceId)
        assertNull(result.notification.serverSequence)
    }

    @Test
    fun `parse returns null for non-message events`() {
        val json = """
            {
              "id": "evt001",
              "time": 1700000004,
              "event": "keepalive",
              "topic": "test-topic"
            }
        """.trimIndent()

        val result = parser.parseWithTopic(json, subscriptionId = 1L, baseUrl = "https://ntfy.sh")

        assertEquals(null, result)
    }

    @Test
    fun `parseWithTopic message_delete event preserves sequenceId`() {
        val json = """
            {
              "id": "msg005",
              "time": 1700000005,
              "sequence_id": "delete-seq-42",
              "event": "message_delete",
              "topic": "test-topic",
              "message": ""
            }
        """.trimIndent()

        val result = parser.parseWithTopic(json, subscriptionId = 1L, baseUrl = "https://ntfy.sh")

        assertNotNull(result)
        assertEquals("delete-seq-42", result!!.notification.sequenceId)
        assertEquals("message_delete", result.notification.event)
        assertNull(result.notification.serverSequence)
    }
}
