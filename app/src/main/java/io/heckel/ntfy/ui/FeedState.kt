package io.heckel.ntfy.ui

/**
 * Represents the four mutually-exclusive display states of the Epic 4 feed.
 * Drive visibility via a single applyFeedState() call — never scatter
 * View.VISIBLE/GONE across multiple observers.
 */
sealed class FeedState {
    object Loading : FeedState()
    data class Empty(val isAllFeed: Boolean) : FeedState()
    data class Disconnected(val isPageLoadFailure: Boolean) : FeedState()
    object HasContent : FeedState()
}
