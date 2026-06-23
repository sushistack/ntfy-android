package io.heckel.ntfy.util

import org.junit.Assert.*
import org.junit.Test

/** Covers the card-JSON → plain-text flattening used for system notifications. */
class SummarizeCardTest {

    @Test fun `kv block flattens to key value lines`() {
        val json = """{"type":"kv","rows":[{"key":"Bucket","value":"r2:backup"},{"key":"Size","value":"8GiB"}]}"""
        assertEquals("Bucket: r2:backup\nSize: 8GiB", summarizeCard(json))
    }

    @Test fun `list block flattens to bullets`() {
        assertEquals("• a\n• b", summarizeCard("""{"type":"list","items":["a","b"]}"""))
    }

    @Test fun `sections concatenate kv markdown and list`() {
        val json = """{"type":"sections","blocks":[
            {"type":"kv","rows":[{"key":"K","value":"V"}]},
            {"type":"markdown","text":"hello"},
            {"type":"list","items":["x"]}]}"""
        assertEquals("K: V\nhello\n• x", summarizeCard(json))
    }

    @Test fun `rows with empty key are skipped`() {
        assertEquals("K: V", summarizeCard("""{"type":"kv","rows":[{"key":"","value":"x"},{"key":"K","value":"V"}]}"""))
    }

    @Test fun `invalid json returns null`() {
        assertNull(summarizeCard("not json"))
        assertNull(summarizeCard(""))
    }

    @Test fun `non-card object with no known blocks returns null`() {
        assertNull(summarizeCard("""{"type":"chart","series":[1,2,3]}"""))
    }
}
