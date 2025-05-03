package com.example.eewapp.data

import androidx.compose.runtime.Immutable

/**
 * 应用设置数据模型
 */
@Immutable
data class AppSettings(
    // 通知设置
    val notificationSettings: NotificationSettings = NotificationSettings(),
    
    // 过滤设置
    val filterSettings: FilterSettings = FilterSettings(),
    
    // 应用设置
    val appPreferences: AppPreferences = AppPreferences()
)

/**
 * 通知设置
 */
@Immutable
data class NotificationSettings(
    // 地震预警通知
    val earthquakeWarningEnabled: Boolean = true,
    
    // 声音提醒
    val soundAlertEnabled: Boolean = true,
    
    // 震动提醒
    val vibrationEnabled: Boolean = true
)

/**
 * 过滤设置
 */
@Immutable
data class FilterSettings(
    // 最小震级 (默认 4.0)
    val minMagnitude: Float = 4.0f,
    
    // 监测半径 (默认 300公里)
    val monitoringRadiusKm: Int = 300
)

/**
 * 应用偏好设置
 */
@Immutable
data class AppPreferences(
    // 语言设置 (默认中文)
    val language: Language = Language.CHINESE,
    
    // 单位设置 (默认公制)
    val unit: MeasurementUnit = MeasurementUnit.METRIC
)

/**
 * 语言枚举
 */
enum class Language(val displayName: String) {
    CHINESE("中文"),
    ENGLISH("English")
}

/**
 * 单位枚举
 */
enum class MeasurementUnit(val displayName: String) {
    METRIC("公制 (公里)"),
    IMPERIAL("英制 (英里)")
} 