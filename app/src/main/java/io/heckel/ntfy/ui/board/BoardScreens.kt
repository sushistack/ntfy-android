package io.heckel.ntfy.ui.board

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Notification
import io.heckel.ntfy.db.Subscription
import io.heckel.ntfy.incident.Incidents
import io.heckel.ntfy.incident.Incidents.State
import io.heckel.ntfy.ui.card.body.CardBodyBinder
import io.heckel.ntfy.ui.card.body.CardBodyDispatcher
import io.heckel.ntfy.ui.card.body.MarkwonCardMarkdownRenderer
import io.heckel.ntfy.util.formatMessage
import io.heckel.ntfy.db.isMarkdown
import io.heckel.ntfy.util.splitTags

typealias Incident = Incidents.Incident<Notification>

// Shared layout values (web: max-w-feed px-4 py-6 / rounded-md bg-surface p-5).
val PagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 96.dp)
private val CardShape = RoundedCornerShape(20.dp)

fun topicLabel(sub: Subscription?): String = sub?.displayName?.takeIf { it.isNotEmpty() } ?: sub?.topic ?: ""

@Composable
private fun displayStyle() = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.7).sp, lineHeight = 34.sp)

@Composable
private fun muted() = colorResource(R.color.muted)

@Composable
private fun textColor() = colorResource(R.color.text)

@Composable
private fun Modifier.card(): Modifier = clip(CardShape).background(colorResource(R.color.surface))

@Composable
private fun sentTodayLabel(count: Int) = pluralStringResource(R.plurals.board_sent_today, count, count)

// ── Board (web Board.jsx) ─────────────────────────────────────────────────────

@Composable
fun BoardScreen(incidents: List<Incident>, now: Long, subs: Map<String, Subscription>, topic: Boolean, onOpen: (Incident) -> Unit) {
    if (incidents.isEmpty()) {
        EmptyPanel(stringResource(if (topic) R.string.board_empty_topic else R.string.board_empty), bellOff = true)
        return
    }
    val today = Incidents.dayStart(now)
    val nowSlot = Incidents.slotOf(now, today)
    val open = incidents.filter { it.state == State.OPEN }
    val quiet = incidents.filter { (it.state == State.QUIET || it.state == State.RESOLVED) && it.latestTime >= today }
    val info = incidents.filter { it.state == State.INFO && it.latestTime >= today }
    val lastToday = incidents.firstOrNull { it.latestTime >= today }?.latestTime

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp)) {
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                if (open.isEmpty()) {
                    StatusAvatar(Look.CLEAR, large = true)
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    if (open.isEmpty()) stringResource(R.string.board_summary_clear)
                    else pluralStringResource(R.plurals.board_summary_firing, open.size, open.size),
                    style = displayStyle(), color = textColor(),
                    modifier = Modifier.semantics { heading() },
                )
                val quietPart = if (quiet.isNotEmpty()) pluralStringResource(R.plurals.board_summary_quiet, quiet.size, quiet.size) + " " else ""
                val lastPart = if (lastToday != null) stringResource(R.string.board_summary_last, hhmm(lastToday)) else stringResource(R.string.board_summary_none_today)
                Text(quietPart + lastPart, style = MaterialTheme.typography.bodyLarge, color = muted(), modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (open.isEmpty()) {
            item {
                Column(Modifier.padding(top = 24.dp).fillMaxWidth().card().padding(20.dp)) {
                    TickStrip(
                        slots = Incidents.slotsForDay(incidents.flatMap { it.firings }, today),
                        tone = colorResource(R.color.priority_max),
                        nowSlot = nowSlot,
                        size = TickSize.LG,
                        label = stringResource(R.string.board_today_strip),
                    )
                    TickAxis()
                }
            }
        }
        if (open.isNotEmpty()) {
            sectionTitle(R.string.board_section_open)
            items(open, key = { "open-" + it.key }) { inc ->
                OpenCard(inc, subs[inc.subscriptionId], now, today, nowSlot) { onOpen(inc) }
                Spacer(Modifier.height(12.dp))
            }
        }
        sheet(R.string.board_section_quiet, quiet, subs, today, onOpen)
        sheet(R.string.board_section_info, info, subs, today, onOpen)
    }
}

private fun LazyListScope.sectionTitle(title: Int) = item(key = "title-$title") {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp),
        color = textColor(),
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 36.dp, bottom = 12.dp).semantics { heading() },
    )
}

