package com.example.finora.util

import java.util.Locale
import kotlin.math.*

/**
 * Utility for calculating great-circle distances between two geographic coordinate points
 * using the Haversine formula.
 */
object HaversineUtil {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates the great-circle distance in kilometers between two latitude/longitude points.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Formats distance into a human-readable string (e.g. "0.8 km away" or "50 m away").
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 0.1) {
            val meters = (distanceKm * 1000).roundToInt()
            "$meters m away"
        } else {
            String.format(Locale.US, "%.1f km away", distanceKm)
        }
    }
}
