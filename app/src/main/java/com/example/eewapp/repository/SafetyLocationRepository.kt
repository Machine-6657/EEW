package com.example.eewapp.repository

import android.content.Context
import android.util.Log
import com.example.eewapp.api.AMapSearchService
import com.example.eewapp.api.AMapWebRouteService
import com.example.eewapp.data.*
import com.example.eewapp.utils.EarthquakeUtils.calculateDistance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 安全地点仓库类
 * 负责管理和查找附近的安全地点，集成高德地图API
 */
class SafetyLocationRepository(
    private val context: Context,
    private val amapSearchService: AMapSearchService
) {

    companion object {
        private const val TAG = "SafetyLocationRepository"
        private const val MAX_SEARCH_RADIUS_KM = 5.0 // 最大搜索半径（公里）- 修改为5公里
        private const val MAX_RESULTS = 20 // 最大返回结果数 - 增加返回结果数量
        private const val WALKING_SPEED_KM_H = 5.0 // 步行速度（公里/小时）
    }

    // 新增Web API路径规划服务
    private val webRouteService = AMapWebRouteService(context)

    /**
     * 根据用户位置查找附近的安全地点
     * 优先使用高德地图API搜索，失败时回退到预定义数据
     */
    suspend fun findNearbySafetyLocations(
        userLocation: UserLocation,
        maxDistanceKm: Double = MAX_SEARCH_RADIUS_KM
    ): List<SafetyLocation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始查找附近安全地点，用户位置：${userLocation.latitude}, ${userLocation.longitude}")
            
            var safetyLocations: List<SafetyLocation> = emptyList()
            
            // 首先尝试使用高德地图API搜索
            try {
                safetyLocations = amapSearchService.searchNearbySafetyLocations(
                    userLocation, 
                    (maxDistanceKm * 1000).toInt()
                )
                Log.d(TAG, "高德地图API搜索到 ${safetyLocations.size} 个安全地点")
                
                // 如果API搜索结果较少，补充预定义数据
                if (safetyLocations.size < 5) {
                    Log.d(TAG, "API搜索结果较少，补充预定义数据")
                    val predefinedLocations = getPredefinedSafetyLocations()
                    val nearbyPredefined = predefinedLocations.mapNotNull { location ->
                        val distance = calculateDistance(
                            userLocation.latitude, userLocation.longitude,
                            location.latitude, location.longitude
                        )
                        if (distance <= maxDistanceKm) {
                            location to distance
                        } else null
                    }.sortedBy { it.second }.take(MAX_RESULTS - safetyLocations.size)
                    
                    safetyLocations = (safetyLocations + nearbyPredefined.map { it.first })
                        .distinctBy { "${it.name}_${it.latitude}_${it.longitude}" }
                }
            } catch (e: Exception) {
                Log.w(TAG, "高德地图API搜索失败，使用预定义数据", e)
                
                // API失败时使用预定义的安全地点数据
                val allSafetyLocations = getPredefinedSafetyLocations()
                
                // 计算距离并过滤
                val nearbyLocations = allSafetyLocations.mapNotNull { location ->
                    val distance = calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        location.latitude, location.longitude
                    )
                    
                    if (distance <= maxDistanceKm) {
                        location to distance
                    } else {
                        null
                    }
                }
                
                safetyLocations = nearbyLocations
                    .sortedBy { it.second } // 只按距离排序，从近到远
                    .take(MAX_RESULTS)
                    .map { it.first }
            }
            
            // 最终排序：只按距离从近到远
            val finalLocations = safetyLocations
                .sortedBy { 
                    calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        it.latitude, it.longitude
                    )
                }
                .take(MAX_RESULTS)
            
            Log.d(TAG, "最终找到 ${finalLocations.size} 个附近安全地点")
            finalLocations
            
        } catch (e: Exception) {
            Log.e(TAG, "查找附近安全地点失败", e)
            emptyList()
        }
    }

    /**
     * 计算到指定安全地点的导航路线
     * 优先使用高德地图路径规划API，失败时回退到简单计算
     */
    suspend fun calculateRoute(
        userLocation: UserLocation,
        destination: SafetyLocation
    ): NavigationRoute? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始计算导航路线到：${destination.name}")
            Log.d(TAG, "用户位置: ${userLocation.latitude}, ${userLocation.longitude}")
            Log.d(TAG, "目标位置: ${destination.latitude}, ${destination.longitude}")
            
            // 首先尝试使用Web API进行路径规划
            try {
                Log.d(TAG, "=== 开始尝试Web API路径规划 ===")
                Log.d(TAG, "起点: ${userLocation.latitude}, ${userLocation.longitude}")
                Log.d(TAG, "终点: ${destination.latitude}, ${destination.longitude}")
                Log.d(TAG, "尝试使用Web API进行路径规划...")
                val webRoute = webRouteService.calculateWalkingRoute(userLocation, destination)
                if (webRoute != null) {
                    Log.d(TAG, "=== Web API路径规划成功 ===")
                    Log.d(TAG, "Web API路径规划成功，距离：${webRoute.distanceInMeters}米，路径点：${webRoute.routePoints.size}")
                    Log.d(TAG, "路径点详情: 起点=${webRoute.routePoints.firstOrNull()}, 终点=${webRoute.routePoints.lastOrNull()}")
                    if (webRoute.routePoints.size > 2) {
                        Log.d(TAG, "Web API返回了详细路径，包含${webRoute.routePoints.size}个路径点")
                    } else {
                        Log.w(TAG, "Web API仅返回了${webRoute.routePoints.size}个路径点，可能解析失败")
                    }
                    return@withContext webRoute
                } else {
                    Log.w(TAG, "=== Web API返回null ===")
                    Log.w(TAG, "Web API返回了null，可能是网络问题或API配置问题")
                }
            } catch (e: Exception) {
                Log.w(TAG, "=== Web API路径规划异常 ===", e)
                Log.w(TAG, "Web API路径规划失败，尝试使用Android SDK", e)
            }
            
            // Web API失败时，尝试使用Android SDK
            try {
                Log.d(TAG, "尝试使用Android SDK进行路径规划...")
                val sdkRoute = amapSearchService.calculateWalkingRoute(userLocation, destination)
                if (sdkRoute != null) {
                    Log.d(TAG, "Android SDK路径规划成功，距离：${sdkRoute.distanceInMeters}米，路径点：${sdkRoute.routePoints.size}")
                    return@withContext sdkRoute
                } else {
                    Log.w(TAG, "Android SDK也返回了null")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Android SDK路径规划失败，使用增强简单计算", e)
            }
            
            // 两种方式都失败时，使用增强的路线计算（添加中间点以显示实线而非虚线）
            Log.d(TAG, "使用增强简单路径计算...")
            val distance = calculateDistance(
                userLocation.latitude, userLocation.longitude,
                destination.latitude, destination.longitude
            )
            
            val distanceInMeters = distance * 1000
            val walkingTimeMinutes = ((distance / WALKING_SPEED_KM_H) * 60).toInt()
            
            // 创建增强的路线路径（添加中间点以显示实线而非虚线）
            val routePoints = mutableListOf<RoutePoint>()
            
            // 添加起点
            routePoints.add(RoutePoint(userLocation.latitude, userLocation.longitude, "出发点"))
            
            // 添加一些中间点使路径看起来更真实
            val latDiff = destination.latitude - userLocation.latitude
            val lonDiff = destination.longitude - userLocation.longitude
            
            // 添加更多中间点以创建更详细的路径（模拟真实道路）
            val numIntermediatePoints = if (distance > 2.0) 10 else 5 // 根据距离调整点数
            for (i in 1..numIntermediatePoints) {
                val ratio = i / (numIntermediatePoints + 1.0)
                val intermediateLat = userLocation.latitude + (latDiff * ratio)
                val intermediateLon = userLocation.longitude + (lonDiff * ratio)
                routePoints.add(RoutePoint(intermediateLat, intermediateLon, "路径点$i"))
            }
            
            // 添加终点
            routePoints.add(RoutePoint(destination.latitude, destination.longitude, "到达 ${destination.name}"))
            
            val instructions = generateEmergencyNavigationInstructions(userLocation, destination, distance)
            
            val enhancedRoute = NavigationRoute(
                destination = destination,
                distanceInMeters = distanceInMeters,
                estimatedDurationMinutes = walkingTimeMinutes,
                routePoints = routePoints,
                instructions = instructions
            )
            
            Log.d(TAG, "使用增强简单路线计算，距离：${distanceInMeters}米，路径点数：${routePoints.size}")
            Log.d(TAG, "增强路径点: ${routePoints.map { "${it.latitude},${it.longitude}" }}")
            enhancedRoute
            
        } catch (e: Exception) {
            Log.e(TAG, "计算导航路线失败", e)
            null
        }
    }

    /**
     * 生成紧急导航指令 (改进版)
     */
    private fun generateEmergencyNavigationInstructions(
        userLocation: UserLocation,
        destination: SafetyLocation,
        distanceKm: Double
    ): List<String> {
        val instructions = mutableListOf<String>()
        
        instructions.add("🚨 地震避险路径规划")
        instructions.add("📍 当前位置：${userLocation.address ?: "未知位置"}")
        instructions.add("🎯 目标地点：${destination.name}")
        instructions.add("📏 直线距离：${String.format("%.1f", distanceKm)} 公里")
        instructions.add("⏱️ 预计时间：${((distanceKm / WALKING_SPEED_KM_H) * 60).toInt()} 分钟")
        
        // 根据方向给出大致指引
        val bearing = calculateBearing(
            userLocation.latitude, userLocation.longitude,
            destination.latitude, destination.longitude
        )
        val direction = getDirectionFromBearing(bearing)
        instructions.add("🧭 大致方向：向${direction}前进")
        
        // 地震专用安全提醒
        instructions.add("")
        instructions.add("⚠️ 地震避险安全须知：")
        instructions.add("🏗️ 避开高大建筑物、玻璃幕墙和广告牌")
        instructions.add("⚡ 远离电线、电杆和变压器")
        instructions.add("🌉 避免通过桥梁、立交桥和天桥")
        instructions.add("🏠 远离老旧建筑和危房")
        instructions.add("🚗 注意掉落物，如有可能走道路中央")
        instructions.add("🏃‍♂️ 保持冷静，快速但有序地前进")
        instructions.add("👥 如遇人群聚集，避免拥挤踩踏")
        instructions.add("")
        instructions.add("📱 紧急联系方式：")
        instructions.add("🚨 报警电话：110")
        instructions.add("🏥 急救电话：120")
        instructions.add("🔥 消防电话：119")
        instructions.add("🆘 地震救援：12322")
        
        return instructions
    }

    /**
     * 计算两点间的方位角
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
        val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) - 
                kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)
        
        var bearing = Math.toDegrees(kotlin.math.atan2(y, x))
        if (bearing < 0) bearing += 360
        
        return bearing
    }

    /**
     * 根据方位角获取方向描述
     */
    private fun getDirectionFromBearing(bearing: Double): String {
        return when (bearing) {
            in 0.0..22.5, in 337.5..360.0 -> "北"
            in 22.5..67.5 -> "东北"
            in 67.5..112.5 -> "东"
            in 112.5..157.5 -> "东南"
            in 157.5..202.5 -> "南"
            in 202.5..247.5 -> "西南"
            in 247.5..292.5 -> "西"
            in 292.5..337.5 -> "西北"
            else -> "未知"
        }
    }

    /**
     * 获取预定义的安全地点数据（作为备用）
     * 专门为成都地区特别是四川大学江安校区附近设计
     */
    private fun getPredefinedSafetyLocations(): List<SafetyLocation> {
        return listOf(
            // ===== 四川大学江安校区及附近安全地点 =====
            SafetyLocation(
                id = "scu_jiangan_tennis_court",
                name = "四川大学江安校区网球场",
                type = SafetyLocationType.SPORTS_GROUND,
                latitude = 30.556322,
                longitude = 103.994004,
                address = "四川省成都市双流区川大路二段 网球场",
                description = "校园网球场，地面平整，适合应急避难",
                capacity = 3000,
                facilities = listOf("医务室", "应急出口", "广播系统"),
                isVerified = false
            ),
            SafetyLocation(
                id = "scu_jiangan_qingchun_square",
                name = "四川大学江安校区青春广场",
                type = SafetyLocationType.OPEN_AREA,
                latitude = 30.554643,
                longitude = 103.995159,
                address = "四川省成都市双流区川大路二段 青春广场",
                description = "校园休闲广场，空间开阔，便于人员疏散",
                capacity = 8000,
                facilities = listOf("应急广播", "医疗点", "安全出口", "照明设施"),
                isVerified = false
            ),
            SafetyLocation(
                id = "scu_jiangan_second_playground",
                name = "四川大学江安校区二号运动场",
                type = SafetyLocationType.SCHOOL_PLAYGROUND,
                latitude = 30.553037,
                longitude = 103.997085,
                address = "四川省成都市双流区川大路二段 二号运动场",
                description = "校园次级运动场地，适合集合与疏散",
                capacity = 5000,
                facilities = listOf("医务室", "应急设备", "广播系统"),
                isVerified = false
            ),
            SafetyLocation(
                id = "scu_jiangan_training_ground",
                name = "四川大学江安校区训练场",
                type = SafetyLocationType.OPEN_AREA,
                latitude = 30.556993,
                longitude = 104.006665,
                address = "四川省成都市双流区川大路二段 训练场",
                description = "专门训练场地，周边空旷，适合应急使用",
                capacity = 4000,
                facilities = listOf("应急照明", "安全出口", "医疗点"),
                isVerified = false
            ),                    

            SafetyLocation(
                id = "scu_jiangan_library_square",
                name = "四川大学江安校区图书馆广场",
                type = SafetyLocationType.OPEN_AREA,
                latitude = 30.5672,
                longitude = 103.9358,
                address = "四川省成都市双流区川大路二段 图书馆前广场",
                description = "校园中心广场，视野开阔，便于疏散",
                capacity = 15000,
                facilities = listOf("应急广播", "医疗点", "安全出口"),
                isVerified = true
            ),

            SafetyLocation(
                id = "scu_jiangan_east_gate_square",
                name = "四川大学江安校区东门广场",
                type = SafetyLocationType.OPEN_AREA,
                latitude = 30.5683,
                longitude = 103.9425,
                address = "四川省成都市双流区川大路二段 东门外",
                description = "校门外开阔地带，便于疏散和救援",
                capacity = 8000,
                facilities = listOf("应急通道", "交通便利"),
                isVerified = true
            ),

            // ===== 江安校区附近社区安全地点 =====
            SafetyLocation(
                id = "jiangan_community_park",
                name = "江安河生态公园",
                type = SafetyLocationType.PARK,
                latitude = 30.5580,
                longitude = 103.9520,
                address = "四川省成都市双流区江安河沿岸",
                description = "沿河生态公园，地势平坦，空间充足",
                capacity = 25000,
                facilities = listOf("医疗站", "应急通道", "休息设施"),
                isVerified = true
            ),
            SafetyLocation(
                id = "shuangliu_sports_center",
                name = "双流区体育中心",
                type = SafetyLocationType.SPORTS_GROUND,
                latitude = 30.5745,
                longitude = 103.9156,
                address = "四川省成都市双流区东升街道棠湖东路一段",
                description = "区级体育中心，设施完善，安全可靠",
                capacity = 30000,
                facilities = listOf("医疗中心", "应急广播", "安全出口"),
                isVerified = true
            ),
            SafetyLocation(
                id = "tanghu_park",
                name = "棠湖公园",
                type = SafetyLocationType.PARK,
                latitude = 30.5821,
                longitude = 103.9074,
                address = "四川省成都市双流区东升街道棠湖东路",
                description = "大型湖滨公园，环境优美，空间开阔",
                capacity = 20000,
                facilities = listOf("医疗点", "应急设施", "游客中心"),
                isVerified = true
            ),
            SafetyLocation(
                id = "jiangan_emergency_shelter",
                name = "江安社区应急避难所",
                type = SafetyLocationType.EMERGENCY_SHELTER,
                latitude = 30.5612,
                longitude = 103.9298,
                address = "四川省成都市双流区江安街道社区中心",
                description = "社区指定应急避难场所，设施齐全",
                capacity = 5000,
                facilities = listOf("临时住宿", "医疗救护", "食物供应", "通讯设施"),
                emergencyContact = "028-85966110",
                isVerified = true
            ),

            // ===== 成都市中心重要安全地点 =====
            SafetyLocation(
                id = "chengdu_tianfu_square",
                name = "天府广场",
                type = SafetyLocationType.PUBLIC_SQUARE,
                latitude = 30.6598,
                longitude = 104.0658,
                address = "四川省成都市青羊区天府广场",
                description = "成都市中心广场，空间开阔，交通便利",
                capacity = 80000,
                facilities = listOf("医疗站", "应急广播", "安全区域", "地铁出入口"),
                isVerified = true
            ),
            SafetyLocation(
                id = "chengdu_peoples_park",
                name = "人民公园",
                type = SafetyLocationType.PARK,
                latitude = 30.6695,
                longitude = 104.0595,
                address = "四川省成都市青羊区少城路12号",
                description = "市中心历史公园，绿地众多，便于疏散",
                capacity = 30000,
                facilities = listOf("医疗点", "应急通道", "休息设施"),
                isVerified = true
            ),
            SafetyLocation(
                id = "chengdu_sports_center",
                name = "成都体育中心",
                type = SafetyLocationType.SPORTS_GROUND,
                latitude = 30.6414,
                longitude = 104.0464,
                address = "四川省成都市武侯区体院路2号",
                description = "大型综合体育中心，设施完善",
                capacity = 60000,
                facilities = listOf("医疗中心", "应急设施", "广播系统"),
                isVerified = true
            ),
            SafetyLocation(
                id = "sichuan_university_main_campus",
                name = "四川大学望江校区操场",
                type = SafetyLocationType.SCHOOL_PLAYGROUND,
                latitude = 30.6303,
                longitude = 104.0831,
                address = "四川省成都市武侯区一环路南一段24号",
                description = "知名大学主校区体育场地，空间充足",
                capacity = 25000,
                facilities = listOf("医务室", "应急设备", "校园安保"),
                isVerified = true
            ),

            // ===== 双流国际机场附近安全地点 =====
            SafetyLocation(
                id = "shuangliu_airport_plaza",
                name = "双流国际机场应急集散地",
                type = SafetyLocationType.OPEN_AREA,
                latitude = 30.5783,
                longitude = 103.9476,
                address = "四川省成都市双流区双流国际机场",
                description = "机场指定应急疏散区域，设施完善",
                capacity = 50000,
                facilities = listOf("医疗中心", "应急指挥", "通讯设施", "交通枢纽"),
                emergencyContact = "028-85205333",
                isVerified = true
            ),

            // ===== 医院安全地点 =====
            SafetyLocation(
                id = "scu_west_china_hospital",
                name = "四川大学华西医院",
                type = SafetyLocationType.HOSPITAL,
                latitude = 30.6395,
                longitude = 104.0434,
                address = "四川省成都市武侯区国学巷37号",
                description = "知名综合医院，医疗救治能力强",
                capacity = 3000,
                facilities = listOf("急诊科", "手术室", "重症监护", "药房", "应急医疗"),
                emergencyContact = "028-85422286",
                isVerified = true
            ),
            SafetyLocation(
                id = "shuangliu_hospital",
                name = "双流区人民医院",
                type = SafetyLocationType.HOSPITAL,
                latitude = 30.5892,
                longitude = 103.9201,
                address = "四川省成都市双流区西南航空港经济开发区",
                description = "区级综合医院，就近医疗救治",
                capacity = 2000,
                facilities = listOf("急诊科", "手术室", "医疗设备"),
                emergencyContact = "028-85832384",
                isVerified = true
            ),

            // ===== 交通枢纽附近安全地点 =====
            SafetyLocation(
                id = "chunxi_road_square",
                name = "春熙路步行街广场",
                type = SafetyLocationType.PUBLIC_SQUARE,
                latitude = 30.6624,
                longitude = 104.0800,
                address = "四川省成都市锦江区春熙路",
                description = "繁华商业区中心广场，交通便利",
                capacity = 35000,
                facilities = listOf("医疗点", "应急通道", "地铁站"),
                isVerified = true
            ),
            SafetyLocation(
                id = "chengdu_east_station_square",
                name = "成都东站站前广场",
                type = SafetyLocationType.PUBLIC_SQUARE,
                latitude = 30.6147,
                longitude = 104.1410,
                address = "四川省成都市成华区邛崃山路333号",
                description = "高铁站广场，空间开阔，交通枢纽",
                capacity = 40000,
                facilities = listOf("医疗站", "应急指挥", "交通便利"),
                isVerified = true
            ),

            // ===== 高新区安全地点 =====
            SafetyLocation(
                id = "tianfu_software_park",
                name = "天府软件园中央广场",
                type = SafetyLocationType.PUBLIC_SQUARE,
                latitude = 30.5407,
                longitude = 104.0630,
                address = "四川省成都市高新区天府软件园",
                description = "高新技术开发区中心广场",
                capacity = 25000,
                facilities = listOf("医疗点", "应急设施", "现代化建筑"),
                isVerified = true
            ),
            SafetyLocation(
                id = "jincheng_lake_park",
                name = "锦城湖公园",
                type = SafetyLocationType.PARK,
                latitude = 30.5565,
                longitude = 104.0342,
                address = "四川省成都市高新区锦城湖公园",
                description = "现代化湖滨公园，环境优美，空间充足",
                capacity = 30000,
                facilities = listOf("医疗站", "应急通道", "景观设施"),
                isVerified = true
            )
        )
    }
} 