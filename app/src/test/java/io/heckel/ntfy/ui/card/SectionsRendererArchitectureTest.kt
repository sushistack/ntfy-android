package io.heckel.ntfy.ui.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture guards for Story 3.7 (AC 9, 10).
 *
 * Verifies SectionsBlockRenderer and SectionsSpecParser satisfy isolation rules:
 * - No Compose dependency.
 * - No Activity, DetailActivity, DetailAdapter, Repository, or CoroutineScope reference.
 * - fragment_detail_item.xml is not modified.
 * - No hard-coded 12dp dimension (must use @dimen/spacing_3).
 */
class SectionsRendererArchitectureTest {

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            relativePath,
            "../$relativePath",
        )
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("File not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private val rendererSource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/card/body/SectionsBlockRenderer.kt")
    }

    private val parserSource: String by lazy {
        readSource("app/src/main/java/io/heckel/ntfy/ui/card/body/SectionsSpecParser.kt")
    }

    private val fragmentDetailItemXml: String by lazy {
        readSource("app/src/main/res/layout/fragment_detail_item.xml")
    }

    // -------------------------------------------------------------------------
    // No Compose (AC 9)
    // -------------------------------------------------------------------------

    @Test
    fun `SectionsBlockRenderer does not import Compose`() {
        assertFalse(
            "SectionsBlockRenderer must not import androidx.compose",
            rendererSource.contains("androidx.compose")
        )
    }

    @Test
    fun `SectionsSpecParser does not import Compose`() {
        assertFalse(
            "SectionsSpecParser must not import androidx.compose",
            parserSource.contains("androidx.compose")
        )
    }

    // -------------------------------------------------------------------------
    // No Activity / adapter / repository coupling (AC 9, 10)
    // -------------------------------------------------------------------------

    @Test
    fun `SectionsBlockRenderer does not import Activity`() {
        assertFalse(
            "SectionsBlockRenderer must not import android.app.Activity",
            rendererSource.contains("import android.app.Activity")
        )
    }

    @Test
    fun `SectionsBlockRenderer constructor does not accept Activity`() {
        val constructorBlock = rendererSource
            .substringAfter("class SectionsBlockRenderer(")
            .substringBefore(") :")
            .substringBefore(") {")
        assertFalse(
            "SectionsBlockRenderer constructor must not accept Activity",
            constructorBlock.contains("Activity")
        )
    }

    @Test
    fun `SectionsBlockRenderer does not reference DetailActivity in code`() {
        val codeLines = rendererSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertFalse(
            "SectionsBlockRenderer must not reference DetailActivity",
            codeLines.contains("DetailActivity")
        )
    }

    @Test
    fun `SectionsBlockRenderer does not reference DetailAdapter`() {
        val codeLines = rendererSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")
        assertFalse(
            "SectionsBlockRenderer must not reference DetailAdapter",
            codeLines.contains("DetailAdapter")
        )
    }

    @Test
    fun `SectionsBlockRenderer does not reference Repository`() {
        val constructorBlock = rendererSource
            .substringAfter("class SectionsBlockRenderer(")
            .substringBefore(") :")
            .substringBefore(") {")
        assertFalse(
            "SectionsBlockRenderer must not accept Repository",
            constructorBlock.contains("Repository")
        )
    }

    @Test
    fun `SectionsBlockRenderer does not reference CoroutineScope`() {
        assertFalse(
            "SectionsBlockRenderer must not reference CoroutineScope",
            rendererSource.contains("CoroutineScope")
        )
    }

    // -------------------------------------------------------------------------
    // No hard-coded 12dp (AC 2, 9)
    // -------------------------------------------------------------------------

    @Test
    fun `SectionsBlockRenderer uses spacing_3 token not hard-coded 12dp`() {
        assertFalse(
            "SectionsBlockRenderer must not hard-code 12.dp or 12f for spacing; use R.dimen.spacing_3",
            Regex("""[^a-zA-Z0-9_]12\.dp""").containsMatchIn(rendererSource)
        )
    }

    // -------------------------------------------------------------------------
    // fragment_detail_item.xml is not modified by Story 3.7 (AC 10)
    // -------------------------------------------------------------------------

    @Test
    fun `fragment_detail_item does not reference sections or SectionsBlockRenderer`() {
        assertFalse(
            "fragment_detail_item.xml must not reference sections renderer",
            fragmentDetailItemXml.contains("sections")
        )
        assertFalse(
            "fragment_detail_item.xml must not reference SectionsBlockRenderer",
            fragmentDetailItemXml.contains("SectionsBlockRenderer")
        )
    }

    @Test
    fun `fragment_detail_item xml exists and is non-empty`() {
        assertTrue(
            "fragment_detail_item.xml must be non-empty (Story 2.1 shell must be intact)",
            fragmentDetailItemXml.isNotBlank()
        )
    }

    // -------------------------------------------------------------------------
    // SectionsSpecParser uses only approved libraries (AC 9)
    // -------------------------------------------------------------------------

    @Test
    fun `SectionsSpecParser does not import kotlinx serialization`() {
        assertFalse(
            "SectionsSpecParser must not import kotlinx.serialization",
            parserSource.contains("kotlinx.serialization")
        )
    }

    @Test
    fun `SectionsSpecParser does not import Moshi`() {
        assertFalse(
            "SectionsSpecParser must not import Moshi",
            parserSource.contains("com.squareup.moshi")
        )
    }
}
