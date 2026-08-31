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

    // ---- parseTimeSlotToMinutesSinceMidnight ----

    @Test
    fun `parseTimeSlotToMinutesSince9AM returns minutes since midnight`() {
        assertEquals(540, BookingUtils.parseTimeSlotToMinutesSince9AM("09:00 AM"))
        assertEquals(570, BookingUtils.parseTimeSlotToMinutesSince9AM("09:30 AM"))
    }

    @Test
    fun `parseTimeSlotToMinutesSince9AM handles afternoon slots correctly`() {
        assertEquals(720, BookingUtils.parseTimeSlotToMinutesSince9AM("12:00 PM"))
        assertEquals(960, BookingUtils.parseTimeSlotToMinutesSince9AM("04:00 PM"))
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
        val bookings = listOf(bookingAt("09:00 AM"), bookingAt("09:00 AM"))

        val twoBays = BookingUtils.computeBusyRanges(bookings, bayCount = 2)
        assertFalse(BookingUtils.hasFreeBay(twoBays, start = 540, end = 570))
        assertTrue(BookingUtils.hasFreeBay(twoBays, start = 570, end = 600))

        val oneBay = BookingUtils.computeBusyRanges(bookings, bayCount = 1)
        assertFalse(BookingUtils.hasFreeBay(oneBay, start = 540, end = 570))
    }

    @Test
    fun `non-overlapping bookings can share a single bay`() {
        val bookings = listOf(bookingAt("09:00 AM"), bookingAt("10:00 AM"))
        val ranges = BookingUtils.computeBusyRanges(bookings, bayCount = 1)

        assertFalse(BookingUtils.hasFreeBay(ranges, start = 540, end = 570))
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 600, end = 630))
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 570, end = 600))
    }

    @Test
    fun `back-to-back bookings do not count as overlapping`() {
        val ranges = BookingUtils.computeBusyRanges(listOf(bookingAt("09:00 AM")), bayCount = 1)
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 570, end = 600))
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
                start = 570, end = 600
            )
            val shuffled = BookingUtils.hasFreeBay(
                BookingUtils.computeBusyRanges(shuffledOrder, bayCount),
                start = 570, end = 600
            )
            assertEquals("bayCount=$bayCount should agree regardless of input order", forward, shuffled)
        }

        val ranges = BookingUtils.computeBusyRanges(forwardOrder, bayCount = 1)
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 570, end = 600))
    }

    // ---- findFreeBayIndex ----

    @Test
    fun `findFreeBayIndex returns the lowest-numbered open bay`() {
        val ranges = BookingUtils.computeBusyRanges(listOf(bookingAt("09:00 AM")), bayCount = 2)
        assertEquals(1, BookingUtils.findFreeBayIndex(ranges, bayCount = 2, start = 540, end = 570))
    }

    @Test
    fun `findFreeBayIndex returns null when every bay conflicts`() {
        val bookings = listOf(bookingAt("09:00 AM"), bookingAt("09:00 AM"))
        val ranges = BookingUtils.computeBusyRanges(bookings, bayCount = 2)
        assertNull(BookingUtils.findFreeBayIndex(ranges, bayCount = 2, start = 540, end = 570))
    }

    // ---- ledgerDocId / busyRangesFromLedger ----

    @Test
    fun `ledgerDocId combines shopId and date deterministically`() {
        assertEquals("shop123_1700000000000", BookingUtils.ledgerDocId("shop123", 1700000000000L))
    }

    @Test
    fun `busyRangesFromLedger groups entries by bay`() {
        val entries = listOf(
            DaySlotEntry(bay = 0, start = 540, end = 570, bookingId = "b1"),
            DaySlotEntry(bay = 0, start = 600, end = 630, bookingId = "b2"),
            DaySlotEntry(bay = 1, start = 540, end = 585, bookingId = "b3")
        )
        val ranges = BookingUtils.busyRangesFromLedger(entries, bayCount = 2)

        assertEquals(listOf(540 to 570, 600 to 630), ranges[0])
        assertEquals(listOf(540 to 585), ranges[1])
        assertFalse(BookingUtils.hasFreeBay(ranges, start = 540, end = 570))
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 585, end = 600))
    }

    @Test
    fun `hasFreeBay returns true when the ledger is empty`() {
        // Regression: a day with no ledger entries yet must NOT grey out every
        // slot. `values.any {}` over an empty map is vacuously false, which
        // previously marked all slots unavailable for any unbooked day.
        val emptyLedger = BookingUtils.busyRangesFromLedger(emptyList(), bayCount = 3)
        assertTrue(BookingUtils.hasFreeBay(emptyLedger, start = 540, end = 570))
        assertTrue(BookingUtils.hasFreeBay(emptyLedger, start = 930, end = 990))
        // Also directly on an empty map (e.g. a missing ledger document).
        assertTrue(BookingUtils.hasFreeBay(emptyMap(), start = 540, end = 570))
    }

    @Test
    fun `busyRangesFromLedger keeps bays without reservations in the map`() {
        // Only bay 0 has a reservation. The buggy shape — a map that only
        // contains bays that HAVE entries — makes the free bay 1 invisible:
        // `values.any {}` only inspects bay 0 (busy) and reports "no free bay".
        val entries = listOf(DaySlotEntry(bay = 0, start = 540, end = 570, bookingId = "b1"))
        val buggyShape = mapOf(0 to listOf(540 to 570))
        assertFalse(BookingUtils.hasFreeBay(buggyShape, start = 540, end = 570))

        // With bayCount the ledger keeps every bay (empty ones included), so
        // bay 1 is visible and 540-570 is bookable there.
        val ranges = BookingUtils.busyRangesFromLedger(entries, bayCount = 2)
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 540, end = 570))
        assertTrue(BookingUtils.hasFreeBay(ranges, start = 570, end = 600))
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
