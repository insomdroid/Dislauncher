package com.insomdroid.dislauncher

/**
 * Folder assignments are stored as "<packageName>\t<folderName>" for compatibility
 * with existing SharedPreferences. Ignore malformed entries rather than exposing them.
 */
internal fun assignedPackagesForFolder(assignments: Set<String>, folderName: String): Set<String> =
    assignments.mapNotNullTo(mutableSetOf()) { assignment ->
        val separator = assignment.indexOf('\t')
        if (separator <= 0 || assignment.substring(separator + 1) != folderName) {
            null
        } else {
            assignment.substring(0, separator).takeIf { it.isNotBlank() }
        }
    }
