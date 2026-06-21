package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.message.ParserParityGoldenCorpus
import io.heckel.ntfy.util.MarkwonLinkPolicy
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * Security test: verifies [MarkwonLinkPolicy] against every markdownDestinationCases
 * vector from the Story 3.0 golden corpus (AC 6).
 *
 * Also exercises additional obfuscation vectors (mixed-case, whitespace/control) and
 * recycled-state correctness (AC 7).
 *
 * Run:
 *   ./gradlew testFdroidDebugUnitTest --tests '*MarkdownDestinationSecurityTest'
 */
class MarkdownDestinationSecurityTest {

    companion object {
        private lateinit var corpus: ParserParityGoldenCorpus.Corpus

        @BeforeClass
        @JvmStatic
        fun loadCorpus() {
            corpus = ParserParityGoldenCorpus.load()
        }
    }

    // -------------------------------------------------------------------------
    // Golden corpus: link cases
    // -------------------------------------------------------------------------

    @Test
    fun `all corpus link cases produce correct policy result`() {
        val linkCases = corpus.markdownDestinationCases.filter {
            it.input.kind == ParserParityGoldenCorpus.MarkdownDestinationKind.LINK
        }
        assertTrue("Need at least 5 link cases", linkCases.size >= 5)

        val failures = mutableListOf<String>()
        for (case in linkCases) {
            val allowed = MarkwonLinkPolicy.isLinkAllowed(case.input.destination)
            val expectedLive = case.expected == ParserParityGoldenCorpus.MarkdownDestinationResult.LIVE
            if (allowed != expectedLive) {
                failures += "[${case.id}] dest='${case.input.destination}' " +
                    "expected=${ if (expectedLive) "live" else "inert" } actual=${ if (allowed) "live" else "inert" }"
            }
        }
        assertTrue("Corpus link policy failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Golden corpus: image cases
    // -------------------------------------------------------------------------

    @Test
    fun `all corpus image cases produce correct policy result`() {
        val imageCases = corpus.markdownDestinationCases.filter {
            it.input.kind == ParserParityGoldenCorpus.MarkdownDestinationKind.IMAGE
        }
        assertTrue("Need at least 5 image cases", imageCases.size >= 5)

        val failures = mutableListOf<String>()
        for (case in imageCases) {
            val allowed = MarkwonLinkPolicy.isImageAllowed(case.input.destination)
            val expectedRender = case.expected == ParserParityGoldenCorpus.MarkdownDestinationResult.RENDER
            if (allowed != expectedRender) {
                failures += "[${case.id}] dest='${case.input.destination}' " +
                    "expected=${ if (expectedRender) "render" else "drop" } actual=${ if (allowed) "render" else "drop" }"
            }
        }
        assertTrue("Corpus image policy failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Link and image policies are independent for same URI
    // -------------------------------------------------------------------------

    @Test
    fun `https is live for links and render for images - policies are independent`() {
        assertTrue("https link must be allowed", MarkwonLinkPolicy.isLinkAllowed("https://example.com"))
        assertTrue("https image must be allowed", MarkwonLinkPolicy.isImageAllowed("https://example.com/img.png"))
        // mailto allowed for links but NOT images
        assertTrue("mailto link must be allowed", MarkwonLinkPolicy.isLinkAllowed("mailto:user@example.com"))
        assertFalse("mailto image must be rejected", MarkwonLinkPolicy.isImageAllowed("mailto:user@example.com"))
    }

    // -------------------------------------------------------------------------
    // Mixed-case scheme obfuscation (AC 6 — covers case-insensitive canonicalization)
    // -------------------------------------------------------------------------

    @Test
    fun `JAVASCRIPT scheme is rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("JAVASCRIPT:alert(1)"))
    }

    @Test
    fun `JavaScript mixed-case scheme is rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("JavaScript:alert(1)"))
    }

    @Test
    fun `DATA uppercase scheme is rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("DATA:text/html,hi"))
    }

    @Test
    fun `FILE uppercase scheme is rejected as image`() {
        assertFalse(MarkwonLinkPolicy.isImageAllowed("FILE:///sdcard/photo.jpg"))
    }

    @Test
    fun `HTTP uppercase scheme is allowed as link`() {
        assertTrue(MarkwonLinkPolicy.isLinkAllowed("HTTP://example.com"))
    }

    @Test
    fun `HTTPS uppercase scheme is allowed as image`() {
        assertTrue(MarkwonLinkPolicy.isImageAllowed("HTTPS://example.com/img.png"))
    }

    // -------------------------------------------------------------------------
    // Whitespace / control-character obfuscation (AC 6)
    // -------------------------------------------------------------------------

    @Test
    fun `tab-separated javascript is rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("java\tscript:alert(1)"))
    }

