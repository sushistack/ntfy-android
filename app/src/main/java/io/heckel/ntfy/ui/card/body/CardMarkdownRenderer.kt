package io.heckel.ntfy.ui.card.body

import android.widget.TextView

/**
 * Reusable Markdown renderer contract for card body slots.
 *
 * One implementation serves paragraph fallback (Story 3.1/3.8) and sections Markdown
 * blocks (Story 3.7) without duplicating node/style mappings.
 *
 * All methods must be safe to call on a recycled view: [reset] is idempotent and must
 * clear text, spans, movement method, and any compound child state left by a prior bind.
 *
 * Exceptions during rendering are caught at this boundary; [renderRawFallback] is called
 * with the original unmodified string and must not itself throw.
 *
 * Link/image policy (Story 3.6b seam): the implementation wires a replaceable link
 * resolver and image scheme policy. This contract does not claim protocol whitelist
 * completion — that belongs to 3.6b.
 */
interface CardMarkdownRenderer {
    /** Render [markdown] into [target], applying token-backed typography and block styles. */
    fun render(target: TextView, markdown: String)

    /**
     * Render [raw] as token-styled plain body text without any Markdown parsing.
     * Called automatically by [render] on exception; also exposed for external callers.
     */
    fun renderRawFallback(target: TextView, raw: String)

    /** Reset [target] to a clean state suitable for RecyclerView rebind. */
    fun reset(target: TextView)
}
