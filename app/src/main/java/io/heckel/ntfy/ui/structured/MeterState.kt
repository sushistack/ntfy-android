package io.heckel.ntfy.ui.structured

import androidx.annotation.ColorRes
import io.heckel.ntfy.R

/**
 * Pure model for an inline meter display.
 *
 * Precondition: [value] passed to [from] is finite.
 * Non-finite filtering belongs at the Story 3.3 parser boundary.
 *
 * [normalizedValue] is clamped to 0f..100f.
 * [band] is derived from [normalizedValue] — thresholds applied after clamping.
 */
data class MeterState(
    val normalizedValue: Float,
    val band: Band,
) {
    enum class Band {
        OK, WARNING, CRITICAL;

        @get:ColorRes
        val fillColorRes: Int
            get() = when (this) {
                OK -> R.color.meter_ok
                WARNING -> R.color.meter_warning
                CRITICAL -> R.color.meter_critical
            }
    }

    companion object {
        fun from(value: Double): MeterState {
            val clamped = clamp(value.toFloat())
            return MeterState(clamped, threshold(clamped))
        }

        /** Clamps a finite value to the 0..100 display range. */
        fun clamp(value: Float): Float = value.coerceIn(0f, 100f)

        /**
         * Selects the threshold band for a value already in 0..100.
         * Boundaries: < 65 → OK, 65..< 90 → WARNING, >= 90 → CRITICAL.
         */
        fun threshold(normalizedValue: Float): Band = when {
            normalizedValue < 65f -> Band.OK
            normalizedValue < 90f -> Band.WARNING
            else -> Band.CRITICAL
        }
    }
}
