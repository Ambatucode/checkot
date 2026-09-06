package com.app.checkot.utils

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates the distance in kilometers between two GPS coordinates (latitude/longitude)
     * using the Haversine formula.
     */
    fun calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) return Double.MAX_VALUE

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(rLat1) * cos(rLat2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Formats a distance in kilometers to a friendly human-readable label (e.g. "450 m" or "1.2 km").
     */
    fun formatDistance(distanceKm: Double): String {
        if (distanceKm == Double.MAX_VALUE || distanceKm < 0.0) return ""
        return if (distanceKm < 1.0) {
            val meters = (distanceKm * 1000).toInt()
            "$meters m"
        } else {
            String.format("%.1f km", distanceKm)
        }
    }
}
