package ir.taghvimkari.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun newerVersionDetected() {
        assertTrue(Version.isNewer("v1.2.0", "1.1.0"))
        assertTrue(Version.isNewer("1.10.0", "1.9.0"))
        assertTrue(Version.isNewer("v2.0.0-beta", "1.9"))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(Version.isNewer("v1.1.0", "1.1.0"))
        assertFalse(Version.isNewer("v1.1", "1.1.0"))
        assertFalse(Version.isNewer("1.0.9", "1.1.0"))
    }
}
