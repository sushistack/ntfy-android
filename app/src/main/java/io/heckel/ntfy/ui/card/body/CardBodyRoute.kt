package io.heckel.ntfy.ui.card.body

/**
 * Sealed route result from [CardBodyDispatcher].
 *
 * [Structured] — dual-gate passed; render with the appropriate structured renderer.
 * [HeuristicKv] — untagged body matched the heuristic kv shape; spec is ready for KvBlockRenderer.
 * [Text] — plain/fallback text path using token-styled renderer.
 */
sealed class CardBodyRoute {
    data class Structured(val spec: CardSpec) : CardBodyRoute()
    data class HeuristicKv(val kvSpec: KvSpec) : CardBodyRoute()
    data class Text(val decodedBody: String) : CardBodyRoute()
}
