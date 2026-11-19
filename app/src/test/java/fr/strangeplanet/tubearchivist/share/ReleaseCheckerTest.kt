package fr.strangeplanet.tubearchivist.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckerTest {
    @Test
    fun detectsNewerVersions() {
        assertTrue(isNewerVersion("1.2.0", "1.1.9"))
        assertTrue(isNewerVersion("v2.0", "1.9.9"))
        assertTrue(isNewerVersion("1.0.0", "1.0.0-rc.1"))
    }

    @Test
    fun doesNotTreatSameOrOlderVersionsAsUpdates() {
        assertFalse(isNewerVersion("1.0.0", "1.0"))
        assertFalse(isNewerVersion("1.0.0-beta.1", "1.0.0"))
        assertFalse(isNewerVersion("0.9.9", "1.0.0"))
    }
}
