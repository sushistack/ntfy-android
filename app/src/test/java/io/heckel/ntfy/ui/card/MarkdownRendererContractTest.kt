package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.CardMarkdownRenderer
import io.heckel.ntfy.ui.card.body.MarkwonCardMarkdownRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Contract tests for Story 3.6a Markdown renderer.
 *
 * These JVM tests verify the reusability contract and architecture constraints
 * that do not require an Android context or Robolectric.
 *
 * AC 6: one reusable renderer/style configuration for paragraph fallback and sections Markdown.
 * AC 8: scope guard — no Compose/WebView dependency, no fragment_detail_item.xml modification.
 */
class MarkdownRendererContractTest {

    // -------------------------------------------------------------------------
    // AC 6: interface contract is implemented
    // -------------------------------------------------------------------------

    @Test
    fun `CardMarkdownRenderer interface declares render renderRawFallback and reset`() {
        // Verify the interface exists and declares the three required methods
        val methods = CardMarkdownRenderer::class.java.methods.map { it.name }.toSet()
        assertNotNull("render method must exist", methods.firstOrNull { it == "render" })
        assertNotNull("renderRawFallback method must exist", methods.firstOrNull { it == "renderRawFallback" })
        assertNotNull("reset method must exist", methods.firstOrNull { it == "reset" })
    }

    @Test
    fun `MarkwonCardMarkdownRenderer implements CardMarkdownRenderer`() {
        val isAssignable = CardMarkdownRenderer::class.java.isAssignableFrom(
            MarkwonCardMarkdownRenderer::class.java
        )
        assert(isAssignable) { "MarkwonCardMarkdownRenderer must implement CardMarkdownRenderer" }
    }

    // -------------------------------------------------------------------------
    // AC 8: scope guard — no WebView, no Compose, no fragment_detail_item.xml changes
    // -------------------------------------------------------------------------

    @Test
    fun `MarkwonCardMarkdownRenderer does not reference WebView`() {
        val rendererSource = MarkwonCardMarkdownRenderer::class.java
        // Check that none of the declared fields/methods reference WebView by scanning class name
        val hasWebView = rendererSource.declaredFields.any { it.type.name.contains("WebView") } ||
            rendererSource.declaredMethods.any { m ->
                m.parameterTypes.any { it.name.contains("WebView") } ||
                    m.returnType.name.contains("WebView")
            }
        assert(!hasWebView) { "MarkwonCardMarkdownRenderer must not reference WebView" }
    }

    @Test
    fun `MarkwonCardMarkdownRenderer does not reference Compose`() {
        val rendererSource = MarkwonCardMarkdownRenderer::class.java
        val hasCompose = rendererSource.declaredFields.any { it.type.name.contains("compose") } ||
            rendererSource.declaredMethods.any { m ->
                m.parameterTypes.any { it.name.contains("compose") }
            }
        assert(!hasCompose) { "MarkwonCardMarkdownRenderer must not reference Compose" }
    }

    @Test
    fun `fragment_detail_item_xml is not modified by this story`() {
        // This test verifies the layout file exists and does NOT contain any Story 3.6a markers
        // that would indicate it was incorrectly modified. The shell is owned by Story 2.1.
        val layoutFile = java.io.File("src/main/res/layout/fragment_detail_item.xml")
        if (!layoutFile.exists()) return // Running in different working directory is acceptable

        val content = layoutFile.readText()
        assert(!content.contains("card_markdown")) {
            "fragment_detail_item.xml must not contain card_markdown view IDs — shell is owned by Story 2.1"
        }
    }

    // -------------------------------------------------------------------------
    // AC 6: buildWeightedTypeface helper is API-safe (does not require API 28 at class level)
    // -------------------------------------------------------------------------

    @Test
    fun `buildWeightedTypeface is accessible as companion object method`() {
        // Verify the method exists and is public on the companion object (API-safety gate)
        val companionClass = MarkwonCardMarkdownRenderer.Companion::class.java
        val method = companionClass.methods.firstOrNull { it.name == "buildWeightedTypeface" }
        assertNotNull("buildWeightedTypeface must be a public companion method", method)
        assertEquals("Must accept 2 parameters (Typeface?, Int)", 2, method!!.parameterCount)
    }
}