private fun LazyListScope.sheet(title: Int, list: List<Incident>, subs: Map<String, Subscription>, today: Long, onOpen: (Incident) -> Unit) {
    if (list.isEmpty()) return
    sectionTitle(title)
    item(key = "sheet-$title") {
        Column(Modifier.fillMaxWidth().card().padding(vertical = 4.dp)) {
            list.forEach { inc -> QuietRow(inc, subs[inc.subscriptionId], today) { onOpen(inc) } }
        }
    }
}

/** Firing now: a full card each, with the day strip. */
@Composable
private fun OpenCard(inc: Incident, sub: Subscription?, now: Long, today: Long, nowSlot: Int, onOpen: () -> Unit) {
    val sent = sentTodayLabel(Incidents.sentToday(inc, today))
    val since = if (inc.since < inc.last) stringResource(R.string.board_since, whenLabel(inc.since, now)) else hhmm(inc.last)
    Column(Modifier.fillMaxWidth().card().clickable(role = Role.Button, onClick = onOpen).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            StatusAvatar(lookFor(inc), unread = inc.unread > 0)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(inc.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp), color = textColor())
                Text(
                    "${topicLabel(sub)} · $since",
                    style = MaterialTheme.typography.bodyMedium, color = muted(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(hhmm(inc.last), style = MaterialTheme.typography.bodySmall, color = muted(), modifier = Modifier.padding(top = 2.dp))
        }
        TickStrip(
            slots = Incidents.slotsForDay(inc.firings, today), tone = toneOf(inc.priority),
            nowSlot = nowSlot, label = sent, modifier = Modifier.padding(top = 16.dp),
        )
        Text(sent, style = MaterialTheme.typography.bodySmall, color = muted(), modifier = Modifier.padding(top = 8.dp))
    }
}

/** Went quiet / info: compact rows grouped on one sheet. */
@Composable
private fun QuietRow(inc: Incident, sub: Subscription?, today: Long, onOpen: () -> Unit) {
    val from = hhmm(inc.since)
    val to = hhmm(inc.resolvedAt ?: inc.last)
    val range = if (inc.state == State.INFO || from == to) to else stringResource(R.string.board_range, from, to)
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusAvatar(lookFor(inc), unread = inc.unread > 0)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(inc.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp), color = textColor())
                Text(
                    "${topicLabel(sub)} · $range", style = MaterialTheme.typography.bodySmall, color = muted(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        TickStrip(
            slots = Incidents.slotsForDay(inc.firings, today), tone = toneOf(inc.priority),
            okSlot = inc.resolvedAt?.let { Incidents.slotOf(it, today) },
            size = TickSize.SM, label = sentTodayLabel(Incidents.sentToday(inc, today)),
            modifier = Modifier.padding(start = 54.dp, top = 12.dp),
        )
    }
}

// ── Incident detail (web IncidentDetail.jsx) ──────────────────────────────────

