package com.example.eewapp.data

/**
 * 地震震感强度
 */
enum class ShakingIntensity(val level: Int, val description: String) {
    LEVEL_0(0, "无感"),
    LEVEL_1(1, "微感"),
    LEVEL_2(2, "轻微"),
    LEVEL_3(3, "轻度"),
    LEVEL_4(4, "中度"),
    LEVEL_5(5, "强烈"),
    LEVEL_6(6, "很强烈"),
    LEVEL_7(7, "剧烈");
    
    companion object {
        /**
         * 根据震级和距离估算震感强度
         */
        fun estimateFromMagnitudeAndDistance(magnitude: Double, distanceKm: Double): ShakingIntensity {
            // 简化的震感强度估算公式
            val estimatedLevel = when {
                magnitude >= 7.0 -> 7
                magnitude >= 6.0 -> 6
                magnitude >= 5.0 -> 5
                magnitude >= 4.0 -> 4
                magnitude >= 3.0 -> 3
                magnitude >= 2.0 -> 2
                magnitude >= 1.0 -> 1
                else -> 0
            }
            
            // 根据距离调整震感强度
            val distanceAdjustment = when {
                distanceKm <= 10 -> 0
                distanceKm <= 50 -> -1
                distanceKm <= 100 -> -2
                distanceKm <= 200 -> -3
                else -> -4
            }
            
            val adjustedLevel = (estimatedLevel + distanceAdjustment).coerceIn(0, 7)
            
            return values().find { it.level == adjustedLevel } ?: LEVEL_0
        }
    }
} 