package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMeetingUrlTest {
    @Test
    fun acceptsHttpsLinks() {
        assertEquals("https://meet.example.com/abc", validateCalendarMeetingUrl("  https://meet.example.com/abc  "))
    }

    @Test
    fun rejectsNonHttpsSchemes() {
        assertNull(validateCalendarMeetingUrl("http://meet.example.com/abc"))
        assertNull(validateCalendarMeetingUrl("javascript:alert(1)"))
        assertNull(validateCalendarMeetingUrl("intent://example#Intent;end"))
    }

    @Test
    fun rejectsUserInfoAndMissingHost() {
        assertNull(validateCalendarMeetingUrl("https://user:secret@meet.example.com/abc"))
        assertNull(validateCalendarMeetingUrl("https:///abc"))
    }

    @Test
    fun rejectsBlankAndNull() {
        assertNull(validateCalendarMeetingUrl(null))
        assertNull(validateCalendarMeetingUrl("   "))
    }
}
