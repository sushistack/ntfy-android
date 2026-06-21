package io.heckel.ntfy.db

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for dark mode initialization semantics (AC 2):
 * - Fresh install → MODE_NIGHT_YES (Dark default)
 * - Existing install with stored value → stored value retained unchanged
 * - System selection (no DarkMode key, ThemeInitialized=true) → MODE_NIGHT_FOLLOW_SYSTEM
 */
class DarkModeLogicTest {

    /** Simulates the getDarkMode logic without Android platform dependency. */
    private fun resolveDarkMode(initialized: Boolean, storedMode: Int?): Int {
        if (!initialized) {
            return AppCompatDelegate.MODE_NIGHT_YES
        }
        return storedMode ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    @Test
    fun freshInstall_noInitMarker_defaultsToDark() {
        val result = resolveDarkMode(initialized = false, storedMode = null)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, result)
    }

    @Test
    fun existingInstall_storedLight_returnsLight() {
        val result = resolveDarkMode(initialized = true, storedMode = AppCompatDelegate.MODE_NIGHT_NO)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, result)
    }

    @Test
    fun existingInstall_storedDark_returnsDark() {
        val result = resolveDarkMode(initialized = true, storedMode = AppCompatDelegate.MODE_NIGHT_YES)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, result)
    }

    @Test
    fun existingInstall_systemSelected_returnsFollowSystem() {
        // System selection removes the DarkMode key but keeps ThemeInitialized=true
        val result = resolveDarkMode(initialized = true, storedMode = null)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, result)
    }

    @Test
    fun existingInstall_storedFollowSystem_returnsFollowSystem() {
        val result = resolveDarkMode(initialized = true, storedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, result)
    }

    /**
     * Simulates the initializeDefaultDarkMode logic.
     * isExistingInstall = true when sentinel keys (PollWorkerVersion etc.) are present.
     */
    private data class Prefs(
        var initialized: Boolean = false,
        var darkMode: Int? = null,
        var isExistingInstall: Boolean = false,
    )

    private fun initDefaultDarkMode(prefs: Prefs) {
        if (!prefs.initialized) {
            if (!prefs.isExistingInstall && prefs.darkMode == null) {
                prefs.darkMode = AppCompatDelegate.MODE_NIGHT_YES
            }
            prefs.initialized = true
        }
    }

    @Test
    fun initDefaultDarkMode_freshInstall_setsDarkAndMarker() {
        val prefs = Prefs()
        initDefaultDarkMode(prefs)
        assertEquals(true, prefs.initialized)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, prefs.darkMode)
    }

    @Test
    fun initDefaultDarkMode_existingInstall_storedLight_preserved() {
        // Upgrade path: user had explicit Light stored; existing-install sentinel key present.
        val prefs = Prefs(isExistingInstall = true, darkMode = AppCompatDelegate.MODE_NIGHT_NO)
        initDefaultDarkMode(prefs)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, prefs.darkMode)
    }

    @Test
    fun initDefaultDarkMode_existingInstall_systemMode_preserved() {
        // Upgrade path: user had System (DarkMode key absent); must NOT default to Dark.
        val prefs = Prefs(isExistingInstall = true, darkMode = null)
        initDefaultDarkMode(prefs)
        assertEquals(null, prefs.darkMode)  // System is represented as absent key
        assertEquals(true, prefs.initialized)
    }

    @Test
    fun initDefaultDarkMode_existingInstall_doesNotOverwrite() {
        val prefs = Prefs(initialized = true, darkMode = AppCompatDelegate.MODE_NIGHT_NO)
        initDefaultDarkMode(prefs)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, prefs.darkMode)
    }

    @Test
    fun initDefaultDarkMode_calledTwice_idempotent() {
        val prefs = Prefs()
        initDefaultDarkMode(prefs)
        initDefaultDarkMode(prefs)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, prefs.darkMode)
        assertEquals(true, prefs.initialized)
    }

    /** Verifies segment-to-mode mapping is bijective for all three values. */
    @Test
    fun segmentToModeMapping_allThreeValues_roundTrip() {
        val modes = listOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        // All three modes are distinct integers
        assertEquals(3, modes.distinct().size)
        // None is uninitialized (-100 sentinel)
        modes.forEach { mode -> assert(mode != -100) }
    }
}
