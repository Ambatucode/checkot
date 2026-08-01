package com.app.checkot.model

import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.PropertyName

// The @Immutable annotations tell the Compose compiler these classes never
// change after construction (Firestore always builds fresh instances), which
// makes list cards skippable — without them the List<> fields mark every
// model unstable and every visible card recomposes on any snapshot update.
@Immutable
data class CarWashUser(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    // A number is only ever set to true after an SMS OTP verification (signup or
    // change). Unverified numbers can't be trusted — this gates entry to the app.
    val phoneVerified: Boolean = false,
    val createdAt: Long = 0,
    val role: String = "customer", // "customer" or "owner"
    val ownedShopId: String? = null, // Only used if role == "owner"
    val defaultCar: Car? = null,
    val savedCars: List<Car> = emptyList(),
    val shopCustomization: ShopCustomization? = null // Owner-only
)

@Immutable
data class CarWashShop(
    val shopId: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,  // 0 = location not set
    val longitude: Double = 0.0,
    val logoUrl: String = ""     // Firebase Storage download URL; "" = no logo
)
@Immutable
data class Car(
    val carId: String = "",
    val plateNumber: String = "",
    val model: String = "",
    val brand: String = "",
    val color: String = "",
    // @PropertyName keeps the Firestore field named "isDefault": without it
    // Kotlin's is-prefixed getter makes Firestore WRITE the field as
    // "default" but READ it back as "isDefault", so the flag silently
    // becomes false after every round-trip.
    @get:PropertyName("isDefault")
    val isDefault: Boolean = false
)
@Immutable
data class Booking(
    val bookingId: String = "",
    val shopId: String = "",
    val userId: String = "",
    val carId: String = "",
    val carDetails: String = "", // Store car plate + model for quick reference
    val services: List<ServiceType> = emptyList(),
    val customServiceNames: List<String> = emptyList(), // Names for CUSTOM type services
    val bookingDate: Long = 0, // Timestamp
    val timeSlot: String = "",
    val status: BookingStatus = BookingStatus.PENDING,
    val price: Double = 0.0,
    val durationMinutes: Int = 0, // total estimated duration snapshot at booking time; 0 = legacy booking
    val notes: String = "",
    val createdAt: Long = 0,
    val confirmedAt: Long? = null,
    val inProgressAt: Long? = null,
    val completedAt: Long? = null,
    val cancelledAt: Long? = null,
    // Staff member assigned when the service is started. Display-only: shown to
    // the client and owner, never gates bay capacity or scheduling.
    val servicedBy: String = "",
    // Paid add-ons appended while the service is in progress. Each entry is a
    // display label like "Exterior Wax - ₱350". Money only: add-ons bump
    // `price` but never extend the reserved bay window (keeps the ledger valid).
    val addOns: List<String> = emptyList(),
    // --- Payment (cash only) ---
    val paymentMethod: String = "Cash",
    // "unpaid" until the owner confirms cash received at the shop.
    val paymentStatus: String = "unpaid",
    // Server timestamp when the owner confirmed cash received. null = not yet.
    val paidAt: Long? = null
)
enum class ServiceType(val displayName: String, val price: Double, val duration: String) {
    // Prices (₱) and durations below are sensible PLACEHOLDERS — tweak freely.
    // The enum NAME (left side) is what's stored in Firestore; the displayName,
    // price, and duration are read generically everywhere, so changing values
    // here is safe. Duration strings must stay in the "30 mins" / "1 hour"
    // format the scheduler's parseDurationMinutes() understands.
    EXTERIOR_WASH("Exterior Wash", 150.0, "30 mins"),
    UNDERWASH("Underwash", 200.0, "30 mins"),
    WAX("Wax", 350.0, "45 mins"),
    INTERIOR_VACUUM("Interior Vacuum", 200.0, "30 mins"),
    TIRE_SHINE("Tire Shine", 100.0, "15 mins"),
    ENGINE_WASH("Engine Wash", 500.0, "1 hour"),
    CUSTOM("Custom Service", 0.0, "N/A")
}
enum class BookingStatus(val displayName: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}
@Immutable
data class TimeSlot(
    val slot: String,
    val available: Boolean = true
)
@Immutable
data class QueueInfo(
    val position: Int = -1,
    val estimatedWaitMinutes: Int = 0,
    val totalInQueue: Int = 0
)

