package com.app.checkot.utils

import com.app.checkot.model.CarWashShop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSortingTest {

    private val userLat = 14.5995
    private val userLon = 120.9842 // Manila

    private val nearShop = CarWashShop(
        shopId = "shop_near",
        name = "Near Wash",
        latitude = 14.6000,
        longitude = 120.9850,
        averageRating = 4.2,
        reviewCount = 10,
        minPrice = 200.0,
        isClosed = false
    )

    private val topRatedShop = CarWashShop(
        shopId = "shop_top",
        name = "Top Wash",
        latitude = 14.6500,
        longitude = 121.0000,
        averageRating = 4.9,
        reviewCount = 50,
        minPrice = 250.0,
        isClosed = false
    )

    private val cheapShop = CarWashShop(
        shopId = "shop_cheap",
        name = "Cheap Wash",
        latitude = 14.6200,
        longitude = 120.9900,
        averageRating = 3.8,
        reviewCount = 5,
        minPrice = 120.0,
        isClosed = false
    )

    private val closedShop = CarWashShop(
        shopId = "shop_closed",
        name = "Closed Wash",
        latitude = 14.6010,
        longitude = 120.9855,
        averageRating = 5.0,
        reviewCount = 100,
        minPrice = 100.0,
        isClosed = true
    )

    private val allShops = listOf(cheapShop, topRatedShop, closedShop, nearShop)

    @Test
    fun `calculateDistanceKm computes accurate distance`() {
        val distKm = LocationUtils.calculateDistanceKm(
            14.5995, 120.9842,
            14.6760, 121.0437
        )
        assertTrue(distKm > 8.0 && distKm < 15.0)
    }

    @Test
    fun `formatDistance formats meters and kilometers cleanly`() {
        assertEquals("450 m", LocationUtils.formatDistance(0.45))
        assertEquals("2.5 km", LocationUtils.formatDistance(2.45))
    }

    @Test
    fun `sortShops Nearest sorts by distance ascending`() {
        val sorted = ShopSortingUtils.sortShops(allShops, ShopSortOption.NEAREST, userLat, userLon)
        assertEquals("shop_near", sorted.first().shopId)
    }

    @Test
    fun `sortShops Top Rated sorts by averageRating descending`() {
        val sorted = ShopSortingUtils.sortShops(allShops, ShopSortOption.TOP_RATED, userLat, userLon)
        assertEquals("shop_closed", sorted[0].shopId) // 5.0
        assertEquals("shop_top", sorted[1].shopId)    // 4.9
    }

    @Test
    fun `sortShops Lowest Price sorts by minPrice ascending`() {
        val sorted = ShopSortingUtils.sortShops(allShops, ShopSortOption.LOWEST_PRICE, userLat, userLon)
        assertEquals("shop_closed", sorted[0].shopId) // 100.0
        assertEquals("shop_cheap", sorted[1].shopId)  // 120.0
    }

    @Test
    fun `sortShops Open Now excludes closed shops`() {
        val sorted = ShopSortingUtils.sortShops(allShops, ShopSortOption.OPEN_NOW, userLat, userLon)
        assertTrue(sorted.none { it.isClosed })
        assertEquals(3, sorted.size)
    }
}
