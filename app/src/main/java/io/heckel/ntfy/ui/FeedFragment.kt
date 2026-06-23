package io.heckel.ntfy.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.heckel.ntfy.R
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.db.Notification
import io.heckel.ntfy.msg.ApiService
import io.heckel.ntfy.ui.accessibility.ArrivalAnnouncer
import io.heckel.ntfy.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single-feed surface (Epic 4). Hosts the ordered notification feed for either the
 * All-notifications view (subscriptionId == ALL_SUBSCRIPTIONS_ID) or a single topic.
 *
 * Extracted from the original FeedActivity so the same feed can be embedded directly in
 * MainActivity's drawer shell (the app's primary UI) and re-used by FeedActivity as a
 * standalone host (deep-link entry). Switching feeds = replace this fragment with new args.
 */
class FeedFragment : Fragment(), DeleteSwipeConfirmFragment.Listener {

    private val viewModel by viewModels<FeedViewModel> {
        FeedViewModelFactory((requireActivity().application as Application).repository)
    }
    private val repository by lazy { (requireActivity().application as Application).repository }
    private val api by lazy { ApiService(requireContext()) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var disconnectedContainer: View
    private lateinit var disconnectedRetry: Button
    private lateinit var adapter: FeedAdapter
    private lateinit var fab: FloatingActionButton

    private var subscriptionId: Long = ALL_SUBSCRIPTIONS_ID
    private var subscriptionTopic: String? = null
    private var deepLinkNotificationId: String? = null
    private var deepLinkConsumed = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.activity_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        subscriptionId = arguments?.getLong(ARG_SUBSCRIPTION_ID, ALL_SUBSCRIPTIONS_ID) ?: ALL_SUBSCRIPTIONS_ID
        subscriptionTopic = arguments?.getString(ARG_SUBSCRIPTION_TOPIC)
        deepLinkNotificationId = arguments?.getString(ARG_DEEP_LINK_NOTIFICATION_ID)
        deepLinkConsumed = false

        recyclerView = view.findViewById(R.id.feed_recycler)
        loadingContainer = view.findViewById(R.id.feed_loading_container)
        emptyContainer = view.findViewById(R.id.feed_empty_container)
        emptyTitle = emptyContainer.findViewById(R.id.feed_empty_title)
        emptyBody = emptyContainer.findViewById(R.id.feed_empty_body)
        disconnectedContainer = view.findViewById(R.id.feed_disconnected_container)
        disconnectedRetry = disconnectedContainer.findViewById(R.id.feed_disconnected_retry)
        fab = view.findViewById(R.id.feed_fab)

        // Publish FAB applies only to a single topic; the All-feed has no publish target.
        val topic = subscriptionTopic
        if (topic != null && topic.isNotEmpty()) {
            fab.visibility = View.VISIBLE
            fab.setOnClickListener {
                val sheet = PublishBottomSheet.newInstance(topic)
                sheet.setOutboxListener(buildOutboxListener())
                sheet.show(childFragmentManager, PublishBottomSheet.TAG)
            }
        } else {
            fab.visibility = View.GONE
        }

        applyFeedState(FeedState.Loading)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos != 0) outRect.top = resources.getDimensionPixelSize(R.dimen.spacing_4)
            }
        })

        adapter = FeedAdapter(
            activity = requireActivity(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            repository = repository,
            onDeleteRequestCallback = { notification -> viewModel.markAsDeleted(notification.id) },
            onMarkReadCallback = { notification -> markNotificationAsRead(notification) },
            onArrivalConsumedCallback = { id -> viewModel.consumeArrivedId(id) },
            onRetryRequestCallback = { localId -> retryOptimistic(localId) },
            onDiscardRequestCallback = { localId -> discardOptimistic(localId) },
        )
        recyclerView.adapter = adapter

        val swipeCallback = FeedSwipeCallback(
            notificationAt = { position ->
                (adapter.currentList.getOrNull(position) as? FeedItem.Server)?.notification
            },
            onSwipeLeft = { notification, position -> showDeleteConfirm(notification.id, position) },
            onSwipeRight = { notification -> markNotificationAsRead(notification) },
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)

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
        observeOutbox()
    }

    private fun observeFeed() {
        viewModel.observeLivePage(subscriptionId).observe(viewLifecycleOwner) { livePage ->
            viewModel.onLivePageUpdate(livePage)
        }

        viewModel.feedItems.observe(viewLifecycleOwner) { serverItems: List<FeedItem> ->
            submitMergedList(serverItems)
        }

        viewModel.newlyArrivedIds.observe(viewLifecycleOwner) { arrivedIds ->
            adapter.setNewlyArrivedIds(arrivedIds)
            if (ArrivalAnnouncer.shouldAnnounce(arrivedIds)) {
                ArrivalAnnouncer.announceArrival(recyclerView, arrivedIds.size)
            }
        }

        viewModel.pageLoadError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Log.w(TAG, "Page load failed: ${error.cause.message}", error.cause)
                applyFeedState(FeedState.Disconnected(isPageLoadFailure = true)) { viewModel.loadNextPage() }
            } else {
                val items = viewModel.feedItems.value ?: emptyList()
                val state = if (items.isEmpty()) {
                    FeedState.Empty(isAllFeed = subscriptionId == ALL_SUBSCRIPTIONS_ID)
                } else {
                    FeedState.HasContent
                }
                applyFeedState(state)
            }
        }
    }

    /**
     * Single entry point that drives panel visibility. Exactly one panel is VISIBLE at a time.
     */
    fun applyFeedState(state: FeedState, onRetry: (() -> Unit)? = null) {
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

        val index = items.indexOfFirst { (it as? FeedItem.Server)?.notification?.id == targetId }
        if (index < 0) return

        deepLinkConsumed = true
        adapter.setDeepLinkTargetId(targetId)
        recyclerView.smoothScrollToPosition(index)
        Log.d(TAG, "Deep-link scroll to position $index for notification $targetId")
    }

    private fun markNotificationAsRead(notification: Notification) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            repository.markAsRead(notification.id)
        }
    }

    private fun showDeleteConfirm(notificationId: String, position: Int) {
        DeleteSwipeConfirmFragment.newInstance(notificationId, position)
            .show(childFragmentManager, DeleteSwipeConfirmFragment.TAG)
    }

    // ── Optimistic outbox ───────────────────────────────────────────────────────

    private fun observeOutbox() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.outbox.collect {
                submitMergedList(viewModel.feedItems.value ?: emptyList())
            }
        }
    }

    private fun submitMergedList(serverItems: List<FeedItem>) {
        val optimisticItems = viewModel.outbox.value.map { FeedItem.Optimistic(it) }
        val merged = optimisticItems + serverItems.filterIsInstance<FeedItem.Server>()
        val arrivedIds = viewModel.newlyArrivedIds.value ?: emptySet()
        adapter.setNewlyArrivedIds(arrivedIds)
        adapter.submitList(merged) {
            handleDeepLink(merged)
        }
        val state = if (merged.isEmpty()) {
            FeedState.Empty(isAllFeed = subscriptionId == ALL_SUBSCRIPTIONS_ID)
        } else {
            FeedState.HasContent
        }
        applyFeedState(state)
    }

    private fun buildOutboxListener(): OutboxListener = object : OutboxListener {
        override fun onOptimisticEmit(msg: OptimisticMessage, job: Job) {
            viewModel.addOptimistic(msg)
            viewModel.registerOutboxJob(msg.localId, job)
        }

        override fun onOptimisticSuccess(localId: String) {
            viewModel.removeOptimistic(localId)
        }

        override fun onOptimisticFailure(localId: String, cause: String) {
            viewModel.updateOptimisticState(localId, SendState.Error(cause))
        }
    }

    private fun retryOptimistic(localId: String) {
        val msg = viewModel.outbox.value.firstOrNull { it.localId == localId } ?: return
        viewModel.cancelOutboxJob(localId)
        viewModel.updateOptimisticState(localId, SendState.Pending)
        val job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload = msg.payload
                val user = repository.getUser(payload.baseUrl)
                api.publish(
                    baseUrl  = payload.baseUrl,
                    topic    = payload.topic,
                    user     = user,
                    message  = payload.message,
                    title    = payload.title,
                    priority = payload.priority,
                    tags     = payload.tags,
                    delay    = "",
                )
                withContext(Dispatchers.Main) { viewModel.removeOptimistic(localId) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewModel.updateOptimisticState(localId, SendState.Error(e.message ?: getString(R.string.publish_sheet_error_unknown)))
                }
            }
        }
        viewModel.registerOutboxJob(localId, job)
    }

    private fun discardOptimistic(localId: String) {
        NotificationDeleteConfirmation.show(requireContext()) {
            viewModel.cancelOutboxJob(localId)
            viewModel.removeOptimistic(localId)
        }
    }

    // DeleteSwipeConfirmFragment.Listener
    override fun onSwipeDeleteConfirmed(notificationId: String, position: Int) {
        // Route through the ViewModel so the in-memory feed (incl. older-page snapshots) is pruned;
        // a direct repository call only updates the DB and leaves older-page cards visible.
        viewModel.markAsDeleted(notificationId)
    }

    override fun onSwipeDeleteCancelled(position: Int) {
        adapter.notifyItemChanged(position)
    }

    companion object {
        const val TAG = "NtfyFeedFragment"
        const val ARG_SUBSCRIPTION_ID = "subscriptionId"
        const val ARG_SUBSCRIPTION_TOPIC = "subscriptionTopic"
        const val ARG_DEEP_LINK_NOTIFICATION_ID = "deepLinkNotificationId"

        fun newInstance(subscriptionId: Long, topic: String?, deepLinkNotificationId: String? = null): FeedFragment {
            return FeedFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SUBSCRIPTION_ID, subscriptionId)
                    putString(ARG_SUBSCRIPTION_TOPIC, topic)
                    putString(ARG_DEEP_LINK_NOTIFICATION_ID, deepLinkNotificationId)
                }
            }
        }
    }
}
