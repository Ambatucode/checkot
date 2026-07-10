package com.app.checkot.utils

import com.app.checkot.model.Booking
import com.app.checkot.model.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingUtilsTest {

    // ---- parseTimeSlotToHourMinute ----

    @Test
    fun `parseTimeSlotToHourMinute handles regular AM and PM`() {
        assertEquals(9 to 0, BookingUtils.parseTimeSlotToHourMinute("09:00 AM"))
        assertEquals(13 to 30, BookingUtils.parseTimeSlotToHourMinute("01:30 PM"))
        assertEquals(23 to 45, BookingUtils.parseTimeSlotToHourMinute("11:45 PM"))
    }

    @Test
    fun `parseTimeSlotToHourMinute handles the noon and midnight edge cases`() {
        // 12 PM is noon -> stays hour 12, not 24
        assertEquals(12 to 0, BookingUtils.parseTimeSlotToHourMinute("12:00 PM"))
        // 12 AM is midnight -> hour 0, not 12
        assertEquals(0 to 30, BookingUtils.parseTimeSlotToHourMinute("12:30 AM"))
    }

    // ---- parseTimeSlotToMinutesSince9AM ----

    @Test
    fun `parseTimeSlotToMinutesSince9AM is zero at the start of the booking day`() {
        assertEquals(0, BookingUtils.parseTimeSlotToMinutesSince9AM("09:00 AM"))
        assertEquals(30, BookingUtils.parseTimeSlotToMinutesSince9AM("09:30 AM"))
    }

    @Test
    fun `parseTimeSlotToMinutesSince9AM handles afternoon slots`() {
        assertEquals(180, BookingUtils.parseTimeSlotToMinutesSince9AM("12:00 PM"))
        assertEquals(420, BookingUtils.parseTimeSlotToMinutesSince9AM("04:00 PM"))
    }

    // ---- parseDurationMinutes ----

    @Test
    fun `parseDurationMinutes parses minute strings`() {
        assertEquals(30, BookingUtils.parseDurationMinutes("30 mins"))
        assertEquals(45, BookingUtils.parseDurationMinutes("45 mins"))
    }

    @Test
    fun `parseDurationMinutes parses hour strings including fractional hours`() {
        assertEquals(60, BookingUtils.parseDurationMinutes("1 hour"))
        assertEquals(90, BookingUtils.parseDurationMinutes("1.5 hours"))
        assertEquals(120, BookingUtils.parseDurationMinutes("2 hours"))
    }

    @Test
    fun `parseDurationMinutes falls back to 30 for unrecognized strings`() {
        // ServiceType.CUSTOM reports duration "N/A"
        assertEquals(30, BookingUtils.parseDurationMinutes("N/A"))
    }

    // ---- totalDurationMinutes ----

    @Test
    fun `totalDurationMinutes sums every selected service`() {
        val total = BookingUtils.totalDurationMinutes(
            listOf(ServiceType.BASIC_WASH, ServiceType.PREMIUM_WASH) // 30 + 45
        )
        assertEquals(75, total)
    }

    @Test
    fun `totalDurationMinutes is zero for no services`() {
        assertEquals(0, BookingUtils.totalDurationMinutes(emptyList()))
    }

    // ---- computeBusyRanges / hasFreeBay ----

    private fun bookingAt(timeSlot: String, service: ServiceType = ServiceType.BASIC_WASH) =
        Booking(timeSlot = timeSlot, services = listOf(service))

    @Test
    fun `two overlapping bookings fully occupy two bays`() {
        // Both 30-min BASIC_WASH slots starting at the same time overlap each
        // other, so each needs its own bay.
        val bookings = listOf(bookingAt("09:00 AM"), bookingAt("09:00 AM"))

        val twoBays = BookingUtils.computeBusyRanges(bookings, bayCount = 2)
        // Both bays are now occupied by the two existing bookings — a third
        // booking at the same time slot has nowhere to go.
        assertFalse(BookingUtils.hasFreeBay(twoBays, start = 0, end = 30))
        // A booking at a different, non-conflicting time still fits.
        assertTrue(BookingUtils.hasFreeBay(twoBays, start = 30, end = 60))

        // With only one bay, there's even less room for a new booking at the
        // same busy time.
        val oneBay = BookingUtils.computeBusyRanges(bookings, bayCount = 1)
        assertFalse(BookingUtils.hasFreeBay(oneBay, start = 0, end = 30))
    }

    @Test
    fun `non-overlapping bookings can share a single bay`() {
        val bookings = listOf(bookingAt("09:00 AM"), bookingAt("10:00 AM"))
        val ranges = BookingUtils.computeBusyRanges(bookings, bayCount = 1)

        // A new booking overlapping either existing one should find no free bay.
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 0, end = 30))
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 60, end = 90))
        // A new booking in the untouched gap between them should still fit.
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 30, end = 60))
    }

    @Test
    fun `back-to-back bookings do not count as overlapping`() {
        // Existing booking occupies [0, 30). A new one starting exactly at 30 should be free.
        val ranges = BookingUtils.computeBusyRanges(listOf(bookingAt("09:00 AM")), bayCount = 1)
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 30, end = 60))
    }

    @Test
    fun `bay assignment is independent of input order`() {
        // Same three bookings, fed in two different orders (simulating Firestore's
        // unordered query results). With bayCount=1, a and c conflict with each other,
        // so whichever is processed second gets silently dropped from the packing —
        // without the internal sortedBy, that "second" booking depends on input order,
        // which flips the answer for a probe overlapping only 'a'. This is a concrete
        // regression test for the sortedBy fix in computeBusyRanges.
        val a = bookingAt("09:00 AM", ServiceType.DETAILING)   // 0-120 (09:00-11:00)
        val b = bookingAt("11:30 AM", ServiceType.BASIC_WASH)  // 150-180, overlaps neither
        val c = bookingAt("10:00 AM", ServiceType.BASIC_WASH)  // 60-90, overlaps a only

        val forwardOrder = listOf(a, b, c)
        val shuffledOrder = listOf(c, a, b)

        for (bayCount in 1..3) {
            val forward = BookingUtils.hasFreeBay(
                BookingUtils.computeBusyRanges(forwardOrder, bayCount),
                start = 30, end = 60 // probe: 09:30-10:00, overlaps only 'a'
            )
            val shuffled = BookingUtils.hasFreeBay(
                BookingUtils.computeBusyRanges(shuffledOrder, bayCount),
                start = 30, end = 60
            )
            assertEquals("bayCount=$bayCount should agree regardless of input order", forward, shuffled)
        }

        // And pin down the actual (correct) answer for bayCount=1: 'a' occupies the
        // only bay, so a new 09:30-10:00 booking must be rejected.
        val ranges = BookingUtils.computeBusyRanges(forwardOrder, bayCount = 1)
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 30, end = 60))
    }
}
