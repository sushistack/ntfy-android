package io.heckel.ntfy.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.R
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.db.Notification
import io.heckel.ntfy.ui.accessibility.ArrivalAnnouncer
import io.heckel.ntfy.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedActivity : AppCompatActivity() {

    private val viewModel by viewModels<FeedViewModel> {
        FeedViewModelFactory((application as Application).repository)
    }
    private val repository by lazy { (application as Application).repository }

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var disconnectedContainer: View
    private lateinit var disconnectedRetry: Button
    private lateinit var adapter: FeedAdapter

    private var subscriptionId: Long = ALL_SUBSCRIPTIONS_ID
    private var subscriptionTopic: String? = null
    private var deepLinkNotificationId: String? = null
    private var deepLinkConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)

        subscriptionId = intent.getLongExtra(EXTRA_SUBSCRIPTION_ID, ALL_SUBSCRIPTIONS_ID)
        subscriptionTopic = intent.getStringExtra(EXTRA_SUBSCRIPTION_TOPIC)
        deepLinkNotificationId = intent.getStringExtra(EXTRA_DEEP_LINK_NOTIFICATION_ID)
        deepLinkConsumed = false

        recyclerView = findViewById(R.id.feed_recycler)
        loadingContainer = findViewById(R.id.feed_loading_container)
        emptyContainer = findViewById(R.id.feed_empty_container)
        emptyTitle = emptyContainer.findViewById(R.id.feed_empty_title)
        emptyBody = emptyContainer.findViewById(R.id.feed_empty_body)
        disconnectedContainer = findViewById(R.id.feed_disconnected_container)
        disconnectedRetry = disconnectedContainer.findViewById(R.id.feed_disconnected_retry)

        applyFeedState(FeedState.Loading)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos != 0) outRect.top = resources.getDimensionPixelSize(R.dimen.spacing_4)
            }
        })

        adapter = FeedAdapter(
            activity = this,
            lifecycleScope = lifecycleScope,
            repository = repository,
            onDeleteRequestCallback = { notification -> viewModel.markAsDeleted(notification.id) },
            onMarkReadCallback = { notification -> markNotificationAsRead(notification) },
            onArrivalConsumedCallback = { id -> viewModel.consumeArrivedId(id) },
        )
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val lastVisible = lm.findLastVisibleItemPosition()
                val total = lm.itemCount
                if (lastVisible >= total - FeedViewModel.SCROLL_THRESHOLD && !viewModel.isLoadingPage) {
                    viewModel.loadNextPage()
                }
            }
        })

        observeFeed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newDeepLinkId = intent.getStringExtra(EXTRA_DEEP_LINK_NOTIFICATION_ID)
        if (newDeepLinkId != null) {
            deepLinkNotificationId = newDeepLinkId
            deepLinkConsumed = false
        }
    }

    private fun observeFeed() {
        // Observe the live first-page Room Flow; ViewModel handles arrival detection + merge.
        viewModel.observeLivePage(subscriptionId).observe(this) { livePage ->
            viewModel.onLivePageUpdate(livePage)
        }

        // Observe the merged feed (live page + paginated pages) and submit to adapter.
        viewModel.feedItems.observe(this) { items ->
            val arrivedIds = viewModel.newlyArrivedIds.value ?: emptySet()
            adapter.setNewlyArrivedIds(arrivedIds)
            adapter.submitList(items) {
                handleDeepLink(items)
            }
            val state = if (items.isEmpty()) {
                FeedState.Empty(isAllFeed = subscriptionId == ALL_SUBSCRIPTIONS_ID)
            } else {
                FeedState.HasContent
            }
            applyFeedState(state)
        }

        // Observe newly-arrived IDs to sync with adapter and fire accessibility announcement.
        viewModel.newlyArrivedIds.observe(this) { arrivedIds ->
            adapter.setNewlyArrivedIds(arrivedIds)
            if (ArrivalAnnouncer.shouldAnnounce(arrivedIds)) {
                ArrivalAnnouncer.announceArrival(recyclerView, arrivedIds.size)
            }
        }

        // Observe page-load errors: surface the disconnected + retry panel.
        viewModel.pageLoadError.observe(this) { error ->
            if (error != null) {
                Log.w(TAG, "Page load failed: ${error.cause.message}", error.cause)
                applyFeedState(FeedState.Disconnected(isPageLoadFailure = true)) { viewModel.loadNextPage() }
            }
        }
    }

    /**
     * Single entry point that drives panel visibility. Exactly one panel is VISIBLE at a time.
     * onRetry is forwarded to the disconnected retry button when state is Disconnected(isPageLoadFailure=true).
     */
    fun applyFeedState(state: FeedState, onRetry: (() -> Unit)? = null) {
        // Hide all panels first, then show only the active one.
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        disconnectedContainer.visibility = View.GONE

        when (state) {
            is FeedState.Loading -> {
                loadingContainer.visibility = View.VISIBLE
            }
            is FeedState.Empty -> {
                emptyContainer.visibility = View.VISIBLE
                if (state.isAllFeed) {
                    emptyTitle.setText(R.string.empty_feed_all_title)
                    emptyBody.setText(R.string.empty_feed_all_body)
                    emptyBody.visibility = View.VISIBLE
                } else {
                    emptyTitle.setText(R.string.empty_feed_topic)
                    emptyBody.visibility = View.GONE
                }
            }
            is FeedState.Disconnected -> {
                disconnectedContainer.visibility = View.VISIBLE
                if (state.isPageLoadFailure && onRetry != null) {
                    disconnectedRetry.visibility = View.VISIBLE
                    disconnectedRetry.setOnClickListener { onRetry() }
                } else {
                    disconnectedRetry.visibility = View.GONE
                }
            }
            is FeedState.HasContent -> {
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun handleDeepLink(items: List<FeedItem>) {
        val targetId = deepLinkNotificationId ?: return
        if (deepLinkConsumed) return

        val index = items.indexOfFirst { it.notification.id == targetId }
        if (index < 0) return

        deepLinkConsumed = true
        adapter.setDeepLinkTargetId(targetId)
        recyclerView.smoothScrollToPosition(index)
        Log.d(TAG, "Deep-link scroll to position $index for notification $targetId")
    }

    private fun markNotificationAsRead(notification: Notification) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.markAsRead(notification.id)
        }
    }

    companion object {
        const val TAG = "NtfyFeedActivity"
        const val EXTRA_SUBSCRIPTION_ID = "subscriptionId"
        const val EXTRA_SUBSCRIPTION_TOPIC = "subscriptionTopic"
        const val EXTRA_DEEP_LINK_NOTIFICATION_ID = "deepLinkNotificationId"
        /** @deprecated Use [ALL_SUBSCRIPTIONS_ID] from FeedViewModel */
        const val ALL_SUBSCRIPTIONS = ALL_SUBSCRIPTIONS_ID
    }
}
