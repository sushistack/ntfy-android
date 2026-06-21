package io.heckel.ntfy.ui.card

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.heckel.ntfy.ui.card.body.ListSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [ListSpec] normalization.
 *
 * Covers AC 1–5 (ordered flag, item coercion, empty/missing items) and AC 6 (no truncation).
 * No Android View references — fast in JVM without Robolectric.
 */
class ListSpecNormalizationTest {

    private val gson = Gson()

    private fun parse(json: String): JsonObject =
        gson.fromJson(json, JsonObject::class.java)

    // -------------------------------------------------------------------------
    // ordered flag (AC 1, 2)
    // -------------------------------------------------------------------------

    @Test
    fun `ordered true produces ordered=true`() {
        val spec = ListSpec.from(parse("""{"type":"list","ordered":true,"items":[]}"""))
        assertTrue(spec.ordered)
    }

    @Test
    fun `ordered false produces ordered=false`() {
        val spec = ListSpec.from(parse("""{"type":"list","ordered":false,"items":[]}"""))
        assertFalse(spec.ordered)
    }

    @Test
    fun `ordered absent produces ordered=false`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[]}"""))
        assertFalse(spec.ordered)
    }

    @Test
    fun `ordered null produces ordered=false`() {
        val spec = ListSpec.from(parse("""{"type":"list","ordered":null,"items":[]}"""))
        assertFalse(spec.ordered)
    }

    @Test
    fun `ordered string true produces ordered=false (not boolean)`() {
        val spec = ListSpec.from(parse("""{"type":"list","ordered":"true","items":[]}"""))
        assertFalse(spec.ordered)
    }

    @Test
    fun `ordered number 1 produces ordered=false (not boolean)`() {
        val spec = ListSpec.from(parse("""{"type":"list","ordered":1,"items":[]}"""))
        assertFalse(spec.ordered)
    }

    // -------------------------------------------------------------------------
    // Item coercion (AC 3)
    // -------------------------------------------------------------------------

    @Test
    fun `string items remain unquoted`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":["hello","world"]}"""))
        assertEquals(listOf("hello", "world"), spec.items)
    }

    @Test
    fun `integer item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[42]}"""))
        assertEquals(listOf("42"), spec.items)
    }

    @Test
    fun `negative decimal number item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[-3.14]}"""))
        assertEquals(listOf("-3.14"), spec.items)
    }

    @Test
    fun `boolean true item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[true]}"""))
        assertEquals(listOf("true"), spec.items)
    }

    @Test
    fun `boolean false item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[false]}"""))
        assertEquals(listOf("false"), spec.items)
    }

    @Test
    fun `null item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[null]}"""))
        assertEquals(listOf("null"), spec.items)
    }

    @Test
    fun `object item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[{"key":"value"}]}"""))
        assertEquals(listOf("""{"key":"value"}"""), spec.items)
    }

    @Test
    fun `nested array item is compact JSON`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[[1,2,3]]}"""))
        assertEquals(listOf("[1,2,3]"), spec.items)
    }

    @Test
    fun `empty string item is preserved as empty string`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[""]}"""))
        assertEquals(listOf(""), spec.items)
    }

    @Test
    fun `unicode and emoji items are preserved`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":["배포 시작","🚀","한글"]}"""))
        assertEquals(listOf("배포 시작", "🚀", "한글"), spec.items)
    }

    @Test
    fun `mixed type items are coerced deterministically`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":["text",42,true,null,{"k":"v"}]}"""))
        assertEquals(listOf("text", "42", "true", "null", """{"k":"v"}"""), spec.items)
    }

    @Test
    fun `multiline string item is preserved`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":["line1\nline2"]}"""))
        assertEquals(listOf("line1\nline2"), spec.items)
    }

    // -------------------------------------------------------------------------
    // Missing / null / non-array items (AC 4)
    // -------------------------------------------------------------------------

    @Test
    fun `missing items field produces empty list`() {
        val spec = ListSpec.from(parse("""{"type":"list"}"""))
        assertEquals(emptyList<String>(), spec.items)
    }

    @Test
    fun `null items field produces empty list`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":null}"""))
        assertEquals(emptyList<String>(), spec.items)
    }

    @Test
    fun `non-array items field produces empty list`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":"not an array"}"""))
        assertEquals(emptyList<String>(), spec.items)
    }

    @Test
    fun `items as object produces empty list`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":{}}"""))
        assertEquals(emptyList<String>(), spec.items)
    }

    // -------------------------------------------------------------------------
    // Array cardinality and ordering preserved (AC 5)
    // -------------------------------------------------------------------------

    @Test
    fun `empty array produces empty items list`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":[]}"""))
        assertEquals(0, spec.items.size)
    }

    @Test
    fun `single item array produces one-element list`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":["only"]}"""))
        assertEquals(listOf("only"), spec.items)
    }

    @Test
    fun `source order is preserved for 12 items`() {
        val items = (1..12).map { "item$it" }
        val json = """{"type":"list","items":${gson.toJson(items)}}"""
        val spec = ListSpec.from(parse(json))
        assertEquals(items, spec.items)
    }

    @Test
    fun `empty string in array preserves cardinality`() {
        val spec = ListSpec.from(parse("""{"type":"list","items":["a","","b"]}"""))
        assertEquals(listOf("a", "", "b"), spec.items)
        assertEquals(3, spec.items.size)
    }

    // -------------------------------------------------------------------------
    // No cap on item count (AC 6 — no truncation)
    // -------------------------------------------------------------------------

    @Test
    fun `100 item list preserves all items`() {
        val items = (1..100).map { "item$it" }
        val json = """{"type":"list","items":${gson.toJson(items)}}"""
        val spec = ListSpec.from(parse(json))
        assertEquals(100, spec.items.size)
        assertEquals(items, spec.items)
    }
}
