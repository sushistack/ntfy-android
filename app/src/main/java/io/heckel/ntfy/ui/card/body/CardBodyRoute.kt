package io.heckel.ntfy.ui.card.body

/**
 * Sealed route result from [CardBodyDispatcher].
 *
 * [Structured] — dual-gate passed; render with the appropriate structured renderer.
 * [HeuristicKv] — untagged body matches the heuristic kv shape; Story 3.8 fills the renderer.
 *                 Until 3.8 lands this safely continues to [Text].
 * [Text] — plain/fallback text path using token-styled renderer.
 */
sealed class CardBodyRoute {
    data class Structured(val spec: CardSpec) : CardBodyRoute()
    data class HeuristicKv(val decodedBody: String) : CardBodyRoute()
    data class Text(val decodedBody: String) : CardBodyRoute()
}