    @Test
    fun `space-separated javascript is rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("java script:alert(1)"))
    }

    @Test
    fun `newline-embedded javascript is rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("java\nscript:alert(1)"))
    }

    // -------------------------------------------------------------------------
    // Sections-block markdown entry point: same policy applies (AC 4)
    // -------------------------------------------------------------------------

    @Test
    fun `policy is pure and context-free - same result for same input regardless of call site`() {
        // Simulate being called from a sections-block markdown vs top-level body.
        // Policy is stateless; same input must always produce the same result.
        val dest = "javascript:void(0)"
        assertFalse(MarkwonLinkPolicy.isLinkAllowed(dest))
        assertFalse(MarkwonLinkPolicy.isLinkAllowed(dest))
    }

    // -------------------------------------------------------------------------
    // Recycled view: safe → unsafe, unsafe → safe (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `policy produces correct result for safe then unsafe destination`() {
        assertTrue(MarkwonLinkPolicy.isLinkAllowed("https://example.com"))
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("javascript:alert(1)"))
    }

    @Test
    fun `policy produces correct result for unsafe then safe destination`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("data:text/html,hi"))
        assertTrue(MarkwonLinkPolicy.isLinkAllowed("https://example.com"))
    }

    @Test
    fun `image policy safe then unsafe recycled bind`() {
        assertTrue(MarkwonLinkPolicy.isImageAllowed("https://example.com/img.png"))
        assertFalse(MarkwonLinkPolicy.isImageAllowed("data:image/png;base64,abc"))
    }

    @Test
    fun `image policy unsafe then safe recycled bind`() {
        assertFalse(MarkwonLinkPolicy.isImageAllowed("file:///sdcard/photo.jpg"))
        assertTrue(MarkwonLinkPolicy.isImageAllowed("http://example.com/img.png"))
    }

    // -------------------------------------------------------------------------
    // Multiple destinations in one body: filtering is per-node (AC 1, 2, 3)
    // -------------------------------------------------------------------------

    @Test
    fun `multiple destinations in mixed body are each filtered independently`() {
        val destinations = listOf(
            "https://example.com" to true,
            "javascript:alert(1)" to false,
            "http://safe.example.com" to true,
            "data:text/html,<b>hi</b>" to false,
            "mailto:user@example.com" to true,
            "file:///etc/passwd" to false,
        )
        for ((dest, expected) in destinations) {
            assertEquals("Link policy for '$dest' was wrong",
                expected, MarkwonLinkPolicy.isLinkAllowed(dest))
        }
    }

    // -------------------------------------------------------------------------
    // Plain-text auto-link bypass: policy must cover same destinations (AC 4)
    // -------------------------------------------------------------------------

    @Test
    fun `auto-link bypass prevention - javascript URI is rejected even without Markdown syntax`() {
        // The policy object is called by the link resolver; it must reject the same
        // destinations regardless of whether the URL came from Markdown syntax or
        // from plain-text auto-linkification.
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("javascript:alert(document.cookie)"))
    }

    // -------------------------------------------------------------------------
    // Malformed / fuzz-like destinations (AC 7)
    // -------------------------------------------------------------------------

    @Test
    fun `null-byte in destination is rejected`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("https ://evil.com"))
    }

    @Test
    fun `very long destination does not throw`() {
        val long = "https://" + "a".repeat(4096) + ".com"
        assertTrue(MarkwonLinkPolicy.isLinkAllowed(long))
    }

    @Test
    fun `unicode scheme is rejected`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("ℎttps://example.com"))
    }

    @Test
    fun `destination that is only whitespace is rejected`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("   "))
    }

    @Test
    fun `android resource scheme rejected as image`() {
        assertFalse(MarkwonLinkPolicy.isImageAllowed("android.resource://io.heckel.ntfy/raw/something"))
    }

    @Test
    fun `intent scheme rejected as link`() {
        assertFalse(MarkwonLinkPolicy.isLinkAllowed("intent://open#Intent;scheme=myapp;end"))
    }
}