private const val SENT_AT_LIMIT = 12

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncidentDetailScreen(
    inc: Incident?,
    now: Long,
    sub: Subscription?,
    onBack: () -> Unit,
    onMute: (Incident) -> Unit,
    onMarkRead: (Incident) -> Unit,
) {
    val today = Incidents.dayStart(now)
    val sent = inc?.let { sentTodayLabel(Incidents.sentToday(it, today)) } ?: ""
    LazyColumn(contentPadding = PagePadding) {
        item { BackButton(onBack) }
        if (inc == null) {
            item { EmptyPanel(stringResource(R.string.incident_not_found)) }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp)) {
                StatusAvatar(lookFor(inc), large = true)
                Text(
                    topicLabel(sub), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorResource(R.color.accent_text), modifier = Modifier.padding(top = 16.dp),
                )
                Text(inc.title, style = displayStyle(), color = textColor(), modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                val status = when (inc.state) {
                    State.OPEN -> stringResource(R.string.incident_status_open, whenLabel(inc.since, now))
                    State.QUIET -> stringResource(R.string.incident_status_quiet, whenLabel(inc.last, now))
                    State.RESOLVED -> stringResource(R.string.incident_status_resolved, whenLabel(inc.resolvedAt ?: inc.last, now))
                    State.INFO -> stringResource(R.string.incident_status_info, whenLabel(inc.last, now))
                }
                Text("$status $sent.", style = MaterialTheme.typography.bodyLarge, color = muted(), modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().card().padding(20.dp)) {
                TickStrip(
                    slots = Incidents.slotsForDay(inc.firings, today), tone = toneOf(inc.priority),
                    okSlot = inc.resolvedAt?.let { Incidents.slotOf(it, today) },
                    nowSlot = Incidents.slotOf(now, today), size = TickSize.LG, label = sent,
                )
                TickAxis()
            }
        }
        // Latest message in full, through the same body renderers as the message feed (kv/list/chart/markdown).
        item(key = "body-" + inc.latest.id) {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().card().padding(20.dp)) { MessageBody(inc.latest) }
        }
        item {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.incident_mute_hour), primary = false, modifier = Modifier.weight(1f)) { onMute(inc) }
                if (inc.unread > 0) {
                    ActionButton(stringResource(R.string.incident_mark_read), primary = true, modifier = Modifier.weight(1f)) { onMarkRead(inc) }
                }
            }
        }
        if (inc.firings.size > 1) {
            sectionTitle(R.string.incident_sent_at)
            item {
                FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    inc.firings.takeLast(SENT_AT_LIMIT).reversed().forEach { t ->
                        Text(
                            whenLabel(t, now),
                            style = MaterialTheme.typography.labelMedium, color = textColor(),
                            modifier = Modifier.clip(CircleShape).background(colorResource(R.color.surface)).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    if (inc.firings.size > SENT_AT_LIMIT) {
                        Text(
                            stringResource(R.string.incident_sent_earlier, inc.firings.size - SENT_AT_LIMIT),
                            style = MaterialTheme.typography.bodySmall, color = muted(),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private val CHEVRON_LEFT = ImageVector.Builder(defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes("M15 18l-6-6 6-6"), stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    .build()

@Composable
private fun BackButton(onBack: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onBack).padding(vertical = 4.dp).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(CHEVRON_LEFT, contentDescription = null, colorFilter = ColorFilter.tint(muted()), modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.board_back), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = muted())
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colorResource(if (primary) R.color.button_fill else R.color.surface))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorResource(if (primary) R.color.button_fill_text else R.color.text),
        )
    }
}

@Composable
private fun MessageBody(notification: Notification) {
    val textColor = colorResource(R.color.text)
    AndroidView(
        factory = { ctx ->
            val message = TextView(ctx).apply {
                setTextAppearance(R.style.TextAppearance_Ntfy_Body)
                setTextColor(ContextCompat.getColor(ctx, R.color.text))
            }
            val host = FrameLayout(ctx)
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(message)
                addView(host)
                tag = CardBodyBinder(message, CardBodyDispatcher(), MarkwonCardMarkdownRenderer(ctx), host)
            }
        },
        update = { view ->
            (view.tag as CardBodyBinder).bind(splitTags(notification.tags), formatMessage(notification), notification.isMarkdown())
            textColor // recompose on theme change
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── History (web History.jsx) ─────────────────────────────────────────────────

private const val DAYS = 7

@Composable
fun HistoryScreen(incidents: List<Incident>, now: Long, subs: Map<String, Subscription>, onOpen: (Incident) -> Unit) {
    val starts = (0 until DAYS).map { Incidents.dayStart(now, it - (DAYS - 1)) } // oldest → today
    val weekStart = starts.first()
    // Alerts only (info one-offs would be noise here), most frequent first.
    val rows = incidents
        .filter { it.state != State.INFO }
        .map { it to it.firings.filter { f -> f >= weekStart } }
        .filter { it.second.isNotEmpty() }
        .sortedByDescending { it.second.size }

    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(horizontal = 4.dp).padding(bottom = 16.dp)) {
                Text(stringResource(R.string.history_title), style = displayStyle(), color = textColor(), modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.history_description), style = MaterialTheme.typography.bodyLarge, color = muted(), modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (rows.isEmpty()) item { EmptyPanel(stringResource(R.string.history_empty)) }
        items(rows, key = { it.first.key }) { (inc, week) -> HistoryCard(inc, week, starts, now, subs[inc.subscriptionId]) { onOpen(inc) } }
    }
}

@Composable
private fun HistoryCard(inc: Incident, week: List<Long>, starts: List<Long>, now: Long, sub: Subscription?, onOpen: () -> Unit) {
    val summary = if (inc.state == State.OPEN) stringResource(R.string.history_still_firing)
    else pluralStringResource(R.plurals.history_sent_week, week.size, week.size)
    val days = starts.count { s -> week.any { it >= s && it < s + 86_400 } }
    Column(Modifier.fillMaxWidth().card().clickable(role = Role.Button, onClick = onOpen).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            StatusAvatar(lookFor(inc))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(inc.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp), color = textColor())
                Text(
                    buildAnnotatedString {
                        append("${topicLabel(sub)} · ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = textColor())) { append(summary) }
                    },
                    style = MaterialTheme.typography.bodyMedium, color = muted(), modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    if (days == 1) stringResource(R.string.history_days_one, hhmm(inc.last)) else stringResource(R.string.history_days_other, days),
                    style = MaterialTheme.typography.bodySmall, color = muted(),
                )
            }
        }
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            starts.forEachIndexed { d, s ->
                val isToday = d == DAYS - 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isToday) stringResource(R.string.history_today) else weekday(s),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
                        color = if (isToday) textColor() else muted(),
                        modifier = Modifier.width(40.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TickStrip(
                        slots = Incidents.slotsForDay(week, s), tone = toneOf(inc.priority),
                        okSlot = inc.resolvedAt?.takeIf { it >= s && it < s + 86_400 }?.let { Incidents.slotOf(it, s) },
                        nowSlot = if (isToday) Incidents.slotOf(now, s) else null,
                        size = TickSize.SM, modifier = Modifier.weight(1f),
                    )
                }
            }
            TickAxis(Modifier.padding(start = 48.dp))
        }
    }
}

