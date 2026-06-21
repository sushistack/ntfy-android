package io.heckel.ntfy.ui.message

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName

/**
 * Shared typed loader for the parser-parity golden corpus.
 *
 * Later Epic 3 consumer tests load the relevant group via [ParserParityGoldenCorpus.load]
 * and parameterize over it. They do NOT duplicate enum mappings or parsing logic here.
 *
 * Consumer ownership:
 *   iconCases            → Story 3.3 (kv icon lookup)
 *   meterCases           → Story 3.2 (inline meter threshold)
 *   cardGateCases        → Story 3.1 (card detection / parseCardSpec)
 *   markdownDestinationCases → Story 3.6b (markdown link/image security)
 *   shapeCases           → Story 3.8 (heuristic-kv fallback shape detection)
 */
object ParserParityGoldenCorpus {

    private val RESOURCE_PATH = "/io/heckel/ntfy/ui/message/parser-parity-golden.json"

    fun load(): Corpus {
        val stream = ParserParityGoldenCorpus::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: error("Golden corpus fixture not found on classpath: $RESOURCE_PATH")
        val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return Gson().fromJson(json, Corpus::class.java)
            ?: throw JsonParseException("Failed to parse golden corpus fixture")
    }

    // -------------------------------------------------------------------------
    // Root corpus model
    // -------------------------------------------------------------------------

    data class Corpus(
        @SerializedName("schemaVersion") val schemaVersion: Int,
        @SerializedName("iconCases") val iconCases: List<IconCase>,
        @SerializedName("meterCases") val meterCases: List<MeterCase>,
        @SerializedName("cardGateCases") val cardGateCases: List<CardGateCase>,
        @SerializedName("markdownDestinationCases") val markdownDestinationCases: List<MarkdownDestinationCase>,
        @SerializedName("shapeCases") val shapeCases: List<ShapeCase>
    )

    // -------------------------------------------------------------------------
    // Icon cases (consumed by Story 3.3)
    // -------------------------------------------------------------------------

    data class IconCase(
        @SerializedName("id") val id: String,
        @SerializedName("input") val input: IconInput,
        @SerializedName("expected") val expected: String
    )

    data class IconInput(
        @SerializedName("key") val key: String,
        @SerializedName("icon") val icon: String? = null
    )

    // -------------------------------------------------------------------------
    // Meter cases (consumed by Story 3.2)
    // -------------------------------------------------------------------------

    data class MeterCase(
        @SerializedName("id") val id: String,
        @SerializedName("input") val input: MeterInput,
        @SerializedName("expected") val expected: MeterClass
    )

    data class MeterInput(
        @SerializedName("value") val value: Double
    )

    enum class MeterClass {
        @SerializedName("ok") OK,
        @SerializedName("warning") WARNING,
        @SerializedName("critical") CRITICAL
    }

    // -------------------------------------------------------------------------
    // Card gate cases (consumed by Story 3.1)
    // -------------------------------------------------------------------------

    data class CardGateCase(
        @SerializedName("id") val id: String,
        @SerializedName("input") val input: CardGateInput,
        @SerializedName("expected") val expected: CardGateResult
    )

    data class CardGateInput(
        @SerializedName("tags") val tags: List<String>,
        @SerializedName("body") val body: String
    )

    enum class CardGateResult {
        @SerializedName("structured") STRUCTURED,
        @SerializedName("fallback") FALLBACK
    }

    // -------------------------------------------------------------------------
    // Markdown destination cases (consumed by Story 3.6b)
    // -------------------------------------------------------------------------

    data class MarkdownDestinationCase(
        @SerializedName("id") val id: String,
        @SerializedName("input") val input: MarkdownDestinationInput,
        @SerializedName("expected") val expected: MarkdownDestinationResult
    )

    data class MarkdownDestinationInput(
        @SerializedName("kind") val kind: MarkdownDestinationKind,
        @SerializedName("destination") val destination: String
    )

    enum class MarkdownDestinationKind {
        @SerializedName("link") LINK,
        @SerializedName("image") IMAGE
    }

    enum class MarkdownDestinationResult {
        @SerializedName("live") LIVE,
        @SerializedName("inert") INERT,
        @SerializedName("render") RENDER,
        @SerializedName("drop") DROP
    }

    // -------------------------------------------------------------------------
    // Shape cases (consumed by Story 3.8)
    // -------------------------------------------------------------------------

    data class ShapeCase(
        @SerializedName("id") val id: String,
        @SerializedName("input") val input: ShapeInput,
        @SerializedName("expected") val expected: ShapeResult
    )

    data class ShapeInput(
        @SerializedName("body") val body: String
    )

    enum class ShapeResult {
        @SerializedName("paragraph") PARAGRAPH,
        @SerializedName("heuristic-kv") HEURISTIC_KV
    }
}
