package io.heckel.ntfy.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.heckel.ntfy.db.Notification
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class FeedItem(val notification: Notification, val topicName: String?)

/** Sentinel: no subscription filter — show all topics. */
const val ALL_SUBSCRIPTIONS_ID = 0L

/**
 * Signals that a page load attempt failed; Story 4.3 / feed host surfaces the retry UI.
 */
data class PageLoadError(val cause: Throwable)

class FeedViewModel(private val repository: Repository) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 20
        const val SCROLL_THRESHOLD = 3
    }

    // ── Live subscription map for topic-name decoration ────────────────────────
    private val subscriptionsLiveData: LiveData<List<Subscription>> =
        repository.getSubscriptionsLiveData()

    // ── Pagination state ────────────────────────────────────────────────────────
    /** Accumulated list backing the adapter (page-1 live slice + older page appends). */
    private val _feedItems = MutableLiveData<List<FeedItem>>(emptyList())
    val feedItems: LiveData<List<FeedItem>> get() = _feedItems

    /** True while a background page fetch is in flight (guards against double triggers). */
    @Volatile var isLoadingPage: Boolean = false
        private set

    /** Set to false when the last page fetch returned fewer than PAGE_SIZE rows. */
    private var hasMorePages: Boolean = true

    /** Offset for the next discrete page fetch (pages 2+). */
    private var nextOffset: Int = PAGE_SIZE

    /** Which subscription is being shown; ALL_SUBSCRIPTIONS_ID for All-feed. */
    private var activeSubscriptionId: Long = ALL_SUBSCRIPTIONS_ID

    // ── Arrival detection state ─────────────────────────────────────────────────
    /**
     * IDs known to be in the live-Flow window before the latest emission.
     * Empty on first emission → initial load produces no arrivals.
     */
    @Volatile private var knownIds: Set<String> = emptySet()

    /**
     * IDs that arrived while the feed was open (genuine live arrivals).
     * Consumed exactly once per ID by the adapter via [CardEffect.NewArrival].
     */
    private val _newlyArrivedIds = MutableLiveData<Set<String>>(emptySet())
    val newlyArrivedIds: LiveData<Set<String>> get() = _newlyArrivedIds

    /** Page-load failure observable; null when no error is pending. */
    private val _pageLoadError = MutableLiveData<PageLoadError?>(null)
    val pageLoadError: LiveData<PageLoadError?> get() = _pageLoadError

    // ── Subscription map (for topic-name decoration) ────────────────────────────
    private var subscriptionMap: Map<Long, String> = emptyMap()

    private val subscriptionObserver = Observer<List<Subscription>> { list ->
        subscriptionMap = list.orEmpty().associate { it.id to it.topic }
        val current = _feedItems.value ?: emptyList()
        if (current.isNotEmpty()) {
            _feedItems.value = current.map { item ->
                item.copy(topicName = subscriptionMap[item.notification.subscriptionId])
            }
        }
    }

    init {
        subscriptionsLiveData.observeForever(subscriptionObserver)
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Starts observing the live Room Flow for the given subscription (or all).
     * Must be called once from the Activity/Fragment after ViewModel is created.
     * Returns a [LiveData] of the first page (newest PAGE_SIZE items) that updates
     * in real-time as new messages arrive.
     */
    fun observeLivePage(subscriptionId: Long): LiveData<List<Notification>> {
        activeSubscriptionId = subscriptionId
        return if (subscriptionId == ALL_SUBSCRIPTIONS_ID) {
            repository.getAllNotificationsLiveData()
        } else {
            repository.getNotificationsLiveData(subscriptionId)
        }
    }

    /**
     * Called by the Activity whenever the live Room Flow emits a new list.
     * Handles arrival detection and merges the live first-page slice with any
     * already-loaded older pages.
     */
    fun onLivePageUpdate(livePage: List<Notification>) {
        val incomingIds = livePage.map { it.id }.toSet()

        val arrivals: Set<String> = if (knownIds.isEmpty()) {
            // First emission = initial load, not arrivals
            emptySet()
        } else {
            incomingIds - knownIds
        }
        knownIds = incomingIds

        // Merge: live page (newest) + already-loaded older pages
        val olderPages = (_feedItems.value ?: emptyList())
            .drop(minOf(livePage.size, _feedItems.value?.size ?: 0))
            .filter { item -> item.notification.id !in incomingIds }

        val decoratedPage = livePage.map { n ->
            FeedItem(n, subscriptionMap[n.subscriptionId])
        }
        _feedItems.value = decoratedPage + olderPages

        // Accumulate new arrivals (don't reset existing ones not yet consumed)
        if (arrivals.isNotEmpty()) {
            val current = _newlyArrivedIds.value ?: emptySet()
            _newlyArrivedIds.value = current + arrivals
        }
    }

    /**
     * Triggered by the scroll listener when the user approaches the end of the list.
     * Appends the next page of older messages to [feedItems].
     */
    fun loadNextPage() {
        if (isLoadingPage || !hasMorePages) return
        isLoadingPage = true
        _pageLoadError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = if (activeSubscriptionId == ALL_SUBSCRIPTIONS_ID) {
                    repository.getAllNotificationsPaged(PAGE_SIZE, nextOffset)
                } else {
                    repository.getNotificationsPaged(activeSubscriptionId, PAGE_SIZE, nextOffset)
                }

                val knownSnapshot = knownIds
                val newItems = results
                    .filter { it.id !in knownSnapshot }
                    .map { n -> FeedItem(n, subscriptionMap[n.subscriptionId]) }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (results.isEmpty()) {
                        hasMorePages = false
                    } else {
                        _feedItems.value = (_feedItems.value ?: emptyList()) + newItems
                        nextOffset += results.size
                        if (results.size < PAGE_SIZE) hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _pageLoadError.value = PageLoadError(e)
                }
            } finally {
                isLoadingPage = false
            }
        }
    }

    /**
     * Called by the adapter's [CardEffect.NewArrival.consumed] callback once
     * the binder has started the slide-in animation for [id].
     */
    fun consumeArrivedId(id: String) {
        val current = _newlyArrivedIds.value ?: return
        if (id in current) {
            _newlyArrivedIds.value = current - id
        }
    }

    fun markAsDeleted(notificationId: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.markAsDeleted(notificationId)
    }

    // ── Legacy single-LiveData API (kept for backwards compat with any callers) ─

    /** @deprecated Use [observeLivePage] + [onLivePageUpdate] + [feedItems] instead. */
    fun listAll(): LiveData<List<FeedItem>> {
        val result = MediatorLiveData<List<FeedItem>>()
        val notifications = repository.getAllNotificationsLiveData()
        var currentNotifications: List<Notification> = emptyList()
        var currentSubscriptionMap: Map<Long, String> = emptyMap()
        fun merge() {
            val map = currentSubscriptionMap
            result.value = currentNotifications.map { n -> FeedItem(n, map[n.subscriptionId]) }
        }
        result.addSource(notifications) { list ->
            currentNotifications = list ?: emptyList(); merge()
        }
        result.addSource(subscriptionsLiveData) { list ->
            currentSubscriptionMap = list.orEmpty().associate { it.id to it.topic }; merge()
        }
        return result
    }

    /** @deprecated Use [observeLivePage] + [onLivePageUpdate] + [feedItems] instead. */
    fun listForSubscription(subscriptionId: Long): LiveData<List<FeedItem>> {
        val result = MediatorLiveData<List<FeedItem>>()
        result.addSource(repository.getNotificationsLiveData(subscriptionId)) { list ->
            result.value = list.orEmpty().map { n -> FeedItem(n, null) }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionsLiveData.removeObserver(subscriptionObserver)
        knownIds = emptySet()
    }
}

class FeedViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(FeedViewModel::class.java) -> FeedViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown viewModel class $modelClass")
        }
}
