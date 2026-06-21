package io.heckel.ntfy.ui.structured

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.heckel.ntfy.R

/**
 * Horizontal inline meter bar with token-styled threshold colors.
 *
 * Renders a pill-shaped track (meter_track) with a clipped fill whose width is
 * proportional to the clamped 0–100 value. Threshold colors are derived from
 * [MeterState.Band].
 *
 * Precondition: only bind finite numeric values. Non-finite filtering belongs at
 * the Story 3.3 KvBlockRenderer boundary.
 *
 * Accessibility: one semantic node reporting a range (0–100, current normalized
 * value) using a ProgressBar class name so TalkBack announces it correctly.
 */
class InlineMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val fillRect = RectF()

    private var state: MeterState? = null
    private val trackHeight = resources.getDimension(R.dimen.meter_track_height)

    init {
        trackPaint.color = ContextCompat.getColor(context, R.color.meter_track)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        ViewCompat.setAccessibilityDelegate(this, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.ProgressBar"
                val s = state
                if (s != null) {
                    info.stateDescription = context.getString(
                        R.string.meter_accessibility_percent,
                        s.normalizedValue.toInt(),
                    )
                    @Suppress("DEPRECATION")
                    info.setRangeInfo(
                        AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                            AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_PERCENT,
                            0f,
                            100f,
                            s.normalizedValue,
                        )
                    )
                }
            }
        })
    }

    /**
     * Bind a new meter value. Only call with finite numeric values.
     * [value] is a raw numeric value — clamping and threshold classification are applied here.
     */
    fun bind(value: Double) {
        val newState = MeterState.from(value)
        val changed = newState != state
        state = newState
        fillPaint.color = ContextCompat.getColor(context, newState.band.fillColorRes)
        invalidate()
        if (changed) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, trackHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f

        // Track pill
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        // Fill pill — clipped to track width
        val s = state ?: return
        val fillWidth = (s.normalizedValue / 100f) * w
        if (fillWidth > 0f) {
            // Draw fill as a pill clamped to track bounds
            val clampedFillWidth = fillWidth.coerceAtMost(w)
            fillRect.set(0f, 0f, clampedFillWidth, h)
            canvas.save()
            canvas.clipRect(fillRect)
            canvas.drawRoundRect(trackRect, radius, radius, fillPaint)
            canvas.restore()
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.ProgressBar"
    }
}
