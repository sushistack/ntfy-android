package io.heckel.ntfy.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.ui.accessibility.ReducedMotion
import io.heckel.ntfy.ui.design.GlowToken
import io.heckel.ntfy.ui.design.resolveGlow

/**
 * Manages transient card animations (new-arrival slide-in, deep-link pulse).
 *
 * Instantiated once per [MessageCardBinder] instance and bound to the same root view.
 * All animators are cancelled and all transient properties reset via [resetTransient]
 * before every bind, ensuring recycled holders never carry state to a new notification.
 */
class CardEffectController(
    private val rootView: View,
    private val cardView: CardView,
) {
    companion object {
        /** Duration spec from design tokens: 250ms ease-out slide. */
        const val ARRIVAL_DURATION_MS = 250L
        /** Deep-link pulse: visible selected emphasis fades back over 400ms. */
        const val DEEP_LINK_PULSE_DURATION_MS = 400L
        /** Static deep-link emphasis remains until explicitly cleared by the host. */
        const val DEEP_LINK_STATIC_ALPHA_START = 1.0f
    }

    private var runningAnimator: Animator? = null
    private var pendingRunnable: Runnable? = null
    private var savedCardBackground: Int? = null

    /**
     * Must be called at the start of every bind (before applying new state).
     * Cancels any in-flight animator, removes any pending runnables, and
     * restores baseline view properties.
     */
    fun resetTransient() {
        runningAnimator?.cancel()
        runningAnimator = null

        pendingRunnable?.let { rootView.removeCallbacks(it) }
        pendingRunnable = null

        // Restore baseline translation and alpha
        rootView.translationY = 0f
        rootView.alpha = 1f

        // Restore card background if we changed it for deep-link emphasis
        savedCardBackground?.let { colorInt ->
            cardView.setCardBackgroundColor(colorInt)
            savedCardBackground = null
        }

        // Clear any software layer set for glow
        rootView.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /**
     * Plays the new-arrival slide-in effect for the given [stableId].
     * After the animator starts, [consumed] is invoked so the host can remove
     * [stableId] from its tracking set, preventing replay on subsequent binds.
     *
     * Query reduced-motion at decision time (AC 5).
     */
    fun playArrival(context: Context, stableId: String, consumed: () -> Unit) {
        val reducedMotion = ReducedMotion.isEnabled()
        consumed() // consume immediately so rebinds cannot replay

        if (reducedMotion) {
            // Reduced motion: instant placement, no animator
            rootView.translationY = 0f
            rootView.alpha = 1f
            return
        }

        val offsetPx = context.resources.getDimension(R.dimen.card_arrival_slide_offset)
        rootView.translationY = -offsetPx
        rootView.alpha = 1f

        val animator = ObjectAnimator.ofFloat(rootView, View.TRANSLATION_Y, -offsetPx, 0f).apply {
            duration = ARRIVAL_DURATION_MS
            interpolator = DecelerateInterpolator()
        }
        runningAnimator = animator
        animator.start()
    }

    /**
     * Applies deep-link emphasis: starts from [surface_active] and pulses back to
     * the normal card colour via [consumed] reporting start.
     *
     * Uses the shared [GlowToken.ACCENT_DOT] glow in dark mode, independent of the
     * priority bar's P4/P5 glow (Story 1.2).
     *
     * In reduced-motion mode: applies a static [surface_active] background only —
     * no animation. The host is responsible for deterministic cleanup (e.g. calling
     * [resetTransient] when the user scrolls or a new bind occurs).
     */
    fun playDeepLinkPulse(context: Context, normalBackgroundColor: Int, consumed: () -> Unit) {
        val reducedMotion = ReducedMotion.isEnabled()
        consumed() // consume immediately

        val emphasizedColor = ContextCompat.getColor(context, R.color.surface_active)
        savedCardBackground = normalBackgroundColor
        cardView.setCardBackgroundColor(emphasizedColor)
        applyDeepLinkGlow(context)

        if (reducedMotion) {
            // Static emphasis: remains until next bind/reset (AC 5)
            return
        }

        val animator = ObjectAnimator.ofArgb(
            cardView, "cardBackgroundColor",
            emphasizedColor, normalBackgroundColor,
        ).apply {
            duration = DEEP_LINK_PULSE_DURATION_MS
            startDelay = 200L
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                savedCardBackground = null
                clearDeepLinkGlow()
            }
            override fun onAnimationCancel(animation: Animator) {
                savedCardBackground = null
                clearDeepLinkGlow()
            }
        })
        runningAnimator = animator
        animator.start()
    }

    /**
     * Applies the static surface_active emphasis without animation.
     * Used when [CardPresentation.StaticDeepLinkEmphasis] is set on the bind state.
     */
    fun applyStaticDeepLinkEmphasis(context: Context, normalBackgroundColor: Int) {
        savedCardBackground = normalBackgroundColor
        val emphasizedColor = ContextCompat.getColor(context, R.color.surface_active)
        cardView.setCardBackgroundColor(emphasizedColor)
        applyDeepLinkGlow(context)
    }

    // ------- private helpers -------

    private fun applyDeepLinkGlow(context: Context) {
        val spec = resolveGlow(context, GlowToken.ACCENT_DOT) ?: return
        val radiusPx = spec.blurRadiusDp * context.resources.displayMetrics.density
        val paint = android.graphics.Paint().apply {
            setShadowLayer(radiusPx, 0f, 0f, spec.color)
        }
        cardView.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
    }

    private fun clearDeepLinkGlow() {
        cardView.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}
