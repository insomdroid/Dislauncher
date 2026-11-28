package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerLayoutTest {
    @Test
    fun listLayoutKeepsOriginalSizes() {
        assertEquals(18, drawerIconSizeDp("list", 0))
        assertEquals(25, drawerLabelSizeSp("list", 0))
        assertEquals(1, drawerColumnCount("list", 0, 18, 1000))
        assertEquals(1, drawerColumnCount("list", 5, 18, 1000))
    }

    @Test
    fun explicitSizesOverrideLayoutDefaults() {
        assertEquals(40, drawerIconSizeDp("grid", 40))
        assertEquals(16, drawerLabelSizeSp("grid", 16))
        assertEquals(48, drawerIconSizeDp("grid", 0))
    }

    @Test
    fun availableWidthAccountsForPaddingAndIndexStrip() {
        assertEquals(315, drawerAvailableWidthDp(360))
        assertEquals(525, drawerAvailableWidthDp(600))
        assertEquals(0, drawerAvailableWidthDp(10))
    }

    @Test
    fun autoColumnsAdaptToWidth() {
        // Phone-width and tablet-width windows.
        assertEquals(3, drawerColumnCount("grid", 0, 48, drawerAvailableWidthDp(360)))
        assertEquals(6, drawerColumnCount("grid", 0, 48, drawerAvailableWidthDp(600)))
        assertEquals(6, drawerColumnCount("icons_only", 0, 56, drawerAvailableWidthDp(600)))
        assertEquals(1, drawerColumnCount("multi_list", 0, 28, drawerAvailableWidthDp(360)))
        assertEquals(2, drawerColumnCount("multi_list", 0, 28, drawerAvailableWidthDp(600)))
        assertEquals(3, drawerColumnCount("multi_list", 0, 28, drawerAvailableWidthDp(840)))
    }

    @Test
    fun manualColumnsAreHonouredWithinLimits() {
        assertEquals(5, drawerColumnCount("grid", 5, 48, 300))
        assertEquals(4, drawerColumnCount("multi_list", 8, 28, 300))
        assertEquals(8, drawerColumnCount("grid", 99, 48, 300))
    }

    @Test
    fun largerIconsReduceAutoColumns() {
        val small = drawerColumnCount("grid", 0, 48, 525)
        val large = drawerColumnCount("grid", 0, 80, 525)
        assert(large < small)
    }

    @Test
    fun normalizesDrawerPreferences() {
        assertEquals("grid", normalizeDrawerLayout("grid"))
        assertEquals("list", normalizeDrawerLayout("carousel"))
        assertEquals(0, normalizeDrawerColumns(-3))
        assertEquals(2, normalizeDrawerColumns(1))
        assertEquals(8, normalizeDrawerColumns(50))
        assertEquals(0, normalizeDrawerIconSize(0))
        assertEquals(DRAWER_ICON_SIZE_MIN, normalizeDrawerIconSize(3))
        assertEquals(DRAWER_ICON_SIZE_MAX, normalizeDrawerIconSize(500))
        assertEquals(DRAWER_LABEL_SIZE_MAX, normalizeDrawerLabelSize(500))
        assertEquals("bold", normalizeLabelWeight("bold"))
        assertEquals("normal", normalizeLabelWeight("heavy"))
        assertEquals("accent", normalizeLabelColor("accent"))
        assertEquals("default", normalizeLabelColor("neon"))
        assertEquals(HOME_LABEL_SIZE_MIN, normalizeHomeLabelSize(1))
        assertEquals(HOME_ICON_SIZE_MAX, normalizeHomeIconSize(999))
    }
}
