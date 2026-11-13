package io.heckel.ntfy.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.heckel.ntfy.R
import io.heckel.ntfy.incident.Incidents
import io.heckel.ntfy.incident.Incidents.State
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Time labels (web useIncidents.js hhmm / when) ─────────────────────────────

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAY_HHMM = DateTimeFormatter.ofPattern("EEE HH:mm")
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE")

private fun zoned(t: Long) = Instant.ofEpochSecond(t).atZone(ZoneId.systemDefault())

fun hhmm(t: Long): String = HHMM.format(zoned(t))

/** "14:05" for today, "Wed 21:00" for earlier days. */
fun whenLabel(t: Long, now: Long): String = if (t >= Incidents.dayStart(now)) hhmm(t) else WEEKDAY_HHMM.format(zoned(t))

fun weekday(t: Long): String = WEEKDAY.format(zoned(t))

// ── Tick strip (web TickStrip.jsx): one day as 96 fifteen-minute ticks ─────────

enum class TickSize(val height: Dp, val gap: Dp) { SM(6.dp, 1.dp), MD(12.dp, 2.dp), LG(40.dp, 2.dp) }

@Composable
fun toneOf(priority: Int): Color = when {
    priority >= 5 -> colorResource(R.color.priority_max)
    priority == 4 -> colorResource(R.color.priority_high)
    else -> colorResource(R.color.accent_ui)
}

@Composable
fun TickStrip(
    slots: Set<Int>,
    tone: Color,
    modifier: Modifier = Modifier,
    okSlot: Int? = null,
    nowSlot: Int? = null,
    size: TickSize = TickSize.MD,
    label: String? = null,
) {
    val track = colorResource(R.color.meter_track)
    val ok = colorResource(R.color.meter_ok)
    val now = colorResource(R.color.muted).copy(alpha = 0.45f)
    val semantics = if (label != null) Modifier.semantics { contentDescription = label } else Modifier.clearAndSetSemantics {}
    Canvas(modifier.fillMaxWidth().height(size.height).then(semantics)) {
        val n = Incidents.SLOTS_PER_DAY
        val gap = size.gap.toPx()
        val w = (this.size.width - gap * (n - 1)) / n
        val r = CornerRadius(w / 2, w / 2)
        for (i in 0 until n) {
            var alpha = 1f
            val color = when {
                i in slots -> tone
                i == okSlot -> ok
                i == nowSlot -> now // "now": a slightly darker tick
                nowSlot != null && i > nowSlot -> track.also { alpha = 0.4f } // not happened yet
                else -> track
            }
            drawRoundRect(color, Offset(i * (w + gap), 0f), Size(w, this.size.height), r, alpha = alpha)
        }
    }
}

@Composable
fun TickAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics {}) {
        listOf("00", "06", "12", "18", "24").forEachIndexed { i, h ->
            Text(
                h,
                modifier = Modifier.weight(1f),
                textAlign = when (i) { 0 -> TextAlign.Start; 4 -> TextAlign.End; else -> TextAlign.Center },
                style = MaterialTheme.typography.bodySmall,
                color = colorResource(R.color.muted),
            )
        }
    }
}

// ── Status avatar (web Avatar.jsx): colour + glyph both carry the state ───────

enum class Look { OPEN5, OPEN4, OPEN3, RESOLVED, QUIET, INFO, CLEAR }

fun lookFor(incident: Incidents.Incident<*>): Look = when (incident.state) {
    State.RESOLVED -> Look.RESOLVED
    State.QUIET -> Look.QUIET
    State.INFO -> Look.INFO
    State.OPEN -> when {
        incident.priority >= 5 -> Look.OPEN5
        incident.priority == 4 -> Look.OPEN4
        else -> Look.OPEN3
    }
}

private fun glyph(d: String, fill: Boolean = false) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(
        pathData = addPathNodes(d),
        fill = if (fill) SolidColor(Color.Black) else null,
        stroke = if (fill) null else SolidColor(Color.Black),
        strokeLineWidth = 2.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

private val ALERT = glyph("M12 5v9M12 19h.01")
private val CHECK = glyph("M5 12.5l4.5 4.5L19 7.5")
private val MOON = glyph("M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z", fill = true)
private val INFO = glyph("M12 11v7M12 6.5h.01")

@Composable
fun StatusAvatar(look: Look, modifier: Modifier = Modifier, unread: Boolean = false, large: Boolean = false) {
    val max = colorResource(R.color.priority_max)
    val high = colorResource(R.color.priority_high)
    val accentUi = colorResource(R.color.accent_ui)
    val accentText = colorResource(R.color.accent_text)
    val ok = colorResource(R.color.meter_ok)
    val (bg, fg, icon) = when (look) {
        Look.OPEN5 -> Triple(max.copy(alpha = 0.18f), max, ALERT)
        Look.OPEN4 -> Triple(high.copy(alpha = 0.20f), high, ALERT)
        Look.OPEN3 -> Triple(accentUi.copy(alpha = 0.18f), accentText, ALERT)
        Look.RESOLVED, Look.CLEAR -> Triple(ok.copy(alpha = 0.18f), ok, CHECK)
        Look.QUIET -> Triple(colorResource(R.color.surface_2), colorResource(R.color.muted), MOON)
        Look.INFO -> Triple(accentUi.copy(alpha = 0.16f), accentText, INFO)
    }
    val side = if (large) 56.dp else 40.dp
    val newLabel = stringResource(R.string.board_new_indicator)
    // The row's text is the accessible name; only the unread dot is announced (TalkBack: "New notification").
    Box(modifier.size(side).then(if (unread) Modifier.semantics { contentDescription = newLabel } else Modifier.clearAndSetSemantics {})) {
        Box(Modifier.fillMaxSize().background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(fg), modifier = Modifier.size(side / 2))
        }
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp) // web: 12px dot at -2px, plus a 2px surface ring
                    .size(16.dp)
                    .border(2.dp, colorResource(R.color.surface), CircleShape)
                    .padding(2.dp)
                    .background(accentUi, CircleShape)
            )
        }
    }
}
