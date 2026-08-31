package com.app.checkot.utils

import com.app.checkot.model.Booking
import com.app.checkot.model.DayHoursOverride
import com.app.checkot.model.DaySlotEntry
import com.app.checkot.model.ServiceType
import com.app.checkot.model.ShopCustomization
import com.app.checkot.model.BookingStatus

/**
 * Shared booking-slot math. Previously duplicated (with slight drift) across
 * BookingViewModel.createBooking, BookingViewModel.fetchAvailableTimeSlots,
 * and OwnerDashboardViewModel.autoCancelStaleBookings.
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

    /** Parses a "hh:mm AM/PM" slot label into minutes since midnight (e.g., "09:00 AM" -> 540). */
    fun parseTimeSlotToMinutes(slot: String): Int {
        val hm = runCatching { parseTimeSlotToHourMinute(slot) }.getOrNull()
        return if (hm != null) hm.first * 60 + hm.second else 0
    }

    /** Converts a UTC midnight timestamp to a local timezone midnight timestamp. */
    fun utcMidnightToLocalMidnight(utcMillis: Long): Long {
        val calUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calUtc.timeInMillis = utcMillis
        val year = calUtc.get(java.util.Calendar.YEAR)
        val month = calUtc.get(java.util.Calendar.MONTH)
        val day = calUtc.get(java.util.Calendar.DAY_OF_MONTH)

        val calLocal = java.util.Calendar.getInstance()
        calLocal.set(java.util.Calendar.YEAR, year)
        calLocal.set(java.util.Calendar.MONTH, month)
        calLocal.set(java.util.Calendar.DAY_OF_MONTH, day)
        calLocal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calLocal.set(java.util.Calendar.MINUTE, 0)
        calLocal.set(java.util.Calendar.SECOND, 0)
        calLocal.set(java.util.Calendar.MILLISECOND, 0)
        return calLocal.timeInMillis
    }

    /** Minutes since midnight for a "hh:mm AM/PM" slot label (0-1440 range, handles early opening hours). */
    fun parseTimeSlotToMinutesSince9AM(slot: String): Int {
        val (h, m) = parseTimeSlotToHourMinute(slot)
        return h * 60 + m
    }

    /** Formats minutes-since-midnight (e.g. 540) into a "hh:mm AM/PM" slot label ("09:00 AM"). */
    fun minutesToSlotLabel(minutesSinceMidnight: Int): String {
        val h = minutesSinceMidnight / 60
        val m = minutesSinceMidnight % 60
        val ampm = if (h >= 12) "PM" else "AM"
        var h12 = h % 12
        if (h12 == 0) h12 = 12
        return String.format("%02d:%02d %s", h12, m, ampm)
    }

    /**
     * Generates the bookable start-time labels for a shop's working hours.
     * [openMinutes]/[closeMinutes] are minutes since midnight; closeMinutes is
     * the last slot start (inclusive). Steps every [stepMinutes] (default 30).
     */
    fun generateSlotLabels(
        openMinutes: Int,
        closeMinutes: Int,
        stepMinutes: Int = 30
    ): List<String> {
        if (closeMinutes < openMinutes || stepMinutes <= 0) return emptyList()
        val labels = ArrayList<String>()
        var t = openMinutes
        while (t <= closeMinutes) {
            labels.add(minutesToSlotLabel(t))
            t += stepMinutes
        }
        return labels
    }

    /**
     * Normalizes a timestamp to the start of its day (local midnight) so dates
     * can be compared consistently — used for shop closedDates, per-service
     * unavailableDates, and booking dates.
     */
    fun startOfDay(timestamp: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Effective open/close (minutes since midnight) for [date], applying a
     * per-day override if one exists — otherwise the shop's permanent hours.
     */
    fun effectiveHours(
        openMinutes: Int,
        closeMinutes: Int,
        overrides: List<DayHoursOverride>,
        date: Long
    ): Pair<Int, Int> {
        val day = startOfDay(date)
        val override = overrides.firstOrNull { it.date == day }
        return if (override != null) override.openMinutes to override.closeMinutes
        else openMinutes to closeMinutes
    }

    /**
     * Why a booking is no longer fully covered by the shop's current schedule
     * (closed date / special hours that no longer include the slot / a booked
     * service removed or unavailable that day), or null if it's fine. Single
     * source of truth for BOTH the owner's notification check and the client's
     * "impacted" banner on the booking screen.
     */
    fun bookingImpactReason(booking: Booking, shop: ShopCustomization): String? {
        val day = startOfDay(booking.bookingDate)
        if (shop.closedDates.contains(day)) {
            return "The shop is closed on ${DateUtils.formatDate(day)}"
        }
        val (effOpen, effClose) = effectiveHours(
            shop.openMinutes, shop.closeMinutes, shop.dayOverrides, booking.bookingDate
        )
        val hm = runCatching { parseTimeSlotToHourMinute(booking.timeSlot) }.getOrNull()
        if (hm != null) {
            val start = hm.first * 60 + hm.second
            if (start < effOpen || start > effClose) {
                return "The shop has special hours on ${DateUtils.formatDate(day)} " +
                    "(${minutesToSlotLabel(effOpen)} – ${minutesToSlotLabel(effClose)}) that no longer include your slot"
            }
        }
        val serviceGone = booking.services.any { svc ->
            if (svc == ServiceType.CUSTOM) {
                booking.customServiceNames.any { name ->
                    val config = shop.services.find { it.isCustom && it.customName == name }
                    config == null || config.unavailableDates.contains(day)
                }
            } else {
                val config = shop.services.find { it.serviceName == svc.name }
                config == null || config.unavailableDates.contains(day)
            }
        }
        return if (serviceGone) {
            "A service you booked is no longer available on ${DateUtils.formatDate(day)}"
        } else null
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

    /**
     * Formats a price for display: whole amounts drop the decimal ("₱200" not
     * "200.0"), fractional amounts keep up to two digits ("₱199.5").
     */
    fun formatPrice(price: Double): String {
        val s = if (price % 1.0 == 0.0) price.toLong().toString()
                else "%.2f".format(java.util.Locale.US, price).trimEnd('0').trimEnd('.')
        return "₱$s"
    }

    /** Total duration, in minutes, of a booking's selected services. */
    fun totalDurationMinutes(services: List<ServiceType>): Int =
        services.sumOf { parseDurationMinutes(it.duration) }

    /**
     * Effective duration of a booking: the owner-configured total snapshotted
     * at creation, falling back to the built-in ServiceType defaults for
     * legacy bookings created before durations were stored.
     */
    fun bookingDurationMinutes(booking: Booking): Int =
        if (booking.durationMinutes > 0) booking.durationMinutes
        else totalDurationMinutes(booking.services)

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
            // Use the SNAPSHOTTED duration (prefers b.durationMinutes, falls
            // back to the built-in defaults for legacy bookings) — exactly what
            // the ledger reserves, so client availability matches the atomic
            // reservation and never shows a slot that the ledger rejects.
            val end = start + bookingDurationMinutes(b)
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

    /**
     * Estimates the wait time for a user by simulating scheduling of all ahead bookings
     * across the available bays, accounting for elapsed time of in-progress bookings.
     */
    fun calculateEstimatedWaitMinutes(ahead: List<Booking>, bayCount: Int): Int {
        val safeBayCount = bayCount.coerceAtLeast(1)
        if (ahead.isEmpty()) return 0
        val bayEnds = IntArray(safeBayCount) { 0 }
        val now = System.currentTimeMillis()
        
        ahead.forEach { b ->
            val duration = bookingDurationMinutes(b)
            val remaining = if (b.status == BookingStatus.IN_PROGRESS && b.inProgressAt != null) {
                val elapsedMins = ((now - b.inProgressAt) / 60000).toInt()
                (duration - elapsedMins).coerceAtLeast(0)
            } else {
                duration
            }
            val earliestBay = bayEnds.indices.minByOrNull { bayEnds[it] } ?: 0
            bayEnds[earliestBay] += remaining
        }
        return bayEnds.minOrNull() ?: 0
    }
}
