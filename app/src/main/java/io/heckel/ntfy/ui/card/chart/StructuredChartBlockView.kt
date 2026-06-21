package io.heckel.ntfy.ui.card.chart

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.heckel.ntfy.R

/**
 * Composite container for a chart block: a [StructuredChartView] (plot) plus an optional
 * label row below it.
 *
 * Label row rules (AC 6–7):
 *   • 1–12 valid points → one TextView per point, equally-wide cells, end-ellipsis.
 *   • 13+ valid points → no label row at all (no reserved height).
 *
 * Rebind clears all state, including recycled label children (AC 9).
 *
 * Layout direction: the label row respects RTL layout; chart data order is always
 * payload order (AC 3 / Dev Notes).
 */
class StructuredChartBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val plotView: StructuredChartView
    private val labelRow: LinearLayout

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        plotView = StructuredChartView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        addView(plotView)

        labelRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        addView(labelRow)
    }

    /**
     * Bind [model] to the chart. Pass null to hide everything (AC 8 — no visible geometry).
     */
    fun bind(model: ChartRenderModel?) {
        plotView.bind(model)
        rebuildLabelRow(model)
        // Accessibility: plot view carries the summary; label row is decorative (AC 11).
        importantForAccessibility = if (model == null || model.points.isEmpty()) {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    private fun rebuildLabelRow(model: ChartRenderModel?) {
        labelRow.removeAllViews()
        if (model == null || !ChartSpec.showLabels(model.points.size)) {
            labelRow.visibility = GONE
            return
        }
        labelRow.visibility = VISIBLE
        val captionSizePx = resources.getDimension(R.dimen.text_caption)
        val captionColor = ContextCompat.getColor(context, R.color.muted)

        for (pt in model.points) {
            val label = ChartLabelFormatter.labelFor(pt, model.unit)
            val tv = TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_PX, captionSizePx)
                setTextColor(captionColor)
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                // Suppress individual label announcements; plot carries the a11y summary (AC 11).
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            labelRow.addView(tv)
        }
    }
}