// ── Empty / state panel (web StatePanel) ─────────────────────────────────────

private val BELL_OFF = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
    listOf(
        "M8.7 3A6 6 0 0 1 18 8a21.3 21.3 0 0 0 .6 5",
        "M17 17H3s3-2 3-9a4.67 4.67 0 0 1 .3-1.7",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0",
        "M2 2l20 20",
    ).forEach { addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
}.build()

@Composable
fun EmptyPanel(text: String, bellOff: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bellOff) {
            Box(Modifier.size(64.dp).clip(CardShape).background(colorResource(R.color.surface_2)), contentAlignment = Alignment.Center) {
                Image(BELL_OFF, contentDescription = null, colorFilter = ColorFilter.tint(muted()), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = muted())
    }
}

// ── Board / Messages switch (web ViewToggle.jsx) ──────────────────────────────

@Composable
fun ViewToggle(messages: Boolean, onChange: (messages: Boolean) -> Unit) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colorResource(R.color.border))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToggleItem(stringResource(R.string.board_view_board), active = !messages, Modifier.weight(1f)) { onChange(false) }
        ToggleItem(stringResource(R.string.board_view_messages), active = messages, Modifier.weight(1f)) { onChange(true) }
    }
}

/** ntfy-web FeedFilter: 전체 / 안읽음 pills above the message list, plus the unread-empty line. */
@Composable
fun FeedFilter(unreadOnly: Boolean, unreadCount: Int, showUnreadEmpty: Boolean, onChange: (unreadOnly: Boolean) -> Unit) {
    val label = stringResource(R.string.feed_filter_label)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(Modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(stringResource(R.string.feed_filter_all), active = !unreadOnly) { onChange(false) }
            FilterPill(
                if (unreadCount > 0) stringResource(R.string.feed_filter_unread_count, unreadCount) else stringResource(R.string.feed_filter_unread),
                active = unreadOnly,
            ) { onChange(true) }
        }
        if (showUnreadEmpty) {
            Text(
                stringResource(R.string.feed_unread_empty),
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                style = MaterialTheme.typography.bodyLarge, color = colorResource(R.color.muted), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .shadow(if (active) 0.dp else 1.dp, CircleShape)
            .clip(CircleShape)
            .background(colorResource(if (active) R.color.text else R.color.surface))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = MaterialTheme.typography.bodyMedium.fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            color = colorResource(if (active) R.color.surface else R.color.muted),
        )
    }
}

@Composable
private fun ToggleItem(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Inverted pill (text-coloured fill) reads as selected in both themes.
    Box(
        modifier
            .clip(CircleShape)
            .background(if (active) colorResource(R.color.text) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = active }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = MaterialTheme.typography.bodyMedium.fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            color = colorResource(if (active) R.color.surface else R.color.muted),
        )
    }
}
