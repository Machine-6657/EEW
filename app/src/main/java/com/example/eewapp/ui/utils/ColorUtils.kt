package com.example.eewapp.ui.utils

import androidx.compose.ui.graphics.Color
import com.example.eewapp.data.ShakingIntensity

/**
 * 颜色工具类
 */
object ColorUtils {
    /**
     * 根据震感强度获取颜色
     */
    fun getColorForIntensity(intensity: ShakingIntensity): Color {
        return when (intensity.level) {
            0 -> Color(0xFF9E9E9E) // 灰色 - 无感
            1 -> Color(0xFF4CAF50) // 绿色 - 微感
            2 -> Color(0xFF8BC34A) // 浅绿色 - 轻微
            3 -> Color(0xFFCDDC39) // 黄绿色 - 轻度
            4 -> Color(0xFFFFEB3B) // 黄色 - 中度
            5 -> Color(0xFFFFC107) // 琥珀色 - 强烈
            6 -> Color(0xFFFF9800) // 橙色 - 很强烈
            7 -> Color(0xFFF44336) // 红色 - 剧烈
            else -> Color(0xFF9E9E9E) // 默认灰色
        }
    }
    
    /**
     * 获取波浪颜色
     */
    fun getWaveColor(intensity: ShakingIntensity): Color {
        return when (intensity.level) {
            0, 1, 2 -> Color(0xFF4CAF50) // 绿色
            3, 4 -> Color(0xFFFFEB3B) // 黄色
            5, 6 -> Color(0xFFFF9800) // 橙色
            7 -> Color(0xFFF44336) // 红色
            else -> Color(0xFF9E9E9E) // 默认灰色
        }
    }
    
    /**
     * 获取波浪颜色的Android颜色值
     */
    fun getWaveColorInt(intensity: ShakingIntensity): Int {
        return when (intensity.level) {
            0, 1, 2 -> android.graphics.Color.parseColor("#4CAF50") // 绿色
            3, 4 -> android.graphics.Color.parseColor("#FFEB3B") // 黄色
            5, 6 -> android.graphics.Color.parseColor("#FF9800") // 橙色
            7 -> android.graphics.Color.parseColor("#F44336") // 红色
            else -> android.graphics.Color.parseColor("#9E9E9E") // 默认灰色
        }
    }
} 