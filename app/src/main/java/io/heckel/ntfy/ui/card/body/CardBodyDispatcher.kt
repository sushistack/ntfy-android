package io.heckel.ntfy.ui.card.body

/**
 * Determines the [CardBodyRoute] for a decoded notification body.
 *
 * Dispatch order (deterministic, per AC 3):
 *   1. Structured spec (dual-gate: "card" tag + valid JSON object + known type)
 *   2. Heuristic-kv shape (Story 3.8 seam; unimplemented branch safely continues to Text)
 *   3. Plain text / paragraph
 *
 * This class is pure (no Android View references) so it is fast in JVM unit tests.
 *
 * @param heuristicKvDetector  seam for Story 3.8; defaults to always-false until implemented
 */
class CardBodyDispatcher(
    private val heuristicKvDetector: HeuristicKvDetector = HeuristicKvDetector.UNIMPLEMENTED,
) {

    fun dispatch(tags: List<String>, decodedBody: String): CardBodyRoute {
        val spec = CardSpecParser.parseCardSpec(tags, decodedBody)
        if (spec != null) return CardBodyRoute.Structured(spec)

        if (heuristicKvDetector.isHeuristicKv(decodedBody)) {
            return CardBodyRoute.HeuristicKv(decodedBody)
        }

        return CardBodyRoute.Text(decodedBody)
    }

    /**
     * Seam that Story 3.8 will replace with the real heuristic-kv shape detector.
     * Until then [UNIMPLEMENTED] always returns false so the Text route is the safe fallback.
     */
    fun interface HeuristicKvDetector {
        fun isHeuristicKv(decodedBody: String): Boolean

        companion object {
            val UNIMPLEMENTED = HeuristicKvDetector { false }
        }
    }
}
