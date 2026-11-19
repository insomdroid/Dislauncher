package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderAssignmentUtilsTest {
    @Test
    fun returnsOnlyPackagesAssignedToRequestedFolder() {
        val assignments = setOf(
            "com.example.mail\tWork",
            "com.example.calendar\tWork",
            "com.example.music\tPersonal",
            "com.example.notes\tWork"
        )

        assertEquals(
            setOf("com.example.mail", "com.example.calendar", "com.example.notes"),
            assignedPackagesForFolder(assignments, "Work")
        )
    }

    @Test
    fun ignoresMalformedAndBlankAssignments() {
        val assignments = setOf(
            "missing-separator",
            "\tWork",
            "com.example.mail\t",
            "com.example.calendar\tWork\textra",
            "com.example.notes\tWork"
        )

        assertEquals(setOf("com.example.notes"), assignedPackagesForFolder(assignments, "Work"))
    }

    @Test
    fun folderMatchingIsExact() {
        val assignments = setOf(
            "com.example.one\tWork",
            "com.example.two\twork",
            "com.example.three\tWork "
        )

        assertEquals(setOf("com.example.one"), assignedPackagesForFolder(assignments, "Work"))
    }

    @Test
    fun returnsEmptySetWhenNoPackageMatches() {
        assertEquals(emptySet<String>(), assignedPackagesForFolder(setOf("com.example.app\tPersonal"), "Work"))
    }
}
