package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMeetingUrlTest {
    @Test
    fun acceptsOnlyHttpsUrlsWithAValidHost() {
        assertEquals("https://meet.example.com/room", validateCalendarMeetingUrl("https://meet.example.com/room"))
        assertNull(validateCalendarMeetingUrl("http://meet.example.com/room"))
        assertNull(validateCalendarMeetingUrl("javascript:alert(1)"))
        assertNull(validateCalendarMeetingUrl("https:///missing-host"))
        assertNull(validateCalendarMeetingUrl("https://user:pass@meet.example.com/room"))
        assertNull(validateCalendarMeetingUrl(null))
    }

    @Test
    fun trimsWhitespaceButRejectsNonHttpsSchemes() {
        assertEquals("https://example.com/room", validateCalendarMeetingUrl("  https://example.com/room  "))
        assertNull(validateCalendarMeetingUrl("ftp://example.com/file"))
    }
}
