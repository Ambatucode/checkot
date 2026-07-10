package com.app.checkot.service

import com.app.checkot.model.Booking
import com.app.checkot.model.DaySlotEntry
import com.app.checkot.model.DaySlotLedger
import com.app.checkot.utils.BookingUtils
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Owns the day_slots ledger used to atomically reserve/release car-wash bays.
 *
 * Firestore transactions can only read specific documents via transaction.get(),
 * never run a query — so "find every booking for this shop+date" (needed to
 * compute bay availability) can't happen inside a transaction. This service
 * keeps one small per-shop-per-day document as a stand-in for that query,
 * so booking creation can check availability and reserve a bay atomically
 * instead of racing a separate check against a separate write.
 */
object BookingLedgerService {

    /** Thrown when no bay is free for the requested time range — a real "fully booked", not an error. */
    class NoFreeBayException : Exception("No free bay for the requested time slot")

    private fun ledgerRef(firestore: FirebaseFirestore, shopId: String, date: Long): DocumentReference =
        firestore.collection("day_slots").document(BookingUtils.ledgerDocId(shopId, date))

    /**
     * Atomically checks bay availability and, if one is free, creates [booking]
     * and reserves its bay in the same transaction. Throws NoFreeBayException
     * if no bay is free; any other exception means availability could not be
     * verified and the booking was not created.
     */
    suspend fun reserveAndCreateBooking(
        firestore: FirebaseFirestore,
        bookingDocRef: DocumentReference,
        booking: Booking,
        startMin: Int,
        endMin: Int
    ) {
        val shopRef = firestore.collection("shop_services").document(booking.shopId)
        val ledgerRef = ledgerRef(firestore, booking.shopId, booking.bookingDate)

        firestore.runTransaction { transaction ->
            val shopSnap = transaction.get(shopRef)
            val bayCount = (shopSnap.getLong("bayCount")?.toInt() ?: 1).coerceAtLeast(1)

            val ledgerSnap = transaction.get(ledgerRef)
            val ledger = ledgerSnap.toObject(DaySlotLedger::class.java)
                ?: DaySlotLedger(shopId = booking.shopId, date = booking.bookingDate)

            val busyRanges = BookingUtils.busyRangesFromLedger(ledger.entries)
            val freeBay = BookingUtils.findFreeBayIndex(busyRanges, bayCount, startMin, endMin)
                ?: throw NoFreeBayException()

            transaction.set(bookingDocRef, booking)

            val updatedLedger = ledger.copy(
                shopId = booking.shopId,
                date = booking.bookingDate,
                entries = ledger.entries + DaySlotEntry(freeBay, startMin, endMin, booking.bookingId)
            )
            transaction.set(ledgerRef, updatedLedger)
            null
        }.await()
    }

    /**
     * Removes [bookingId]'s reservation from the ledger. Best-effort: unlike
     * reserve, a failed release isn't safety-critical — it just leaves the
     * slot marked busy a bit longer than necessary, it can never cause an
     * overbooking.
     */
    suspend fun release(firestore: FirebaseFirestore, shopId: String, date: Long, bookingId: String) {
        if (shopId.isEmpty()) return
        val ref = ledgerRef(firestore, shopId, date)
        try {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(ref)
                val ledger = snap.toObject(DaySlotLedger::class.java) ?: return@runTransaction null
                val updated = ledger.copy(entries = ledger.entries.filterNot { it.bookingId == bookingId })
                transaction.set(ref, updated)
                null
            }.await()
        } catch (e: Exception) {
            // Best-effort — see doc comment above.
        }
    }
}
