package io.heckel.ntfy.ui.card.body

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/** Draws a 24×24-viewport SVG path with a 2px round stroke (the web's line icons), scaled to bounds. */
class KvIconDrawable(pathData: String, color: Int) : Drawable() {
    private val source: Path = PathParser.createPathFromPathData(pathData)
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    override fun draw(canvas: Canvas) {
        val scale = bounds.width() / 24f
        paint.strokeWidth = 2f * scale
        path.reset()
        source.transform(Matrix().apply { setScale(scale, scale); postTranslate(bounds.left.toFloat(), bounds.top.toFloat()) }, path)
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
