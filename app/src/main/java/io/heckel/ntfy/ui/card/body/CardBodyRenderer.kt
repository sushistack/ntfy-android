package io.heckel.ntfy.ui.card.body

import android.view.ViewGroup

/**
 * Renderer contract for all card body types.
 *
 * Each implementation takes ownership of [container]: it inflates its own
 * layout into the container and sets up any listeners. The container is
 * always reset by [CardBodyBinder] before [render] is called.
 */
interface CardBodyRenderer {
    fun render(container: ViewGroup, route: CardBodyRoute)
}
