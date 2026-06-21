package io.heckel.ntfy.ui.card.body

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan

/**
 * Token-backed blockquote leading rule span.
 *
 * Draws a colored vertical bar [ruleWidth] px wide on the leading edge, then insets
 * the text by [totalInset] px. Colors come from the caller so no context is stored.
 */
class BlockquoteRuleSpan(
    private val ruleColor: Int,
    private val ruleWidth: Int,
    private val totalInset: Int,
) : LeadingMarginSpan {

    override fun getLeadingMargin(first: Boolean): Int = totalInset

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: android.text.Layout,
    ) {
        val savedColor = paint.color
        val savedStyle = paint.style
        paint.color = ruleColor
        paint.style = Paint.Style.FILL
        val left = if (dir > 0) x.toFloat() else (x - ruleWidth).toFloat()
        canvas.drawRect(left, top.toFloat(), left + ruleWidth, bottom.toFloat(), paint)
        paint.color = savedColor
        paint.style = savedStyle
    }
}

/**
 * Rounded background span for inline code (single-line).
 *
 * Draws [bgColor] behind the text run with [cornerRadius] px rounding.
 * Horizontal padding [hPad] is added inside the corners.
 */
class InlineCodeBackgroundSpan(
    private val bgColor: Int,
    private val cornerRadius: Float,
    private val hPad: Int,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            val orig = paint.fontMetricsInt
            fm.ascent = orig.ascent
            fm.descent = orig.descent
            fm.top = orig.top
            fm.bottom = orig.bottom
        }
        return (paint.measureText(text, start, end) + 2 * hPad).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val savedColor = paint.color
        paint.color = bgColor
        val rect = RectF(x, top.toFloat(), x + paint.measureText(text, start, end) + 2 * hPad, bottom.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = savedColor
        canvas.drawText(text, start, end, x + hPad, y.toFloat(), paint)
    }
}

/**
 * Rounded background span for fenced/indented code blocks (multi-line).
 *
 * Applied to the full code block run via [LineBackgroundSpan]; [hPad] and [vPad]
 * are added inside the rounded rect.
 */
class CodeBlockBackgroundSpan(
    private val bgColor: Int,
    private val cornerRadius: Float,
    private val hPad: Int,
    private val vPad: Int,
) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val savedColor = paint.color
        paint.color = bgColor
        val rect = RectF(
            (left + hPad / 2).toFloat(),
            (top - vPad).toFloat(),
            (right - hPad / 2).toFloat(),
            (bottom + vPad).toFloat(),
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = savedColor
    }
}
