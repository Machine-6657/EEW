package com.example.eewapp.utils

import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.data.UserLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility class for earthquake-related calculations
 */
object EarthquakeUtils {
    
    // Earth radius in kilometers
    private const val EARTH_RADIUS = 6371.0
    
    // Average P-wave velocity in km/s (primary waves - faster but less damaging)
    private const val P_WAVE_VELOCITY = 6.0
    
    // Average S-wave velocity in km/s (secondary waves - slower but more damaging)
    private const val S_WAVE_VELOCITY = 3.5
    
    /**
     * Calculate the distance between two points on Earth using the Haversine formula
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Distance in kilometers
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        
        val a = sin(latDistance / 2).pow(2) + 
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * 
                sin(lonDistance / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS * c
    }
    
    /**
     * Calculate the estimated arrival time of seismic waves
     * @param distance Distance from earthquake epicenter in kilometers
     * @param depth Depth of the earthquake in kilometers
     * @param isPWave Whether to calculate for P-wave (true) or S-wave (false)
     * @return Travel time in seconds
     */
    fun calculateWaveTravelTime(distance: Double, depth: Double, isPWave: Boolean): Double {
        // Calculate the hypotenuse (direct path) using Pythagorean theorem
        val directPath = sqrt(distance.pow(2) + depth.pow(2))
        
        // Calculate travel time based on wave velocity
        return directPath / if (isPWave) P_WAVE_VELOCITY else S_WAVE_VELOCITY
    }
    
    /**
     * Calculate the bearing from one point to another
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lonDiffRad = Math.toRadians(lon2 - lon1)
        
        val y = sin(lonDiffRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(lonDiffRad)
        
        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) {
            bearing += 360
        }
        
        return bearing
    }
    
    /**
     * Estimate the shaking intensity at a given distance from an earthquake
     * This is a simplified model based on the Modified Mercalli Intensity Scale
     * @param magnitude Earthquake magnitude (Richter scale)
     * @param distance Distance from epicenter in kilometers
     * @param depth Depth of the earthquake in kilometers
     * @return Estimated shaking intensity
     */
    fun estimateShakingIntensity(magnitude: Double, distance: Double, depth: Double): ShakingIntensity {
        // Calculate hypocentral distance (direct path from hypocenter to location)
        val hypocentralDistance = sqrt(distance.pow(2) + depth.pow(2))
        
        // Simple attenuation model: intensity decreases with distance
        // This is a simplified model and not scientifically accurate
        val estimatedIntensity = magnitude - 2 * log10(hypocentralDistance) - 0.0086 * hypocentralDistance
        
        return when {
            estimatedIntensity < 2 -> ShakingIntensity.LEVEL_0
            estimatedIntensity < 3 -> ShakingIntensity.LEVEL_1
            estimatedIntensity < 4 -> ShakingIntensity.LEVEL_2
            estimatedIntensity < 5 -> ShakingIntensity.LEVEL_3
            estimatedIntensity < 6 -> ShakingIntensity.LEVEL_4
            estimatedIntensity < 7 -> ShakingIntensity.LEVEL_5
            estimatedIntensity < 8 -> ShakingIntensity.LEVEL_6
            estimatedIntensity < 9 -> ShakingIntensity.LEVEL_7
            else -> ShakingIntensity.LEVEL_7
        }
    }
    
    /**
     * Calculate the impact of an earthquake on a specific location
     * @param earthquake The earthquake data
     * @param userLocation The user's location
     * @return The calculated impact
     */
    fun calculateEarthquakeImpact(earthquake: Earthquake, userLocation: UserLocation): EarthquakeImpact {
        val distance = calculateDistance(
            userLocation.latitude, userLocation.longitude,
            earthquake.location.latitude, earthquake.location.longitude
        )
        
        // Calculate S-wave arrival time (the damaging waves)
        val travelTimeSeconds = calculateWaveTravelTime(distance, earthquake.depth, false)
        val estimatedArrivalTime = earthquake.time.time + (travelTimeSeconds * 1000).toLong()
        val secondsUntilArrival = ((estimatedArrivalTime - System.currentTimeMillis()) / 1000).toInt()
        
        val intensity = estimateShakingIntensity(earthquake.magnitude, distance, earthquake.depth)
        
        return EarthquakeImpact(
            earthquake = earthquake,
            distanceFromUser = distance,
            estimatedArrivalTime = estimatedArrivalTime,
            secondsUntilArrival = secondsUntilArrival,
            intensity = intensity
        )
    }
    
    /**
     * Custom log10 function to avoid issues with negative numbers
     */
    private fun log10(value: Double): Double {
        return kotlin.math.log10(maxOf(value, 0.0001))
    }
} 