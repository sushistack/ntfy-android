package io.heckel.ntfy.ui.card.body

import android.content.Context
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.util.Linkify
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import io.heckel.ntfy.R
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.util.MarkwonLinkPolicy
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.StrongEmphasis

/**
 * Token-backed Markdown renderer for card body slots.
 *
 * Reusable by paragraph fallback (Story 3.1/3.8) and sections Markdown blocks (Story 3.7).
 * Construction takes only [Context]; no Activity, lifecycle, or navigation reference.
 *
 * Typography mapping (card-scale, not display-scale):
 *   h1  → text_subtitle (18sp) + semibold + @color/text
 *   h2  → text_body (16sp)     + semibold + @color/text
 *   h3  → text_body_sm (14sp)  + medium   + @color/text
 *   paragraph/list base → text_body_sm (14sp) + @color/muted
 *   strong → semibold + @color/text
 *   emphasis → italic + @color/muted
 *   inline code → font_mono + @color/surface_2 background + radius_sm rounding
 *   code block → font_mono + @color/surface_2 background + radius_sm rounding + block padding
 *   blockquote → @color/border left rule + spacing_3 inset + @color/muted text
 *   unordered list → disc bullet (BulletSpan) + spacing_2 gap
 *   ordered list → decimal numbering from CoreProps.ORDERED_LIST_ITEM_NUMBER
 *
 * Link/image policy seam (Story 3.6b): ImagesPlugin and LinkifyPlugin are wired as in the
 * legacy MarkwonFactory. Story 3.6b tightens protocol/scheme without touching typography.
 */
class MarkwonCardMarkdownRenderer(context: Context) : CardMarkdownRenderer {

    private val markwon: Markwon = buildMarkwon(context)

    override fun render(target: TextView, markdown: String) {
        reset(target)
        try {
            target.ellipsize = null
            target.maxLines = Int.MAX_VALUE
            markwon.setMarkdown(target, markdown)
            target.movementMethod = BetterLinkMovementMethod.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Markdown render failed; falling back to raw text", e)
            renderRawFallback(target, markdown)
        }
    }

    override fun renderRawFallback(target: TextView, raw: String) {
        reset(target)
        target.autoLinkMask = Linkify.WEB_URLS
        target.text = raw
        target.movementMethod = BetterLinkMovementMethod.getInstance()
    }

    override fun reset(target: TextView) {
        target.text = ""
        target.movementMethod = null
        target.autoLinkMask = 0
        target.ellipsize = null
        target.maxLines = Int.MAX_VALUE
        target.setOnClickListener(null)
        target.setOnLongClickListener(null)
    }

    private fun buildMarkwon(context: Context): Markwon {
        val monoTf: Typeface? = ResourcesCompat.getFont(context, R.font.font_mono)
        val sansTf: Typeface? = ResourcesCompat.getFont(context, R.font.font_sans)
        val textColorVal = ContextCompat.getColor(context, R.color.text)
        val mutedColorVal = ContextCompat.getColor(context, R.color.muted)
        val surface2Val = ContextCompat.getColor(context, R.color.surface_2)
        val borderVal = ContextCompat.getColor(context, R.color.border)
        val textBodySmSp = context.resources.getDimension(R.dimen.text_body_sm)
        val textBodySp = context.resources.getDimension(R.dimen.text_body)
        val textSubtitleSp = context.resources.getDimension(R.dimen.text_subtitle)
        val radiusSmPx = context.resources.getDimension(R.dimen.radius_sm)
        val spacing2Px = context.resources.getDimensionPixelSize(R.dimen.spacing_2)
        val spacing3Px = context.resources.getDimensionPixelSize(R.dimen.spacing_3)
        val spacing4Px = context.resources.getDimensionPixelSize(R.dimen.spacing_4)
        val density = context.resources.displayMetrics.density

        val accentTextColor = ContextCompat.getColor(context, R.color.accent_text)
        val securedImages = ImagesPlugin.create().apply {
            removeSchemeHandler("data")
            removeSchemeHandler("file")
            removeSchemeHandler("content")
            removeSchemeHandler("android.resource")
        }

        return Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(MovementMethodPlugin.create(BetterLinkMovementMethod.getInstance()))
            .usePlugin(securedImages)
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(accentTextColor)
                        .isLinkUnderlined(true)
                        // Block margin backed by spacing_4 token
                        .blockMargin(spacing4Px)
                        // Blockquote visual config: width and color (rule drawn by our custom span)
                        .blockQuoteWidth((density * 3 + 0.5f).toInt())
                        .blockQuoteColor(ContextCompat.getColor(context, R.color.border))
                        .bulletWidth((density * 4 + 0.5f).toInt())
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        if (link != null && MarkwonLinkPolicy.isLinkAllowed(link)) {
                            LinkResolverDef().resolve(view, link)
                        }
                    }
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    // --- Headings: card-scale sizes, semibold/medium weight, primary text color ---
                    builder.setFactory(Heading::class.java) { _, props: RenderProps? ->
                        if (props == null) return@setFactory emptyArray<Any>()
                        val level = CoreProps.HEADING_LEVEL.require(props)
                        val sizeSp = when (level) {
                            1 -> textSubtitleSp  // 18sp
                            2 -> textBodySp      // 16sp
                            else -> textBodySmSp // 14sp (h3+)
                        }
                        // h1/h2: semibold (weight 600); h3+: medium (weight 500)
                        val tf = buildWeightedTypeface(sansTf, if (level <= 2) 600 else 500)
                        arrayOf(
                            android.text.style.AbsoluteSizeSpan(sizeSp.toInt(), false),
                            CustomTypefaceSpan(tf),
                            ForegroundColorSpan(textColorVal),
                        )
                    }

