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

    private fun ledgerRef(firestore: FirebaseFirestore, shopId: String, date: Long): DocumentReference =
        firestore.collection("day_slots").document(BookingUtils.ledgerDocId(shopId, date))

    /**
     * Removes [bookingId]'s reservation from the ledger. Best-effort: unlike
     * reserve, a failed release isn't safety-critical — it just leaves the
     * slot marked busy a bit longer than necessary, it can never cause an
     * overbooking.
     */
    suspend fun release(firestore: FirebaseFirestore, shopId: String, date: Long, bookingId: String) {
        if (shopId.isEmpty() || bookingId.isEmpty()) return
        val ref = ledgerRef(firestore, shopId, date)
        try {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(ref)
                if (!snap.exists()) return@runTransaction null
                val ledger = snap.toObject(DaySlotLedger::class.java) ?: return@runTransaction null
                val filtered = ledger.entries.filterNot { it.bookingId == bookingId }
                if (filtered.size != ledger.entries.size) {
                    val updated = ledger.copy(entries = filtered)
                    transaction.set(ref, updated)
                }
                null
            }.await()
            android.util.Log.d("BookingLedgerService", "✅ Released booking $bookingId from ledger ${ref.id}")
        } catch (e: Exception) {
            android.util.Log.e("BookingLedgerService", "❌ Failed to release booking $bookingId from ledger: ${e.message}")
        }
    }

    /**
     * Updates the bay index in [day_slots] ledger for [bookingId] when the owner assigns or changes a bay.
     */
    suspend fun updateBayAssignment(
        firestore: FirebaseFirestore,
        shopId: String,
        date: Long,
        bookingId: String,
        newBayNumber: Int // 1-indexed (1..4)
    ) {
        if (shopId.isEmpty() || bookingId.isEmpty()) return
        val ref = ledgerRef(firestore, shopId, date)
        try {
            firestore.runTransaction { transaction ->
                val snap = transaction.get(ref)
                if (!snap.exists()) return@runTransaction null
                val ledger = snap.toObject(DaySlotLedger::class.java) ?: return@runTransaction null
                val updatedEntries = ledger.entries.map { entry ->
                    if (entry.bookingId == bookingId) {
                        entry.copy(bay = if (newBayNumber > 0) newBayNumber - 1 else entry.bay)
                    } else entry
                }
                transaction.set(ref, ledger.copy(entries = updatedEntries))
                null
            }.await()
            android.util.Log.d("BookingLedgerService", "✅ Updated ledger bay for booking $bookingId -> bay $newBayNumber")
        } catch (e: Exception) {
            android.util.Log.e("BookingLedgerService", "❌ Failed to update ledger bay for booking $bookingId: ${e.message}")
        }
    }
}