@Immutable
data class ShopCustomization(
    val shopName: String = "",
    val shopAddress: String = "",
    val status: String = "active", // "pending", "active", "rejected"
    val ownerId: String = "", // The admin-set owner UID (for admin dashboard)
    val ownerName: String = "", // Owner's full name (admin only, not shown to customers)
    val ownerEmail: String = "", // Owner's email (admin only, not shown to customers)
    val bayCount: Int = 1, // How many cars can be serviced simultaneously
    // Shop logo now lives in Firebase Storage; logoUrl is the download URL saved
    // here (keeps this doc small). logoBase64/logoMimeType are legacy/unused.
    val logoUrl: String = "",
    // Optional wide cover/banner image (Storage download URL). "" = no banner;
    // the booking screen falls back to the logo + name header.
    val bannerUrl: String = "",
    val logoBase64: String = "",
    val logoMimeType: String = "image/png",
    val services: List<CustomServiceConfig> = emptyList(),
    // Daily working hours as minutes since midnight. Defaults reproduce the old
    // hardcoded window (9:00 AM – 4:00 PM). closeMinutes is the last bookable
    // slot start. Applied to every day.
    val openMinutes: Int = 540,  // 9:00 AM
    val closeMinutes: Int = 960, // 4:00 PM
    // Shop location on the map. 0 = not set yet.
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val ownerFcmToken: String = "", // FCM token for sending notifications to the owner
    // Staff the owner can assign to a service when starting it. Display-only —
    // does not affect bay count or booking capacity.
    val staffNames: List<String> = emptyList()
)

@Immutable
data class CustomServiceConfig(
    val serviceName: String = "", // Maps to ServiceType.name, or custom ID
    val displayName: String = "",
    val customPrice: Double = 0.0, // 0 = use default from ServiceType
    // See Car.isDefault: without @PropertyName this was saved as "custom"
    // and read back as "isCustom", so the flag was ALWAYS false after a
    // Firestore round-trip — which dropped custom service names from
    // bookings ("Custom Service" shown instead of the real name).
    @get:PropertyName("isCustom")
    val isCustom: Boolean = false, // true for owner-created "Others" services
    val customName: String = "", // Custom name for "Others" services
    val durationMinutes: Int = 0, // 0 = not set; legacy docs fall back to the ServiceType default
    val description: String = "" // Owner-written detail shown to clients so they know what the service is
)

/**
 * One reserved bay-time range within a DaySlotLedger, tied back to the
 * booking that reserved it.
 */
data class DaySlotEntry(
    val bay: Int = 0,
    val start: Int = 0, // minutes since 9:00 AM
    val end: Int = 0,
    val bookingId: String = ""
)

/**
 * Per-shop-per-day bay reservation ledger, stored at day_slots/{shopId}_{date}.
 * Firestore transactions can only read specific documents (not run queries),
 * so this single small document stands in for "query every booking for this
 * shop+date" — letting booking creation check-and-reserve a bay atomically.
 */
data class DaySlotLedger(
    val shopId: String = "",
    val date: Long = 0,
    val entries: List<DaySlotEntry> = emptyList()
)

/**
 * Per-service display names, replacing each CUSTOM entry with its actual custom
 * name from [Booking.customServiceNames]. Trailing custom names with no matching
 * CUSTOM slot (legacy bookings) are appended so none are lost. This is the
 * source of truth for turning a booking's services into human-readable names.
 */
fun Booking.resolvedServiceNames(): List<String> {
    var customCounter = 0
    val names = services.map { service ->
        if (service == ServiceType.CUSTOM) {
            val name = customServiceNames.getOrElse(customCounter) { service.displayName }
            customCounter++
            name
        } else {
            service.displayName
        }
    }
    // Append unmatched custom names (for old bookings)
    return if (customCounter < customServiceNames.size) {
        names + customServiceNames.drop(customCounter)
    } else {
        names
    }
}

/** Returns the formatted service names, replacing "Custom Service" with actual custom names */
fun Booking.displayServiceNames(): String = resolvedServiceNames().joinToString(", ")


