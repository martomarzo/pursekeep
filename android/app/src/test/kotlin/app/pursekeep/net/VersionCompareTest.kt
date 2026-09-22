package app.pursekeep.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newer() = assertTrue(UpdateChecker.isNewer("android-v0.2.0", "0.1.0"))
    @Test fun patch() = assertTrue(UpdateChecker.isNewer("android-v0.1.1", "0.1.0"))
    @Test fun same() = assertFalse(UpdateChecker.isNewer("android-v0.1.0", "0.1.0"))
    @Test fun older() = assertFalse(UpdateChecker.isNewer("android-v0.0.9", "0.1.0"))
    @Test fun otherTag() = assertFalse(UpdateChecker.isNewer("v1.0.0", "0.1.0"))
}
