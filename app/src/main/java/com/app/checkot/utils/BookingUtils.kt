package com.app.checkot.utils

import com.app.checkot.model.Booking
import com.app.checkot.model.DaySlotEntry
import com.app.checkot.model.ServiceType

/**
 * Shared booking-slot math. Previously duplicated (with slight drift) across
 * BookingViewModel.createBooking, BookingViewModel.fetchAvailableTimeSlots,
 * and AdminViewModel.autoCancelStaleBookings.
 */
object BookingUtils {

    /** Parses a "hh:mm AM/PM" slot label (e.g. "09:00 AM") into 24-hour (hour, minute). */
    fun parseTimeSlotToHourMinute(slot: String): Pair<Int, Int> {
        val parts = slot.split(" ")
        val t = parts[0].split(":")
        var h = t[0].toInt()
        val m = t[1].toInt()
        if (parts[1] == "PM" && h != 12) h += 12
        if (parts[1] == "AM" && h == 12) h = 0
        return h to m
    }

    /** Minutes since 09:00 (the start of the booking day) for a "hh:mm AM/PM" slot label. */
    fun parseTimeSlotToMinutesSince9AM(slot: String): Int {
        val (h, m) = parseTimeSlotToHourMinute(slot)
        return (h - 9) * 60 + m
    }

    /** Parses a duration string like "45 mins" or "1.5 hours" into minutes. */
    fun parseDurationMinutes(duration: String): Int {
        return when {
            duration.contains("hour") -> {
                val hours = duration.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0
                (hours * 60).toInt()
            }
            duration.contains("min") -> {
                duration.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 30
            }
            else -> 30
        }
    }

    /** Total duration, in minutes, of a booking's selected services. */
    fun totalDurationMinutes(services: List<ServiceType>): Int =
        services.sumOf { parseDurationMinutes(it.duration) }

    /**
     * Assigns each booking to the lowest-numbered free bay using first-fit
     * interval scheduling. Bookings are sorted by start time first — first-fit
     * is only guaranteed to find a valid assignment (when one exists) if
     * intervals are processed in start-time order; Firestore query results
     * aren't ordered, so skipping the sort can make an actually-available
     * slot look fully booked.
     */
    fun computeBusyRanges(bookings: List<Booking>, bayCount: Int): Map<Int, List<Pair<Int, Int>>> {
        val busyRanges: Map<Int, MutableList<Pair<Int, Int>>> =
            (0 until bayCount).associateWith { mutableListOf() }
        val sorted = bookings.sortedBy { parseTimeSlotToMinutesSince9AM(it.timeSlot) }
        for (b in sorted) {
            val start = parseTimeSlotToMinutesSince9AM(b.timeSlot)
            val end = start + totalDurationMinutes(b.services)
            for (bay in 0 until bayCount) {
                val ranges = busyRanges.getValue(bay)
                if (ranges.none { (s, e) -> start < e && end > s }) {
                    ranges.add(start to end)
                    break
                }
            }
        }
        return busyRanges
    }

    /** True if at least one bay has no range overlapping [start, end). */
    fun hasFreeBay(busyRanges: Map<Int, List<Pair<Int, Int>>>, start: Int, end: Int): Boolean {
        return busyRanges.values.any { ranges -> ranges.none { (s, e) -> start < e && end > s } }
    }

    /** The lowest-numbered bay (0-indexed) with no range overlapping [start, end), or null if none. */
    fun findFreeBayIndex(busyRanges: Map<Int, List<Pair<Int, Int>>>, bayCount: Int, start: Int, end: Int): Int? {
        for (bay in 0 until bayCount) {
            val ranges = busyRanges[bay].orEmpty()
            if (ranges.none { (s, e) -> start < e && end > s }) return bay
        }
        return null
    }

    /** Deterministic document ID for a shop's day_slots ledger entry. */
    fun ledgerDocId(shopId: String, date: Long): String = "${shopId}_$date"

    /** Reshapes a ledger's flat entry list into the same per-bay range map computeBusyRanges produces. */
    fun busyRangesFromLedger(entries: List<DaySlotEntry>): Map<Int, List<Pair<Int, Int>>> =
        entries.groupBy { it.bay }.mapValues { (_, v) -> v.map { it.start to it.end } }
}
