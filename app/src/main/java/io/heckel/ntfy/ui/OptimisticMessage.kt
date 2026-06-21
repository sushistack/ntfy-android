package io.heckel.ntfy.ui

import java.util.UUID

/** Payload stored inside an OptimisticMessage so retry can re-issue the same HTTP call. */
data class PublishPayload(
    val baseUrl: String,
    val topic: String,
    val message: String,
    val title: String,
    val priority: Int,
    val tags: List<String>,
)

sealed class SendState {
    object Pending : SendState()
    data class Error(val cause: String) : SendState()
}

/** In-memory optimistic message shown at the top of the feed while a publish is in flight. */
data class OptimisticMessage(
    val localId: String,
    val title: String,
    val message: String,
    val priority: Int,
    val tags: List<String>,
    val timestamp: Long,
    val sendState: SendState,
    val payload: PublishPayload,
) {
    companion object {
        fun create(payload: PublishPayload, timestamp: Long): OptimisticMessage =
            OptimisticMessage(
                localId = "local_${UUID.randomUUID()}",
                title = payload.title,
                message = payload.message,
                priority = payload.priority,
                tags = payload.tags,
                timestamp = timestamp,
                sendState = SendState.Pending,
                payload = payload,
            )
    }
}
