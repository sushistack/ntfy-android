package io.heckel.ntfy.ui

/**
 * Persistent card presentation mode — mutually exclusive, stable across rebinds.
 */
sealed class CardPresentation {
    /** Normal card bound to a real notification. */
    object Normal : CardPresentation()
    /** Skeleton placeholder — no notification data, non-interactive. */
    object Loading : CardPresentation()
    /**
     * Static deep-link emphasis without animation (reduced motion path).
     * The host sets this after scroll/locate; the binder applies a static surface_active tint.
     */
    data class StaticDeepLinkEmphasis(val targetId: String) : CardPresentation()
}

/**
 * One-shot transient effects. Each is consumed exactly once at bind time
 * and keyed by stable notification identity (not adapter position).
 *
 * The host clears a one-shot ID from its tracking set in the [consumed] callback
 * so subsequent binds of the same holder cannot replay it.
 */
sealed class CardEffect {
    /** No pending one-shot effect. */
    object None : CardEffect()

    /**
     * Slide-in from above for a genuinely newly arrived notification.
     *
     * @param stableId  the persisted notification identity that authorises the effect
     * @param consumed  callback the binder invokes after the animator starts so the host
     *                  can remove [stableId] from its tracking set
     */
    data class NewArrival(
        val stableId: String,
        val consumed: () -> Unit,
    ) : CardEffect()

    /**
     * Animated deep-link highlight: pulse from surface_active back to normal.
     *
     * @param targetId  the persisted notification identity
     * @param consumed  callback invoked after the animator starts
     */
    data class DeepLinkPulse(
        val targetId: String,
        val consumed: () -> Unit,
    ) : CardEffect()
}

/**
 * Complete bind-time state handed to [MessageCardBinder].
 *
 * [presentation] governs the persistent render mode.
 * [effect] governs the optional one-shot transient animation.
 * Both fields are fully independent; a card can be Normal + NewArrival simultaneously.
 */
data class CardBindState(
    val presentation: CardPresentation = CardPresentation.Normal,
    val effect: CardEffect = CardEffect.None,
)