                    // --- StrongEmphasis: semibold + primary text ---
                    builder.setFactory(StrongEmphasis::class.java) { _, _ ->
                        arrayOf(
                            CustomTypefaceSpan(buildWeightedTypeface(sansTf, 600)),
                            ForegroundColorSpan(textColorVal),
                        )
                    }

                    // --- Emphasis: italic + muted ---
                    builder.setFactory(Emphasis::class.java) { _, _ ->
                        arrayOf(
                            StyleSpan(Typeface.ITALIC),
                            ForegroundColorSpan(mutedColorVal),
                        )
                    }

                    // --- Inline code: mono font + surface_2 background + rounded corners ---
                    builder.setFactory(Code::class.java) { _, _ ->
                        val hPad = spacing2Px / 2
                        arrayOf(
                            monoTf?.let { CustomTypefaceSpan(it) } ?: TypefaceSpan("monospace"),
                            InlineCodeBackgroundSpan(surface2Val, radiusSmPx, hPad),
                        )
                    }

                    // --- Fenced code block: mono font + surface_2 background block ---
                    builder.setFactory(FencedCodeBlock::class.java) { _, _ ->
                        arrayOf(
                            monoTf?.let { CustomTypefaceSpan(it) } ?: TypefaceSpan("monospace"),
                            CodeBlockBackgroundSpan(surface2Val, radiusSmPx, spacing4Px, spacing2Px),
                        )
                    }

                    // --- Indented code block ---
                    builder.setFactory(IndentedCodeBlock::class.java) { _, _ ->
                        arrayOf(
                            monoTf?.let { CustomTypefaceSpan(it) } ?: TypefaceSpan("monospace"),
                            CodeBlockBackgroundSpan(surface2Val, radiusSmPx, spacing4Px, spacing2Px),
                        )
                    }

                    // --- Blockquote: border-colored left rule + muted text ---
                    builder.appendFactory(BlockQuote::class.java) { _, _ ->
                        arrayOf(
                            BlockquoteRuleSpan(borderVal, (density * 3 + 0.5f).toInt(), spacing3Px),
                            ForegroundColorSpan(mutedColorVal),
                        )
                    }

                    // --- List items: type-aware bullet vs ordered number ---
                    builder.setFactory(ListItem::class.java) { _, props: RenderProps? ->
                        val bulletGap = spacing2Px
                        // Use LIST_ITEM_TYPE to distinguish ordered from unordered
                        val listItemType = if (props != null) CoreProps.LIST_ITEM_TYPE.get(props) else null
                        val isOrdered = listItemType == CoreProps.ListItemType.ORDERED

                        if (isOrdered) {
                            // Ordered: let Markwon's default ordered span handle numbering
                            // Return null so Markwon falls back to its default OrderedListItemSpan
                            null
                        } else {
                            android.text.style.BulletSpan(bulletGap)
                        }
                    }

                    // --- Links: inert accent span for rejected destinations ---
                    builder.setFactory(Link::class.java) { _, props ->
                        val destination = CoreProps.LINK_DESTINATION.get(props) ?: ""
                        if (MarkwonLinkPolicy.isLinkAllowed(destination)) {
                            null // Let Markwon default (clickable link via resolver)
                        } else {
                            ForegroundColorSpan(accentTextColor)
                        }
                    }
                }
            })
            .build()
    }

    companion object {
        private const val TAG = "MarkwonCardMarkdownRenderer"

        /**
         * Factory method for callers that do not own the lifecycle (e.g. Story 3.7).
         * Identical to constructor; provided for symmetry with MarkwonFactory.
         */
        fun create(context: Context): MarkwonCardMarkdownRenderer = MarkwonCardMarkdownRenderer(context)

        /**
         * Build a typeface at [weight] from [base], compatible with API 26+.
         * On API 28+ uses the system weight API for precise weight matching.
         * On API 26-27 falls back to BOLD for semibold requests and NORMAL otherwise.
         */
        @Suppress("DEPRECATION")
        fun buildWeightedTypeface(base: Typeface?, weight: Int): Typeface {
            if (base == null) {
                return Typeface.defaultFromStyle(if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            }
            return if (android.os.Build.VERSION.SDK_INT >= 28) {
                Typeface.create(base, weight, false)
            } else {
                // API 26-27: best approximation using style flag
                Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }
}

/**
 * Typeface span that applies an exact [Typeface] object (not just a family/style string).
 * Required for weight-specific faces (semibold 600, medium 500) loaded from font resources.
 */
internal class CustomTypefaceSpan(private val typeface: Typeface) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(ds: android.text.TextPaint) {
        applyTypeface(ds)
    }

    override fun updateMeasureState(paint: android.text.TextPaint) {
        applyTypeface(paint)
    }

    private fun applyTypeface(paint: android.text.TextPaint) {
        val old = paint.typeface
        val oldStyle = old?.style ?: 0
        // Merge italic from the enclosing italic span if present
        val italic = (oldStyle and Typeface.ITALIC) != 0
        if (italic && android.os.Build.VERSION.SDK_INT >= 28) {
            paint.typeface = Typeface.create(typeface, typeface.weight, true)
        } else {
            paint.typeface = typeface
        }
    }
}
