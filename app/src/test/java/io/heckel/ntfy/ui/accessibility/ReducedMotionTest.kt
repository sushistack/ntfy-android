package io.heckel.ntfy.ui.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun `animators enabled means reduced motion off`() {
        assertFalse(ReducedMotion.isEnabledForAnimatorsEnabled(animatorsEnabled = true))
    }

    @Test
    fun `animators disabled means reduced motion on`() {
        assertTrue(ReducedMotion.isEnabledForAnimatorsEnabled(animatorsEnabled = false))
    }
}
