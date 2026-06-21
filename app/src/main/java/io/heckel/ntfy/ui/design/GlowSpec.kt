package io.heckel.ntfy.ui.design

import android.content.Context
import android.content.res.Configuration
import io.heckel.ntfy.R

/**
 * Dark-only glow specification resolved from night-qualified resources.
 *
 * In light mode [resolve] always returns null — callers must check for null and skip
 * glow drawing entirely. This keeps light mode zero-cost and prevents accidental glow
 * in day themes.
 *
 * Glow drawing contract: use Paint.setShadowLayer(blurRadiusPx, 0f, 0f, color) on a
 * hardware-accelerated layer (setLayerType(LAYER_TYPE_SOFTWARE, ...) is required for
 * setShadowLayer on API < 28). On API 31+ you may additionally apply a RenderEffect blur
 * as an optimization, but it must not be the sole implementation because minSdk is 26.
 *
 * Token assignment:
 *   P4 (high priority)           → [PRIORITY_HIGH]
 *   P5 (max priority)            → [PRIORITY_MAX]
 *   unread dots, status dots,
 *   accent emphasis, deep-link   → [ACCENT_DOT]
 *   chart glow                   → [ACCENT_DOT] only when renderer explicitly opts in
 */
data class GlowSpec(
    /** ARGB glow color including alpha. */
    val color: Int,
    /** Blur radius in dp. Convert to px with context.resources.displayMetrics.density. */
    val blurRadiusDp: Float,
)

object GlowToken {
    const val PRIORITY_HIGH = "priority_high"
    const val PRIORITY_MAX = "priority_max"
    const val ACCENT_DOT = "accent_dot"
}

/**
 * Returns a [GlowSpec] for the given token in dark mode, or null in light mode.
 *
 * Callers must not cache the result across configuration changes.
 */
fun resolveGlow(context: Context, token: String): GlowSpec? {
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (nightMode != Configuration.UI_MODE_NIGHT_YES) return null

    return when (token) {
        GlowToken.PRIORITY_HIGH -> GlowSpec(
            color = context.getColor(R.color.glow_priority_high),
            blurRadiusDp = context.resources.getDimension(R.dimen.glow_priority_high_radius) /
                context.resources.displayMetrics.density,
        )
        GlowToken.PRIORITY_MAX -> GlowSpec(
            color = context.getColor(R.color.glow_priority_max),
            blurRadiusDp = context.resources.getDimension(R.dimen.glow_priority_max_radius) /
                context.resources.displayMetrics.density,
        )
        GlowToken.ACCENT_DOT -> GlowSpec(
            color = context.getColor(R.color.glow_accent_dot),
            blurRadiusDp = context.resources.getDimension(R.dimen.glow_accent_dot_radius) /
                context.resources.displayMetrics.density,
        )
        else -> null
    }
}
