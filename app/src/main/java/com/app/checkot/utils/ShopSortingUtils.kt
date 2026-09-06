package com.app.checkot.utils

import com.app.checkot.model.CarWashShop
import kotlin.math.max

enum class ShopSortOption(val displayName: String) {
    RECOMMENDED("Recommended"),
    NEAREST("Nearest"),
    TOP_RATED("Top Rated"),
    LOWEST_PRICE("Lowest Price"),
    OPEN_NOW("Open Now")
}

object ShopSortingUtils {

    /**
     * Attaches computed distance to each shop and sorts the list according to the chosen [ShopSortOption].
     */
    fun sortShops(
        shops: List<CarWashShop>,
        option: ShopSortOption,
        userLat: Double = 0.0,
        userLon: Double = 0.0
    ): List<CarWashShop> {
        // Compute distance for each shop if valid user location is provided
        val shopsWithDistance = shops.map { shop ->
            if (userLat != 0.0 && userLon != 0.0 && shop.latitude != 0.0 && shop.longitude != 0.0) {
                val dist = LocationUtils.calculateDistanceKm(userLat, userLon, shop.latitude, shop.longitude)
                shop.copy(distanceKm = dist)
            } else {
                shop
            }
        }

        return when (option) {
            ShopSortOption.RECOMMENDED -> {
                // Foodpanda-style smart ranking formula:
                // Open status (+50 pts) + Rating (+0-50 pts) + Proximity (+0-50 pts)
                shopsWithDistance.sortedByDescending { shop ->
                    val openBonus = if (!shop.isClosed) 50.0 else 0.0
                    val ratingScore = shop.averageRating * 10.0 // Max 50
                    val proximityScore = if (shop.distanceKm != Double.MAX_VALUE && shop.distanceKm >= 0) {
                        max(0.0, 50.0 - shop.distanceKm * 3.0)
                    } else {
                        10.0 // Default baseline for unknown location
                    }
                    openBonus + ratingScore + proximityScore
                }
            }

            ShopSortOption.NEAREST -> {
                shopsWithDistance.sortedWith(
                    compareBy<CarWashShop> { if (it.isClosed) 1 else 0 }
                        .thenBy { if (it.distanceKm == Double.MAX_VALUE) Double.MAX_VALUE else it.distanceKm }
                )
            }

            ShopSortOption.TOP_RATED -> {
                shopsWithDistance.sortedWith(
                    compareByDescending<CarWashShop> { it.averageRating }
                        .thenByDescending { it.reviewCount }
                        .thenBy { if (it.distanceKm == Double.MAX_VALUE) Double.MAX_VALUE else it.distanceKm }
                )
            }

            ShopSortOption.LOWEST_PRICE -> {
                shopsWithDistance.sortedWith(
                    compareBy<CarWashShop> { if (it.minPrice <= 0) Double.MAX_VALUE else it.minPrice }
                        .thenByDescending { it.averageRating }
                )
            }

            ShopSortOption.OPEN_NOW -> {
                shopsWithDistance
                    .filter { !it.isClosed }
                    .sortedWith(
                        compareBy<CarWashShop> { if (it.distanceKm == Double.MAX_VALUE) Double.MAX_VALUE else it.distanceKm }
                            .thenByDescending { it.averageRating }
                    )
            }
        }
    }
}
