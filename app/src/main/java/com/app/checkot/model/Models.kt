package com.app.checkot.model

data class CarWashUser(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = 0,
    val role: String = "customer", // "customer" or "owner"
    val ownedShopId: String? = null, // Only used if role == "owner"
    var defaultCar: Car? = null,
    val savedCars: List<Car> = emptyList(),
    val shopCustomization: ShopCustomization? = null // Owner-only
)

data class CarWashShop(
    val shopId: String = "",
    val name: String = "",
    val address: String = ""
)
data class Car(
    val carId: String = "",
    val plateNumber: String = "",
    val model: String = "",
    val brand: String = "",
    val color: String = "",
    val isDefault: Boolean = false
)
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
data class TimeSlot(
    val slot: String,
    val available: Boolean = true
)
data class QueueInfo(
    val position: Int = -1,
    val estimatedWaitMinutes: Int = 0,
    val totalInQueue: Int = 0
)

data class ShopCustomization(
    val logoBase64: String = "",
    val logoMimeType: String = "image/png",
    val services: List<CustomServiceConfig> = emptyList(),
    val ownerFcmToken: String = "" // FCM token for sending notifications to the owner
)

data class CustomServiceConfig(
    val serviceName: String = "", // Maps to ServiceType.name, or custom ID
    val displayName: String = "",
    val customPrice: Double = 0.0, // 0 = use default from ServiceType
    val isCustom: Boolean = false, // true for owner-created "Others" services
    val customName: String = "" // Custom name for "Others" services
)

/** Returns the formatted service names, replacing "Custom Service" with actual custom names */
fun Booking.displayServiceNames(): String {
    val customIndices = services.mapIndexedNotNull { index, s -> if (s == ServiceType.CUSTOM) index else null }
    return services.joinToString(", ") { service ->
        if (service == ServiceType.CUSTOM) {
            val pos = customIndices.indexOf(services.indexOf(service))
            customServiceNames.getOrElse(pos) { service.displayName }
        } else {
            service.displayName
        }
    }
}

val partnerShops = listOf(
    CarWashShop("shop_1", "General T. Cleaners", "123 Ugong Street"),
    CarWashShop("shop_2", "Sparkle Wash De Leon", "456 De Leon Ave"),
    CarWashShop("shop_3", "Premium Detailing Hub", "789 Main Rd")
)
