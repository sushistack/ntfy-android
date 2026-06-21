package io.heckel.ntfy.ui.card.chart

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.heckel.ntfy.R

/**
 * Custom View that renders bar or line chart geometry onto a Canvas.
 *
 * Design principles (AC 9, Story Dev Notes):
 *   • Paint, Path, and geometry buffers are pre-allocated; never created in onDraw().
 *   • Geometry is recomputed when model, size, layout-direction, or theme changes.
 *   • Theme colors are resolved per-bind, not cached globally, so configuration changes
 *     always see the current resource values.
 *   • The View accepts an immutable [ChartRenderModel]; rebinding replaces all geometry.
 *   • Accessibility: a concise content-description is set from the model (AC 11).
 */
class StructuredChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // Pre-allocated drawing objects — never created in onDraw().
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePath = Path()

    // Current geometry — rebuilt on model/size change.
    private var model: ChartRenderModel? = null
    private var bars: List<ChartGeometry.BarGeom> = emptyList()
    private var linePoints: List<ChartGeometry.LinePoint> = emptyList()
    private var lastMeasuredWidth = -1f
    private var lastMeasuredHeight = -1f

    /**
     * Bind a new [ChartRenderModel].  Replaces all state; triggers layout/draw as needed.
     * Pass null to clear the view (empty / all-invalid data path — AC 8).
     */
    fun bind(newModel: ChartRenderModel?) {
        model = newModel
        bars = emptyList()
        linePoints = emptyList()
        lastMeasuredWidth = -1f   // force geometry recompute on next layout
        updateAccessibilityDescription()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val plotHeightPx = resources.getDimension(R.dimen.chart_plot_height)
        setMeasuredDimension(w, plotHeightPx.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeGeometry(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val m = model ?: return
        if (m.points.isEmpty()) return

        // Resolve colors fresh from resources (AC 10 — no stale color integers).
        val accentColor = ContextCompat.getColor(context, R.color.accent_ui)
        barPaint.color = accentColor
        linePaint.color = accentColor
        dotPaint.color = accentColor

        val strokePx = resources.getDimension(R.dimen.chart_line_stroke)
        linePaint.strokeWidth = strokePx

        when (m.kind) {
            ChartKind.BAR -> drawBars(canvas)
            ChartKind.LINE -> drawLine(canvas, strokePx)
        }
    }

    private fun drawBars(canvas: Canvas) {
        for (bar in bars) {
            canvas.drawRect(bar.left, bar.top, bar.right, bar.bottom, barPaint)
        }
    }

    private fun drawLine(canvas: Canvas, strokePx: Float) {
        if (linePoints.size == 1) {
            // Single-point: draw a filled circle so valid data is always visible (AC 3).
            val dotRadius = resources.getDimension(R.dimen.chart_single_point_radius)
            canvas.drawCircle(linePoints[0].x, linePoints[0].y, dotRadius, dotPaint)
            return
        }
        linePath.reset()
        var first = true
        for (lp in linePoints) {
            if (first) { linePath.moveTo(lp.x, lp.y); first = false }
            else linePath.lineTo(lp.x, lp.y)
        }
        canvas.drawPath(linePath, linePaint)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Theme may have changed; colors are resolved per-draw, but geometry is size-independent.
        invalidate()
    }

    private fun recomputeGeometry(w: Float, h: Float) {
        val m = model
        if (m == null || m.points.isEmpty() || w <= 0f || h <= 0f) {
            bars = emptyList(); linePoints = emptyList(); return
        }
        if (w == lastMeasuredWidth && h == lastMeasuredHeight) return
        lastMeasuredWidth = w; lastMeasuredHeight = h

        val domain = ChartDomain.compute(m.points)
        when (m.kind) {
            ChartKind.BAR -> {
                bars = ChartGeometry.computeBars(m.points, domain, w, h)
                linePoints = emptyList()
            }
            ChartKind.LINE -> {
                linePoints = ChartGeometry.computeLinePoints(m.points, domain, w, h)
                bars = emptyList()
            }
        }
    }

    private fun updateAccessibilityDescription() {
        val m = model
        if (m == null || m.points.isEmpty()) {
            contentDescription = null
            return
        }
        val kindStr = if (m.kind == ChartKind.LINE) "line" else "bar"
        val count = m.points.size
        val min = m.points.minOf { it.value }
        val max = m.points.maxOf { it.value }
        val minLabel = ChartLabelFormatter.formatNumber(min)
        val maxLabel = ChartLabelFormatter.formatNumber(max)
        // AC 11: concise, localizable summary.
        contentDescription = "$kindStr chart, $count points, $minLabel to $maxLabel"
    }
}
