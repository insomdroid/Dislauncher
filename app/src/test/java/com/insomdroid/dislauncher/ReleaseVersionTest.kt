package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun parsesStableVersionsWithOptionalTagPrefix() {
        assertEquals(SemanticVersion(1, 3, 6), SemanticVersion.parse("1.3.6"))
        assertEquals(SemanticVersion(1, 3, 6), SemanticVersion.parse("v1.3.6"))
    }

    @Test
    fun parsesNightlyDevelopmentVersions() {
        assertEquals(
            SemanticVersion(1, 3, 7, "dev.854"),
            SemanticVersion.parse("1.3.7-dev.854")
        )
    }

    @Test
    fun rejectsMalformedAndOverflowingVersions() {
        listOf("", "1.3", "v1.3.6.1", "01.3.6", "1.x.6", "999999999999.1.1")
            .forEach { assertNull(SemanticVersion.parse(it)) }
    }

    @Test
    fun comparesMajorMinorAndPatchNumerically() {
        assertTrue(SemanticVersion.parse("2.0.0")!! > SemanticVersion.parse("1.99.99")!!)
        assertTrue(SemanticVersion.parse("1.4.0")!! > SemanticVersion.parse("1.3.99")!!)
        assertTrue(SemanticVersion.parse("1.3.10")!! > SemanticVersion.parse("1.3.9")!!)
    }

    @Test
    fun stableReleaseIsNewerThanPrereleaseOfSameVersion() {
        assertTrue(SemanticVersion.parse("1.3.7")!! > SemanticVersion.parse("1.3.7-dev.854")!!)
    }

    @Test
    fun prereleaseIsOlderThanStableOfSameVersion() {
        assertTrue(SemanticVersion.parse("1.3.7-rc.1")!! < SemanticVersion.parse("1.3.7")!!)
    }

    @Test
    fun newerNightlyIsNotDowngradedToOlderStableRelease() {
        val installed = SemanticVersion.parse("1.3.7-dev.854")!!
        val latestStable = SemanticVersion.parse("1.3.6")!!
        assertFalse(latestStable > installed)
    }

    @Test
    fun stableUpdateIsDetected() {
        val installed = SemanticVersion.parse("1.3.5")!!
        val latestStable = SemanticVersion.parse("1.3.6")!!
        assertTrue(latestStable > installed)
    }
}
