package io.heckel.ntfy.ui.card

import io.heckel.ntfy.ui.card.body.KvIconResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lookup rules of ntfy-web StructuredCard KvIcon / looksLikePath (2026-11 line icons). */
class KvIconResolverTest {
    private fun same(a: String, b: String) = assertEquals(KvIconResolver.resolve(b, null), KvIconResolver.resolve(a, null))

    @Test fun exactAndAliases() {
        assertTrue(KvIconResolver.resolve("disk", null)!!.startsWith("M4 7c0-1.7"))
        same("ram", "memory"); same("fix", "exit"); same("newest", "uptime"); same("url", "ping")
    }
    @Test fun caseAndFirstWord() { same("CPU Usage", "cpu"); same("Disk I/O", "disk"); same("Load Avg", "load") }
    @Test fun iconOverridesKey() = assertEquals(KvIconResolver.resolve("ping", null), KvIconResolver.resolve("disk", "ping"))
    @Test fun unknownIsDot() {
        assertNull(KvIconResolver.resolve("Sync", null))
        assertNull(KvIconResolver.resolve("", null))
        assertNull(KvIconResolver.resolve("cpu", "UNKNOWN_XYZ"))
        assertNull(KvIconResolver.resolve(" cpu", null)) // web: leading space → first word "" (no match)
    }
    @Test fun pathChip() {
        assertTrue(KvIconResolver.looksLikePath("r2:homelab-k3s-services-backup/anytype/"))
        assertFalse(KvIconResolver.looksLikePath("a/b"))           // too short
        assertFalse(KvIconResolver.looksLikePath("Check the logs/now please")) // has spaces
        assertFalse(KvIconResolver.looksLikePath("homepage-degraded"))  // no / or :
    }
}
