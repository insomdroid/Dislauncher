package com.insomdroid.dislauncher

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenUtilsTest {
    @Test
    fun formatsEnglishOrdinalDaySuffixesCorrectly() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val cases = mapOf(
                1 to "1st",
                2 to "2nd",
                3 to "3rd",
                4 to "4th",
                11 to "11th",
                12 to "12th",
                13 to "13th",
                21 to "21st",
                22 to "22nd",
                23 to "23rd",
                31 to "31st"
            )

            cases.forEach { (day, suffix) ->
                assertTrue(
                    "Expected day $day to use suffix $suffix",
                    formatHomeDate(LocalDate.of(2026, 1, day)).contains(suffix)
                )
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun formatsNonEnglishDateWithoutEnglishOrdinalSuffixes() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("id-ID"))
            val formatted = formatHomeDate(LocalDate.of(2026, 1, 21))

            assertTrue(formatted.contains("21"))
            assertTrue(formatted.contains("Januari", ignoreCase = true))
            assertTrue(!formatted.contains("21st"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
