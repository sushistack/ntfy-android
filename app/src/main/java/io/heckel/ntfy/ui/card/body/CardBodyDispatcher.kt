package io.heckel.ntfy.ui.card.body

/**
 * Determines the [CardBodyRoute] for a decoded notification body.
 *
 * Dispatch order (deterministic, per AC 3):
 *   1. Structured spec (dual-gate: "card" tag + valid JSON object + known type)
 *   2. Heuristic-kv shape (Story 3.8 — real detector wired)
 *   3. Plain text / paragraph
 *
 * This class is pure (no Android View references) so it is fast in JVM unit tests.
 *
 * @param heuristicKvDetector  injectable seam; defaults to the real Story 3.8 detector
 */
class CardBodyDispatcher(
    private val heuristicKvDetector: HeuristicKvDetector = HeuristicKvDetector.DEFAULT,
) {

    fun dispatch(tags: List<String>, decodedBody: String): CardBodyRoute {
        val spec = CardSpecParser.parseCardSpec(tags, decodedBody)
        if (spec != null) return CardBodyRoute.Structured(spec)

        if (heuristicKvDetector.isHeuristicKv(decodedBody)) {
            val kvSpec = HeuristicKvParser.parseHeuristicKvSpec(decodedBody)
            return CardBodyRoute.HeuristicKv(kvSpec)
        }

        return CardBodyRoute.Text(decodedBody)
    }

    /**
     * Seam for the heuristic-kv shape detector.
     * [DEFAULT] wires the real [HeuristicKvParser.detectBodyShape] from Story 3.8.
     * Tests can inject an always-true or always-false detector as needed.
     */
    fun interface HeuristicKvDetector {
        fun isHeuristicKv(decodedBody: String): Boolean

        companion object {
            val DEFAULT = HeuristicKvDetector { body ->
                HeuristicKvParser.detectBodyShape(body) == BodyShape.HeuristicKv
            }
            val UNIMPLEMENTED = HeuristicKvDetector { false }
        }
    }
}
