package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderAssignmentUtilsTest {
    @Test
    fun returnsOnlyPackagesAssignedToRequestedFolder() {
        val assignments = setOf(
            "com.example.alpha\tWork",
            "com.example.beta\tPersonal",
            "com.example.gamma\tWork"
        )

        assertEquals(
            setOf("com.example.alpha", "com.example.gamma"),
            assignedPackagesForFolder(assignments, "Work")
        )
    }

    @Test
    fun ignoresMalformedAndBlankPackageAssignments() {
        val assignments = setOf(
            "missing-separator",
            "\tWork",
            "com.example.alpha\tWork\textra",
            "com.example.beta\tWork"
        )

        assertEquals(setOf("com.example.beta"), assignedPackagesForFolder(assignments, "Work"))
    }
}
