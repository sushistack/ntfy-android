package io.heckel.ntfy.ui.board

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.incident.Incidents
import io.heckel.ntfy.ui.ALL_SUBSCRIPTIONS_ID
import io.heckel.ntfy.ui.compose.NotifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Board (one topic or all), its incident detail, or the 7-day history — the Compose screens of the
 * 2026-11 redesign, hosted in MainActivity's feed container next to the XML message feed.
 */
class BoardFragment : Fragment() {
    /** Implemented by the hosting activity. */
    interface Host {
        fun onIncidentDetailShown(shown: Boolean)
        fun openIncident(subscriptionId: Long, incidentId: String)
    }

    private val repository by lazy { (requireActivity().application as Application).repository }
    private val subscriptionId by lazy { requireArguments().getLong(ARG_SUBSCRIPTION_ID) }
    private val history by lazy { requireArguments().getBoolean(ARG_HISTORY) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { NotifyTheme { Root() } }
        }

    @Composable
    private fun Root() {
        val all = history || subscriptionId == ALL_SUBSCRIPTIONS_ID
        val notifications by remember {
            if (all) repository.getAllNotificationsLiveData() else repository.getNotificationsLiveData(subscriptionId)
        }.observeAsState()
        val subscriptions by remember { repository.getSubscriptionsLiveData() }.observeAsState(emptyList())
        // Refreshed every minute so "firing" turns into "went quiet" without new messages (web useNow).
        var now by remember { mutableLongStateOf(nowSeconds()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); now = nowSeconds() } }
        var selected by rememberSaveable { mutableStateOf(requireArguments().getString(ARG_INCIDENT_ID)) }

        val host = activity as? Host
        LaunchedEffect(selected) { host?.onIncidentDetailShown(selected != null) }
        BackHandler(enabled = selected != null) { selected = null }

        val list = notifications ?: return // loading
        val incidents = remember(list, now) { Incidents.group(list, now) }
        val subs = remember(subscriptions) { subscriptions.associateBy { it.id.toString() } }

        when {
            history -> HistoryScreen(incidents, now, subs) { inc ->
                host?.openIncident(inc.subscriptionId.toLong(), inc.id)
            }
            selected != null -> {
                val inc = incidents.find { it.id == selected }
                IncidentDetailScreen(
                    inc = inc, now = now, sub = inc?.let { subs[it.subscriptionId] },
                    onBack = { selected = null },
                    onMute = { mute(it.subscriptionId.toLong(), now) },
                    onMarkRead = { markRead(it) },
                )
            }
            else -> BoardScreen(incidents, now, subs) { selected = it.id }
        }
    }

    private fun mute(subscriptionId: Long, now: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.getSubscription(subscriptionId)?.let { repository.updateSubscription(it.copy(mutedUntil = now + 3600)) }
        }
    }

    private fun markRead(incident: Incident) {
        lifecycleScope.launch(Dispatchers.IO) {
            incident.notifications.filter { it.notificationId != 0 }.forEach { repository.markAsRead(it.id) }
        }
    }

    companion object {
        private const val ARG_SUBSCRIPTION_ID = "subscriptionId"
        private const val ARG_HISTORY = "history"
        private const val ARG_INCIDENT_ID = "incidentId"

        private fun nowSeconds() = System.currentTimeMillis() / 1000

        fun board(subscriptionId: Long, incidentId: String? = null) = BoardFragment().apply {
            arguments = bundleOf(ARG_SUBSCRIPTION_ID to subscriptionId, ARG_HISTORY to false, ARG_INCIDENT_ID to incidentId)
        }

        fun history() = BoardFragment().apply {
            arguments = bundleOf(ARG_SUBSCRIPTION_ID to ALL_SUBSCRIPTIONS_ID, ARG_HISTORY to true)
        }
    }
}
