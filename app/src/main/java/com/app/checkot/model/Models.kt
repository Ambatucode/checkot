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
    val address: String = ""
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
    val cancelledAt: Long? = null
)
enum class ServiceType(val displayName: String, val price: Double, val duration: String) {
    BASIC_WASH("Basic Wash", 150.0, "30 mins"),
    PREMIUM_WASH("Premium Wash", 300.0, "45 mins"),
    DETAILING("Full Detailing", 800.0, "2 hours"),
    INTERIOR_CLEAN("Interior Clean", 400.0, "1 hour"),
    EXTERIOR_WAX("Exterior Wax", 350.0, "45 mins"),
    ENGINE_CLEAN("Engine Clean", 500.0, "1.5 hours"),
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
    val logoBase64: String = "",
    val logoMimeType: String = "image/png",
    val services: List<CustomServiceConfig> = emptyList(),
    val ownerFcmToken: String = "" // FCM token for sending notifications to the owner
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
    val durationMinutes: Int = 0 // 0 = not set; legacy docs fall back to the ServiceType default
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


