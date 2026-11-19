package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderingTest {
    @Test
    fun movesNonSystemAppsBeforeSystemAppsWithoutReorderingEitherGroup() {
        val apps = listOf(
            LauncherApp("system.one", "Main", "System One", true),
            LauncherApp("user.one", "Main", "User One", false),
            LauncherApp("system.two", "Main", "System Two", true),
            LauncherApp("user.two", "Main", "User Two", false)
        )

        assertEquals(
            listOf("user.one", "user.two", "system.one", "system.two"),
            prioritizeNonSystemApps(apps).map { it.packageName }
        )
    }

    @Test
    fun preservesOriginalOrderWhenEveryAppHasSameSystemClassification() {
        val userApps = listOf(
            LauncherApp("user.one", "Main", "One", false),
            LauncherApp("user.two", "Main", "Two", false)
        )
        val systemApps = listOf(
            LauncherApp("system.one", "Main", "One", true),
            LauncherApp("system.two", "Main", "Two", true)
        )

        assertEquals(userApps, prioritizeNonSystemApps(userApps))
        assertEquals(systemApps, prioritizeNonSystemApps(systemApps))
    }

    @Test
    fun handlesEmptyAppList() {
        assertEquals(emptyList<LauncherApp>(), prioritizeNonSystemApps(emptyList()))
    }
}
