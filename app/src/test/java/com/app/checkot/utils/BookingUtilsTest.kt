package com.app.checkot.utils

import com.app.checkot.model.Booking
import com.app.checkot.model.DaySlotEntry
import com.app.checkot.model.ServiceType
import com.app.checkot.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---- minutesToSlotLabel ----

    @Test
    fun `minutesToSlotLabel formats morning, noon, afternoon and midnight`() {
        assertEquals("09:00 AM", BookingUtils.minutesToSlotLabel(540))
        assertEquals("12:00 PM", BookingUtils.minutesToSlotLabel(720)) // noon stays 12 PM
        assertEquals("01:00 PM", BookingUtils.minutesToSlotLabel(780))
        assertEquals("04:00 PM", BookingUtils.minutesToSlotLabel(960))
        assertEquals("12:30 AM", BookingUtils.minutesToSlotLabel(30))  // midnight stays 12 AM
    }

    // ---- generateSlotLabels ----

    @Test
    fun `generateSlotLabels reproduces the legacy 9 to 4 window`() {
        val slots = BookingUtils.generateSlotLabels(540, 960)
        assertEquals(15, slots.size)
        assertEquals("09:00 AM", slots.first())
        assertEquals("04:00 PM", slots.last())
    }

    @Test
    fun `generateSlotLabels honours custom hours and closeMinutes is inclusive`() {
        val slots = BookingUtils.generateSlotLabels(480, 540) // 8:00 AM – 9:00 AM
        assertEquals(listOf("08:00 AM", "08:30 AM", "09:00 AM"), slots)
    }

    @Test
    fun `generateSlotLabels returns empty when close is before open`() {
        assertTrue(BookingUtils.generateSlotLabels(960, 540).isEmpty())
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
            listOf(ServiceType.EXTERIOR_WASH, ServiceType.WAX) // 30 + 45
        )
        assertEquals(75, total)
    }

    @Test
    fun `totalDurationMinutes is zero for no services`() {
        assertEquals(0, BookingUtils.totalDurationMinutes(emptyList()))
    }

    // ---- computeBusyRanges / hasFreeBay ----

    private fun bookingAt(timeSlot: String, service: ServiceType = ServiceType.EXTERIOR_WASH) =
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
        // 'a' is a long booking (ENGINE_WASH 60 + WAX 45 = 105 min) standing in for
        // the old 2-hour DETAILING; it still spans past c's start so it overlaps c.
        val a = Booking(
            timeSlot = "09:00 AM",
            services = listOf(ServiceType.ENGINE_WASH, ServiceType.WAX)
        )                                                          // 0-105 (09:00-10:45)
        val b = bookingAt("11:30 AM", ServiceType.EXTERIOR_WASH)  // 150-180, overlaps neither
        val c = bookingAt("10:00 AM", ServiceType.EXTERIOR_WASH)  // 60-90, overlaps a only

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

    // ---- findFreeBayIndex ----

    @Test
    fun `findFreeBayIndex returns the lowest-numbered open bay`() {
        val ranges = BookingUtils.computeBusyRanges(listOf(bookingAt("09:00 AM")), bayCount = 2)
        // Bay 0 is taken by the existing booking, bay 1 is free.
        assertEquals(1, BookingUtils.findFreeBayIndex(ranges, bayCount = 2, start = 0, end = 30))
    }

    @Test
    fun `findFreeBayIndex returns null when every bay conflicts`() {
        val bookings = listOf(bookingAt("09:00 AM"), bookingAt("09:00 AM"))
        val ranges = BookingUtils.computeBusyRanges(bookings, bayCount = 2)
        assertNull(BookingUtils.findFreeBayIndex(ranges, bayCount = 2, start = 0, end = 30))
    }

    // ---- ledgerDocId / busyRangesFromLedger ----

    @Test
    fun `ledgerDocId combines shopId and date deterministically`() {
        assertEquals("shop123_1700000000000", BookingUtils.ledgerDocId("shop123", 1700000000000L))
    }

    @Test
    fun `busyRangesFromLedger groups entries by bay`() {
        val entries = listOf(
            DaySlotEntry(bay = 0, start = 0, end = 30, bookingId = "b1"),
            DaySlotEntry(bay = 0, start = 60, end = 90, bookingId = "b2"),
            DaySlotEntry(bay = 1, start = 0, end = 45, bookingId = "b3")
        )
        val ranges = BookingUtils.busyRangesFromLedger(entries)

        assertEquals(listOf(0 to 30, 60 to 90), ranges[0])
        assertEquals(listOf(0 to 45), ranges[1])
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 0, end = 30)) // bay0 busy...
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 45, end = 60)) // ...but a gap exists in bay0
    }

    // ---- utcMidnightToLocalMidnight ----
    @Test
    fun `utcMidnightToLocalMidnight parses utc millis and outputs local timezone midnight`() {
        val calUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(2026, java.util.Calendar.AUGUST, 25, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val utcMillis = calUtc.timeInMillis
        val localMidnight = BookingUtils.utcMidnightToLocalMidnight(utcMillis)

        val calLocal = java.util.Calendar.getInstance()
        calLocal.timeInMillis = localMidnight
        assertEquals(2026, calLocal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.AUGUST, calLocal.get(java.util.Calendar.MONTH))
        assertEquals(25, calLocal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, calLocal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, calLocal.get(java.util.Calendar.MINUTE))
        assertEquals(0, calLocal.get(java.util.Calendar.SECOND))
        assertEquals(0, calLocal.get(java.util.Calendar.MILLISECOND))
    }

    // ---- parseTimeSlotToMinutes ----
    @Test
    fun `parseTimeSlotToMinutes parses slot strings to minutes since midnight`() {
        assertEquals(540, BookingUtils.parseTimeSlotToMinutes("09:00 AM"))
        assertEquals(810, BookingUtils.parseTimeSlotToMinutes("01:30 PM"))
        assertEquals(0, BookingUtils.parseTimeSlotToMinutes("invalid"))
    }

    // ---- calculateEstimatedWaitMinutes ----

    @Test
    fun `calculateEstimatedWaitMinutes returns zero when ahead list is empty`() {
        assertEquals(0, BookingUtils.calculateEstimatedWaitMinutes(emptyList(), bayCount = 1))
        assertEquals(0, BookingUtils.calculateEstimatedWaitMinutes(emptyList(), bayCount = 3))
    }

    @Test
    fun `calculateEstimatedWaitMinutes sums durations sequentially for single bay`() {
        val b1 = Booking(durationMinutes = 30, status = BookingStatus.PENDING)
        val b2 = Booking(durationMinutes = 45, status = BookingStatus.PENDING)
        val ahead = listOf(b1, b2)
        assertEquals(75, BookingUtils.calculateEstimatedWaitMinutes(ahead, bayCount = 1))
    }

    @Test
    fun `calculateEstimatedWaitMinutes simulates parallel bays correctly`() {
        // Two bays. b1 takes 30 mins, b2 takes 45 mins.
        // Third car (the user's) should wait until the first bay becomes free (at 30 mins).
        val b1 = Booking(durationMinutes = 30, status = BookingStatus.PENDING)
        val b2 = Booking(durationMinutes = 45, status = BookingStatus.PENDING)
        val ahead = listOf(b1, b2)
        assertEquals(30, BookingUtils.calculateEstimatedWaitMinutes(ahead, bayCount = 2))

        // Three bays. Two cars ahead. Next available bay is free immediately.
        assertEquals(0, BookingUtils.calculateEstimatedWaitMinutes(ahead, bayCount = 3))
    }

    @Test
    fun `calculateEstimatedWaitMinutes deducts elapsed time for in progress bookings`() {
        val now = System.currentTimeMillis()
        
        // Booking has 30 mins duration and has been running for 10 minutes (elapsed = 10 mins).
        // Remaining time should be 20 minutes.
        val b1 = Booking(
            durationMinutes = 30,
            status = BookingStatus.IN_PROGRESS,
            inProgressAt = now - 10 * 60000 // 10 minutes ago
        )
        val ahead = listOf(b1)
        assertEquals(20, BookingUtils.calculateEstimatedWaitMinutes(ahead, bayCount = 1))
    }

    @Test
    fun `calculateEstimatedWaitMinutes handles elapsed time exceeding duration gracefully`() {
        val now = System.currentTimeMillis()
        
        // Booking has 30 mins duration and has been running for 40 minutes (elapsed = 40 mins).
        // Remaining time should be 0 minutes.
        val b1 = Booking(
            durationMinutes = 30,
            status = BookingStatus.IN_PROGRESS,
            inProgressAt = now - 40 * 60000 // 40 minutes ago
        )
        val ahead = listOf(b1)
        assertEquals(0, BookingUtils.calculateEstimatedWaitMinutes(ahead, bayCount = 1))
    }
}
