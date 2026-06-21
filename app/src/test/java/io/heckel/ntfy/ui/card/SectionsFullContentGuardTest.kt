package io.heckel.ntfy.ui.card

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Source-level guard: SectionsBlockRenderer must not introduce truncation,
 * ellipsis, fixed body height, or compact affordances (AC 8).
 *
 * Pure JVM test — reads source files directly.
 */
class SectionsFullContentGuardTest {

    private fun readSource(relativePath: String): String {
        val candidates = listOf(relativePath, "../$relativePath")
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("File not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private val rendererSource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/card/body/SectionsBlockRenderer.kt")
    }

    private val sectionsXml: String by lazy {
        readSource("app/src/main/res/layout/view_card_sections.xml")
    }

    @Test
    fun `SectionsBlockRenderer does not set maxLines to a finite value`() {
        // Setting maxLines = Int.MAX_VALUE is acceptable (it means no limit).
        // Setting a finite integer literal is a violation.
        val lines = rendererSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
        val truncatingLine = lines.firstOrNull { line ->
            val noMaxInt = line.replace("Int.MAX_VALUE", "")
            Regex("""maxLines\s*=\s*\d+""").containsMatchIn(noMaxInt)
        }
        assertFalse(
            "SectionsBlockRenderer must not set a finite maxLines (found: $truncatingLine)",
            truncatingLine != null
        )
    }

    @Test
    fun `SectionsBlockRenderer does not set ellipsize`() {
        val codeLines = rendererSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        // "ellipsize = null" is the allowed reset form; anything else is a violation
        val withoutReset = codeLines.replace("ellipsize = null", "")
        assertFalse(
            "SectionsBlockRenderer must not set a non-null ellipsize",
            withoutReset.contains("ellipsize")
        )
    }

    @Test
    fun `view_card_sections xml does not set android maxLines`() {
        assertFalse(
            "view_card_sections.xml must not set maxLines",
            sectionsXml.contains("maxLines")
        )
    }

    @Test
    fun `view_card_sections xml does not set a fixed layout height in dp`() {
        // WRAP_CONTENT is required; a fixed dp height would clip content.
        assertFalse(
            "view_card_sections.xml must not use a fixed dp height",
            Regex("""layout_height\s*=\s*"\d+dp"""").containsMatchIn(sectionsXml)
        )
    }

    @Test
    fun `view_card_sections xml does not reference ellipsize attribute`() {
        assertFalse(
            "view_card_sections.xml must not set ellipsize",
            sectionsXml.contains("ellipsize")
        )
    }
}
