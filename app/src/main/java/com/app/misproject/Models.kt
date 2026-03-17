package com.app.misproject

data class CarWashUser(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = 0,
    var defaultCar: Car? = null,
    val savedCars: List<Car> = emptyList()
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
    val userId: String = "",
    val carId: String = "",
    val carDetails: String = "", // Store car plate + model for quick reference
    val serviceType: ServiceType = ServiceType.BASIC_WASH,
    val bookingDate: Long = 0, // Timestamp
    val timeSlot: String = "",
    val status: BookingStatus = BookingStatus.PENDING,
    val price: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = 0
)

enum class ServiceType(val displayName: String, val price: Double, val duration: String) {
    BASIC_WASH("Basic Wash", 150.0, "30 mins"),
    PREMIUM_WASH("Premium Wash", 300.0, "45 mins"),
    DETAILING("Full Detailing", 800.0, "2 hours"),
    INTERIOR_CLEAN("Interior Clean", 400.0, "1 hour"),
    EXTERIOR_WAX("Exterior Wax", 350.0, "45 mins"),
    ENGINE_CLEAN("Engine Clean", 500.0, "1.5 hours")
}

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class TimeSlot(
    val slot: String,
    val available: Boolean = true
)