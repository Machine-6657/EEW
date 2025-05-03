package com.example.eewapp.data

import java.util.Date

/**
 * Represents earthquake data
 */
data class Earthquake(
    val id: String,
    val magnitude: Double,
    val location: EarthquakeLocation,
    val depth: Double, // in kilometers
    val time: Date,
    val title: String,
    val url: String,
    val tsunamiWarning: Boolean = false
)

/**
 * Represents the location of an earthquake
 */
data class EarthquakeLocation(
    val latitude: Double,
    val longitude: Double,
    val place: String // Description of the location
)

/**
 * Represents the calculated impact of an earthquake on the user's location
 */
data class EarthquakeImpact(
    val earthquake: Earthquake,
    val distanceFromUser: Double, // in kilometers
    val estimatedArrivalTime: Long, // in milliseconds since epoch
    val secondsUntilArrival: Int,
    val intensity: ShakingIntensity
)

/**
 * Represents the user's current location
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val address: String? = null
)

/**
 * Represents the app's state regarding earthquake alerts
 */
data class AlertState(
    val isActive: Boolean = false,
    val currentEarthquake: EarthquakeImpact? = null,
    val alertLevel: AlertLevel = AlertLevel.NONE
)

/**
 * Enum representing the level of alert to display to the user
 */
enum class AlertLevel(val color: Long) {
    NONE(0xFF808080), // Gray
    LOW(0xFF4CAF50),  // Green
    MEDIUM(0xFFFFEB3B), // Yellow
    HIGH(0xFFFF9800),  // Orange
    CRITICAL(0xFFFF0000) // Red
} 