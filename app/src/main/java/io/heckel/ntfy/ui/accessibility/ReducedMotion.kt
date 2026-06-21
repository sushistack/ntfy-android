package io.heckel.ntfy.ui.accessibility

import android.animation.ValueAnimator

/**
 * Query at animation decision time; do not cache for the process lifetime.
 * Returns true when the system has disabled animators (duration scale 0, battery saver, etc.).
 */
object ReducedMotion {

    fun isEnabled(): Boolean = isEnabledForAnimatorsEnabled(ValueAnimator.areAnimatorsEnabled())

    // Internal seam: allows unit testing both outcomes without mutating device settings.
    internal fun isEnabledForAnimatorsEnabled(animatorsEnabled: Boolean): Boolean = !animatorsEnabled
}
