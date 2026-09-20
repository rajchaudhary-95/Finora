package com.example.finora

import com.example.finora.util.HaversineUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineUtilTest {

    @Test
    fun testSameCoordinates_returnsZeroDistance() {
        val lat = 37.4220
        val lon = -122.0841
        val distance = HaversineUtil.calculateDistanceKm(lat, lon, lat, lon)
        assertEquals(0.0, distance, 0.0001)
    }

    @Test
    fun testStatueOfLibertyToEmpireStateBuilding() {
        // Statue of Liberty: 40.6892° N, 74.0445° W
        val libertyLat = 40.6892
        val libertyLon = -74.0445

        // Empire State Building: 40.7484° N, 73.9857° W
        val empireLat = 40.7484
        val empireLon = -73.9857

        val distance = HaversineUtil.calculateDistanceKm(libertyLat, libertyLon, empireLat, empireLon)
        // Known distance is ~8.24 km
        assertEquals(8.24, distance, 0.1)
    }

    @Test
    fun testLondonToParis() {
        // London: 51.5074° N, 0.1278° W
        val londonLat = 51.5074
        val londonLon = -0.1278

        // Paris: 48.8566° N, 2.3522° E
        val parisLat = 48.8566
        val parisLon = 2.3522

        val distance = HaversineUtil.calculateDistanceKm(londonLat, londonLon, parisLat, parisLon)
        // Known great-circle distance is ~343.5 km
        assertEquals(343.5, distance, 2.0)
    }

    @Test
    fun testFormatDistance_subHundredMeters() {
        assertEquals("50 m away", HaversineUtil.formatDistance(0.05))
        assertEquals("20 m away", HaversineUtil.formatDistance(0.02))
    }

    @Test
    fun testFormatDistance_kilometer() {
        assertEquals("0.8 km away", HaversineUtil.formatDistance(0.8))
        assertEquals("12.5 km away", HaversineUtil.formatDistance(12.54))
    }
}
