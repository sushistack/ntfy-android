package io.heckel.ntfy.ui.card.body

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.ui.structured.InlineMeterView

/**
 * Renders a parsed [KvSpec] inside the card_body [ViewGroup] (AC 1–8).
 *
 * Responsibilities:
 * - Inflate view_card_kv.xml as the only child of [container].
 * - Determine responsive column count from measured body width vs the breakpoint (AC 6).
 * - Add one view_card_kv_row.xml per [KvRow] in payload order (AC 7).
 * - Set icon, key, value, status dot, and meter for each row (AC 2–5).
 * - Clear stale children on every bind to prevent recycler leakage (AC 8).
 *
 * No Activity, adapter, repository, coroutine, or navigation dependency.
 */
class KvBlockRenderer : CardBodyRenderer {

    override fun render(container: ViewGroup, route: CardBodyRoute) {
        if (route !is CardBodyRoute.Structured) return
        val spec = KvSpecParser.parse(route.spec.root) ?: return
        renderKvSpec(container, spec)
    }

    fun renderKvSpec(container: ViewGroup, spec: KvSpec) {
        // Always clear stale content first (AC 8)
        container.removeAllViews()

        val context = container.context
        val inflater = LayoutInflater.from(context)

        val gridView = inflater.inflate(R.layout.view_card_kv, container, false) as GridLayout
        container.addView(gridView)

        val breakpointPx = context.resources.getDimension(R.dimen.kv_two_column_breakpoint)

        fun buildRows(availableWidthPx: Int) {
            gridView.removeAllViews()

            val requestedColumns = spec.columns
            val actualColumns = if (requestedColumns == 2 && availableWidthPx >= breakpointPx) 2 else 1
            gridView.columnCount = actualColumns

            val rows = spec.rows
            for (index in rows.indices) {
                val row = rows[index]
                val rowView = inflater.inflate(R.layout.view_card_kv_row, gridView, false)
                bindRow(rowView, row)

                val params = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    // FILL alignment forces an EXACTLY measure spec on the cell so the row's
                    // weighted value TextView is bounded to the column width and wraps long
                    // unbreakable tokens (paths/URLs) instead of clipping on one line.
                    GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f),
                ).apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                }
                rowView.layoutParams = params
                gridView.addView(rowView)
            }
        }

        // Measure available width; use a one-shot pre-draw listener if not yet laid out.
        val containerWidth = container.width
        if (containerWidth > 0) {
            buildRows(containerWidth)
        } else {
            // Not yet measured — build with 1 column first, then rebuild if 2-col breakpoint passes
            buildRows(0)
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    container.viewTreeObserver.removeOnPreDrawListener(this)
                    val measured = container.width
                    buildRows(measured)
                    return true
                }
            }
            container.viewTreeObserver.addOnPreDrawListener(listener)
        }
    }

    private fun bindRow(rowView: View, row: KvRow) {
        val context = rowView.context

        val iconView = rowView.findViewById<TextView>(R.id.kv_row_icon)
        val keyView = rowView.findViewById<TextView>(R.id.kv_row_key)
        val dotView = rowView.findViewById<View>(R.id.kv_row_status_dot)
        val valueView = rowView.findViewById<TextView>(R.id.kv_row_value)
        val meterView = rowView.findViewById<InlineMeterView>(R.id.kv_row_meter)

        // Reset state on every bind (AC 8)
        dotView.visibility = View.GONE
        dotView.backgroundTintList = null
        valueView.setTextColor(ContextCompat.getColor(context, R.color.text))
        meterView.visibility = View.GONE

        // Icon glyph (AC 2)
        iconView.text = KvIconResolver.resolve(row.key, row.icon)

        // Key (AC 1)
        keyView.text = row.key

        // Value color: error → priority_urgent; others → text (AC 3)
        if (row.status == "error") {
            valueView.setTextColor(ContextCompat.getColor(context, R.color.priority_urgent))
        }
        valueView.text = row.value

        // Meter or status dot (AC 4, 5)
        val valueLp = valueView.layoutParams as LinearLayout.LayoutParams
        val meterLp = meterView.layoutParams as LinearLayout.LayoutParams
        val meterValue = row.meter
        if (meterValue != null && meterValue.isFinite()) {
            // Finite meter present: the meter takes the row's remaining width (fills the area);
            // the value shrinks to its text so the bar isn't a tiny right-pinned pill.
            valueLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
            valueLp.weight = 0f
            meterLp.width = 0
            meterLp.weight = 1f
            valueView.layoutParams = valueLp
            meterView.layoutParams = meterLp

            meterView.bind(meterValue)
            meterView.visibility = View.VISIBLE
        } else {
            // No finite meter: value takes the flexible space so long text stays fully visible (AC 7).
            valueLp.width = 0
            valueLp.weight = 1f
            valueView.layoutParams = valueLp

            // Show status dot for known status keywords (AC 4)
            val dotColorRes = statusDotColorRes(row.status)
            if (dotColorRes != null) {
                dotView.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, dotColorRes)
                )
                dotView.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        // Accepts both the meter vocabulary (ok/warning/critical) and the legacy ok/warn/error.
        private fun statusDotColorRes(status: String?): Int? = when (status?.lowercase()) {
            "ok", "up", "healthy", "success" -> R.color.accent_ui
            "warn", "warning", "degraded" -> R.color.priority_high
            "error", "critical", "down", "fail", "failed" -> R.color.priority_max
            else -> null
        }
    }
}
