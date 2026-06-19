package com.app.checkot.model
import com.app.checkot.model.*
import com.app.checkot.viewmodel.*
import com.app.checkot.navigation.*
import com.app.checkot.utils.*
import com.app.checkot.service.*
import com.app.checkot.ui.screens.*
data class CarWashUser(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = 0,
    val role: String = "customer", // "customer" or "owner"
    val ownedShopId: String? = null, // Only used if role == "owner"
    var defaultCar: Car? = null,
    val savedCars: List<Car> = emptyList()
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
    ENGINE_CLEAN("Engine Clean", 500.0, "1.5 hours")
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
