package io.heckel.ntfy.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Architecture guard: verifies MessageCardBinder does not reference
 * Activity, DetailActivity, DetailAdapter, or Compose.
 * Runs on the JVM without an Android device.
 */
class MessageCardArchitectureTest {

    private val binderSource: String by lazy {
        val candidates = listOf(
            "app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt",
            "../app/src/main/java/io/heckel/ntfy/ui/MessageCardBinder.kt",
        )
        candidates.map { File(it) }.firstOrNull { it.exists() }?.readText()
            ?: error("MessageCardBinder.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun `binder does not import android app Activity`() {
        // The import android.app.Activity is the definitive violation — anything less is incidental text
        assertFalse(
            "MessageCardBinder must not import android.app.Activity",
            binderSource.contains("import android.app.Activity")
        )
        assertFalse(
            "MessageCardBinder must not cast context to Activity",
            binderSource.contains("as Activity")
        )
        // No constructor/field parameter of type Activity (standalone word boundary check)
        val noAllowed = binderSource
            .replace("ActivityNotFoundException", "__EXCEPTION__")
            .replace("startActivity", "__START__")
            .replace("DetailActivity", "__DETAIL_ACTIVITY__")
        assertFalse(
            "MessageCardBinder must not accept or store an Activity type",
            Regex("""\bActivity\b""").containsMatchIn(noAllowed)
        )
    }

    @Test
    fun `binder does not reference DetailActivity in non-comment code`() {
        val codeLines = binderSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }
            .joinToString("\n")
        assertFalse(
            "MessageCardBinder code must not reference DetailActivity",
            codeLines.contains("DetailActivity")
        )
    }

    @Test
    fun `binder does not reference DetailAdapter in non-comment code`() {
        val codeLines = binderSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }
            .joinToString("\n")
        assertFalse(
            "MessageCardBinder code must not reference DetailAdapter",
            codeLines.contains("DetailAdapter")
        )
    }

    @Test
    fun `binder does not reference Compose`() {
        assertFalse(
            "MessageCardBinder must not import Compose",
            binderSource.contains("androidx.compose")
        )
    }

    @Test
    fun `binder does not use findViewTreeLifecycleOwner`() {
        assertFalse(
            "MessageCardBinder must not discover host via findViewTreeLifecycleOwner",
            binderSource.contains("findViewTreeLifecycleOwner")
        )
    }

    @Test
    fun `binder does not store Repository or CoroutineScope as field`() {
        // Repository and CoroutineScope must not appear as constructor params or class fields
        val constructorBlock = binderSource.substringAfter("class MessageCardBinder(")
            .substringBefore(") {")
        assertFalse(
            "MessageCardBinder constructor must not take Repository",
            constructorBlock.contains("Repository")
        )
        assertFalse(
            "MessageCardBinder constructor must not take CoroutineScope",
            constructorBlock.contains("CoroutineScope")
        )
    }
}
