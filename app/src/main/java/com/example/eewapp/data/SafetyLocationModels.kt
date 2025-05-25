package com.example.eewapp.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 安全地点数据模型
 */
data class SafetyLocation(
    val id: String,
    val name: String,
    val type: SafetyLocationType,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val description: String = "",
    val capacity: Int? = null, // 容纳人数
    val facilities: List<String> = emptyList(), // 设施信息
    val emergencyContact: String? = null, // 紧急联系方式
    val isVerified: Boolean = true // 是否经过验证
)

/**
 * 安全地点类型
 */
enum class SafetyLocationType(
    val displayName: String,
    val icon: ImageVector,
    val priority: Int, // 优先级，数字越小优先级越高
    val color: Long
) {
    EMERGENCY_SHELTER(
        displayName = "应急避难所",
        icon = Icons.Default.Home,
        priority = 1,
        color = 0xFF2196F3 // 蓝色
    ),
    SCHOOL_PLAYGROUND(
        displayName = "学校操场",
        icon = Icons.Default.School,
        priority = 2,
        color = 0xFF4CAF50 // 绿色
    ),
    PUBLIC_SQUARE(
        displayName = "公共广场",
        icon = Icons.Default.LocationCity,
        priority = 3,
        color = 0xFF9C27B0 // 紫色
    ),
    PARK(
        displayName = "公园空地",
        icon = Icons.Default.Park,
        priority = 4,
        color = 0xFF8BC34A // 浅绿色
    ),
    SPORTS_GROUND(
        displayName = "体育场馆",
        icon = Icons.Default.SportsSoccer,
        priority = 5,
        color = 0xFFFF9800 // 橙色
    ),
    OPEN_AREA(
        displayName = "空旷地带",
        icon = Icons.Default.Landscape,
        priority = 6,
        color = 0xFF795548 // 棕色
    ),
    HOSPITAL(
        displayName = "医院",
        icon = Icons.Default.LocalHospital,
        priority = 7,
        color = 0xFFF44336 // 红色
    )
}

/**
 * 导航路线信息
 */
data class NavigationRoute(
    val destination: SafetyLocation,
    val distanceInMeters: Double,
    val estimatedDurationMinutes: Int,
    val routePoints: List<RoutePoint> = emptyList(),
    val instructions: List<String> = emptyList()
)

/**
 * 路线点
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val instruction: String = ""
)

/**
 * 逃生导航状态
 */
data class EscapeNavigationState(
    val isActive: Boolean = false,
    val safetyLocations: List<SafetyLocation> = emptyList(),
    val selectedDestination: SafetyLocation? = null,
    val currentRoute: NavigationRoute? = null,
    val isNavigating: Boolean = false
) 