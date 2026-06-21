package io.heckel.ntfy.ui.card.chart

enum class ChartKind {
    BAR,
    LINE;

    companion object {
        fun fromWire(s: String?): ChartKind = if (s == "line") LINE else BAR
    }
}
