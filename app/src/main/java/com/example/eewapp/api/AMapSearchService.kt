package com.example.eewapp.api

import android.content.Context
import android.util.Log
import com.example.eewapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 高德地图搜索服务
 * 提供POI搜索和路径规划功能
 */
class AMapSearchService(private val context: Context) {

    companion object {
        private const val TAG = "AMapSearchService"
        private const val SEARCH_RADIUS = 10000 // 搜索半径10公里
    }

    // 通过反射获取搜索API实例
    private val poiSearch: Any? by lazy {
        try {
            val poiSearchClass = Class.forName("com.amap.api.services.poisearch.PoiSearch")
            val builderClass = Class.forName("com.amap.api.services.poisearch.PoiSearch\$Builder")
            val builder = builderClass.getConstructor(Context::class.java).newInstance(context)
            val buildMethod = builderClass.getMethod("build")
            buildMethod.invoke(builder)
        } catch (e: Exception) {
            Log.e(TAG, "初始化POI搜索失败", e)
            null
        }
    }

    private val routeSearch: Any? by lazy {
        try {
            val routeSearchClass = Class.forName("com.amap.api.services.route.RouteSearch")
            routeSearchClass.getConstructor(Context::class.java).newInstance(context)
        } catch (e: Exception) {
            Log.e(TAG, "初始化路径搜索失败", e)
            null
        }
    }

    /**
     * 搜索附近的安全地点
     */
    suspend fun searchNearbySafetyLocations(
        userLocation: UserLocation,
        maxDistance: Int = SEARCH_RADIUS
    ): List<SafetyLocation> = withContext(Dispatchers.IO) {
        val allLocations = mutableListOf<SafetyLocation>()
        
        // 搜索不同类型的安全设施
        val searchQueries = mapOf(
            "应急避难所|避难场所|紧急避难" to SafetyLocationType.EMERGENCY_SHELTER,
            "医院|急救中心|卫生院" to SafetyLocationType.HOSPITAL,
            "学校|中学|小学|大学" to SafetyLocationType.SCHOOL_PLAYGROUND,
            "公园|绿地|广场" to SafetyLocationType.PUBLIC_SQUARE,
            "体育场|体育馆|运动场" to SafetyLocationType.SPORTS_GROUND
        )
        
        for ((keywords, type) in searchQueries) {
            try {
                val locations = searchPOIByKeywords(userLocation, keywords, type, maxDistance)
                allLocations.addAll(locations)
                Log.d(TAG, "搜索 $keywords 找到 ${locations.size} 个地点")
            } catch (e: Exception) {
                Log.e(TAG, "搜索 $keywords 失败", e)
            }
        }
        
        // 按距离排序，去重
        allLocations
            .distinctBy { "${it.name}_${it.latitude}_${it.longitude}" }
            .sortedBy { 
                calculateDistance(
                    userLocation.latitude, userLocation.longitude,
                    it.latitude, it.longitude
                )
            }
            .take(15) // 最多返回15个结果
    }

    /**
     * 根据关键词搜索POI
     */
    private suspend fun searchPOIByKeywords(
        userLocation: UserLocation,
        keywords: String,
        locationType: SafetyLocationType,
        radius: Int
    ): List<SafetyLocation> = suspendCancellableCoroutine { continuation ->
        
        try {
            val poiSearchClass = Class.forName("com.amap.api.services.poisearch.PoiSearch")
            val queryClass = Class.forName("com.amap.api.services.poisearch.PoiSearch\$Query")
            val latLonPointClass = Class.forName("com.amap.api.services.core.LatLonPoint")
            
            // 创建查询对象
            val query = queryClass.getConstructor(String::class.java, String::class.java, String::class.java)
                .newInstance(keywords, "", "")
            
            // 设置每页搜索数量
            val setPageSizeMethod = queryClass.getMethod("setPageSize", Int::class.java)
            setPageSizeMethod.invoke(query, 50)
            
            // 创建中心点
            val centerPoint = latLonPointClass.getConstructor(Double::class.java, Double::class.java)
                .newInstance(userLocation.latitude, userLocation.longitude)
            
            // 创建搜索边界
            val searchBoundClass = Class.forName("com.amap.api.services.poisearch.PoiSearch\$SearchBound")
            val searchBound = searchBoundClass.getConstructor(latLonPointClass, Int::class.java)
                .newInstance(centerPoint, radius)
            
            // 设置搜索边界
            val setBoundMethod = queryClass.getMethod("setBound", searchBoundClass)
            setBoundMethod.invoke(query, searchBound)
            
            // 创建搜索监听器
            val listenerClass = Class.forName("com.amap.api.services.poisearch.PoiSearch\$OnPoiSearchListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onPoiSearched" -> {
                        try {
                            val result = args[0]
                            val errorCode = args[1] as Int
                            
                            if (errorCode == 1000) { // 成功
                                val locations = parseSearchResult(result, locationType)
                                continuation.resume(locations)
                            } else {
                                Log.e(TAG, "POI搜索失败，错误码: $errorCode")
                                continuation.resume(emptyList())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析搜索结果失败", e)
                            continuation.resumeWithException(e)
                        }
                    }
                    "onPoiItemSearched" -> {
                        // 处理单个POI搜索结果
                    }
                }
                null
            }
            
            // 重新创建POI搜索实例并设置查询
            val builderClass = Class.forName("com.amap.api.services.poisearch.PoiSearch\$Builder")
            val builder = builderClass.getConstructor(Context::class.java).newInstance(context)
            val buildMethod = builderClass.getMethod("build")
            val poiSearchInstance = buildMethod.invoke(builder)
            
            // 设置查询和监听器
            val setQueryMethod = poiSearchClass.getMethod("setQuery", queryClass)
            setQueryMethod.invoke(poiSearchInstance, query)
            
            val setOnPoiSearchListenerMethod = poiSearchClass.getMethod("setOnPoiSearchListener", listenerClass)
            setOnPoiSearchListenerMethod.invoke(poiSearchInstance, listener)
            
            // 开始异步搜索
            val searchPOIAsyncMethod = poiSearchClass.getMethod("searchPOIAsyn")
            searchPOIAsyncMethod.invoke(poiSearchInstance)
            
        } catch (e: Exception) {
            Log.e(TAG, "POI搜索设置失败", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * 解析搜索结果
     */
    private fun parseSearchResult(result: Any, locationType: SafetyLocationType): List<SafetyLocation> {
        val locations = mutableListOf<SafetyLocation>()
        
        try {
            // 获取POI列表
            val getPoisMethod = result.javaClass.getMethod("getPois")
            val pois = getPoisMethod.invoke(result) as? ArrayList<*>
            
            pois?.forEach { poi ->
                try {
                    val poiItemClass = Class.forName("com.amap.api.services.core.PoiItem")
                    
                    if (poiItemClass.isInstance(poi)) {
                        // 获取POI信息
                        val getPoiIdMethod = poiItemClass.getMethod("getPoiId")
                        val getTitleMethod = poiItemClass.getMethod("getTitle")
                        val getSnippetMethod = poiItemClass.getMethod("getSnippet")
                        val getLatLonPointMethod = poiItemClass.getMethod("getLatLonPoint")
                        val getAdNameMethod = poiItemClass.getMethod("getAdName")
                        val getTypeCodeMethod = poiItemClass.getMethod("getTypeCode")
                        
                        val poiId = getPoiIdMethod.invoke(poi) as? String ?: ""
                        val title = getTitleMethod.invoke(poi) as? String ?: ""
                        val snippet = getSnippetMethod.invoke(poi) as? String ?: ""
                        val adName = getAdNameMethod.invoke(poi) as? String ?: ""
                        val typeCode = getTypeCodeMethod.invoke(poi) as? String ?: ""
                        
                        val latLonPoint = getLatLonPointMethod.invoke(poi)
                        if (latLonPoint != null) {
                            val getLatitudeMethod = latLonPoint.javaClass.getMethod("getLatitude")
                            val getLongitudeMethod = latLonPoint.javaClass.getMethod("getLongitude")
                            
                            val latitude = getLatitudeMethod.invoke(latLonPoint) as Double
                            val longitude = getLongitudeMethod.invoke(latLonPoint) as Double
                            
                            // 创建安全地点对象
                            val safetyLocation = SafetyLocation(
                                id = "amap_$poiId",
                                name = title,
                                type = locationType,
                                latitude = latitude,
                                longitude = longitude,
                                address = adName,
                                description = snippet,
                                capacity = estimateCapacity(locationType, typeCode),
                                facilities = getFacilitiesByType(locationType),
                                isVerified = false // API搜索的地点标记为未验证
                            )
                            
                            locations.add(safetyLocation)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析单个POI失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析搜索结果失败", e)
        }
        
        return locations
    }

    /**
     * 计算步行路径
     */
    suspend fun calculateWalkingRoute(
        userLocation: UserLocation,
        destination: SafetyLocation
    ): NavigationRoute? = suspendCancellableCoroutine { continuation ->
        
        try {
            val routeSearchClass = Class.forName("com.amap.api.services.route.RouteSearch")
            val walkRouteQueryClass = Class.forName("com.amap.api.services.route.WalkRouteQuery")
            val latLonPointClass = Class.forName("com.amap.api.services.core.LatLonPoint")
            
            // 创建起点和终点
            val fromPoint = latLonPointClass.getConstructor(Double::class.java, Double::class.java)
                .newInstance(userLocation.latitude, userLocation.longitude)
            val toPoint = latLonPointClass.getConstructor(Double::class.java, Double::class.java)
                .newInstance(destination.latitude, destination.longitude)
            
            // 创建步行路径查询
            val walkQuery = walkRouteQueryClass.getConstructor(latLonPointClass, latLonPointClass)
                .newInstance(fromPoint, toPoint)
            
            // 创建路径搜索监听器
            val listenerClass = Class.forName("com.amap.api.services.route.RouteSearch\$OnRouteSearchListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onWalkRouteSearched" -> {
                        try {
                            val result = args[0]
                            val errorCode = args[1] as Int
                            
                            if (errorCode == 1000) {
                                val route = parseWalkRouteResult(result, destination)
                                continuation.resume(route)
                            } else {
                                Log.e(TAG, "步行路径搜索失败，错误码: $errorCode")
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析路径结果失败", e)
                            continuation.resumeWithException(e)
                        }
                    }
                    "onDriveRouteSearched", "onBusRouteSearched", "onRideRouteSearched" -> {
                        // 忽略其他路径搜索结果
                    }
                }
                null
            }
            
            // 设置监听器并开始搜索
            val setRouteSearchListenerMethod = routeSearchClass.getMethod("setRouteSearchListener", listenerClass)
            setRouteSearchListenerMethod.invoke(routeSearch, listener)
            
            val calculateWalkRouteAsyncMethod = routeSearchClass.getMethod("calculateWalkRouteAsyn", walkRouteQueryClass)
            calculateWalkRouteAsyncMethod.invoke(routeSearch, walkQuery)
            
        } catch (e: Exception) {
            Log.e(TAG, "步行路径计算设置失败", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * 解析步行路径结果
     */
    private fun parseWalkRouteResult(result: Any, destination: SafetyLocation): NavigationRoute? {
        try {
            // 获取步行路径
            val getWalkPathMethod = result.javaClass.getMethod("getPaths")
            val paths = getWalkPathMethod.invoke(result) as? ArrayList<*>
            
            if (paths.isNullOrEmpty()) return null
            
            val firstPath = paths[0]
            val walkPathClass = Class.forName("com.amap.api.services.route.WalkPath")
            
            if (!walkPathClass.isInstance(firstPath)) return null
            
            // 获取路径信息
            val getDistanceMethod = walkPathClass.getMethod("getDistance")
            val getDurationMethod = walkPathClass.getMethod("getDuration")
            val getStepsMethod = walkPathClass.getMethod("getSteps")
            
            val distance = getDistanceMethod.invoke(firstPath) as Float
            val duration = getDurationMethod.invoke(firstPath) as Long
            val steps = getStepsMethod.invoke(firstPath) as? ArrayList<*>
            
            // 解析步骤
            val routePoints = mutableListOf<RoutePoint>()
            val instructions = mutableListOf<String>()
            
            steps?.forEach { step ->
                try {
                    val walkStepClass = Class.forName("com.amap.api.services.route.WalkStep")
                    if (walkStepClass.isInstance(step)) {
                        val getInstructionMethod = walkStepClass.getMethod("getInstruction")
                        val getPolylineMethod = walkStepClass.getMethod("getPolyline")
                        val getDistanceMethod = walkStepClass.getMethod("getDistance")
                        
                        val instruction = getInstructionMethod.invoke(step) as? String ?: ""
                        val stepDistance = getDistanceMethod.invoke(step) as? Float ?: 0f
                        val polyline = getPolylineMethod.invoke(step) as? ArrayList<*>
                        
                        if (instruction.isNotEmpty()) {
                            instructions.add("📍 ${instruction} (${stepDistance.toInt()}米)")
                        }
                        
                        // 解析路径点
                        polyline?.forEach { point ->
                            try {
                                val latLonPointClass = Class.forName("com.amap.api.services.core.LatLonPoint")
                                if (latLonPointClass.isInstance(point)) {
                                    val getLatitudeMethod = latLonPointClass.getMethod("getLatitude")
                                    val getLongitudeMethod = latLonPointClass.getMethod("getLongitude")
                                    
                                    val lat = getLatitudeMethod.invoke(point) as Double
                                    val lng = getLongitudeMethod.invoke(point) as Double
                                    
                                    routePoints.add(RoutePoint(lat, lng))
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "解析路径点失败", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "解析步骤失败", e)
                }
            }
            
            // 添加起始和结束提示
            val finalInstructions = mutableListOf<String>().apply {
                add("🚀 开始导航到 ${destination.name}")
                add("📏 总距离：${(distance / 1000).let { String.format("%.1f", it) }} 公里")
                add("⏱️ 预计时间：${(duration / 60)} 分钟")
                addAll(instructions)
                add("🎯 到达目的地：${destination.name}")
                add("⚠️ 注意安全，避开危险区域")
            }
            
            return NavigationRoute(
                destination = destination,
                distanceInMeters = distance.toDouble(),
                estimatedDurationMinutes = (duration / 60).toInt(),
                routePoints = routePoints,
                instructions = finalInstructions
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "解析步行路径结果失败", e)
            return null
        }
    }

    /**
     * 估算容量
     */
    private fun estimateCapacity(type: SafetyLocationType, typeCode: String): Int? {
        return when (type) {
            SafetyLocationType.EMERGENCY_SHELTER -> 5000
            SafetyLocationType.HOSPITAL -> 1000
            SafetyLocationType.SCHOOL_PLAYGROUND -> 3000
            SafetyLocationType.PUBLIC_SQUARE -> 10000
            SafetyLocationType.SPORTS_GROUND -> 8000
            SafetyLocationType.PARK -> 5000
            SafetyLocationType.OPEN_AREA -> 2000
        }
    }

    /**
     * 根据类型获取设施信息
     */
    private fun getFacilitiesByType(type: SafetyLocationType): List<String> {
        return when (type) {
            SafetyLocationType.EMERGENCY_SHELTER -> listOf("应急设施", "医疗点", "食物供应", "通讯设备")
            SafetyLocationType.HOSPITAL -> listOf("医疗救护", "急诊科", "手术室", "药房")
            SafetyLocationType.SCHOOL_PLAYGROUND -> listOf("开阔场地", "医务室", "广播系统")
            SafetyLocationType.PUBLIC_SQUARE -> listOf("开阔空间", "应急广播", "安全出口")
            SafetyLocationType.SPORTS_GROUND -> listOf("大型场地", "医疗室", "应急设施")
            SafetyLocationType.PARK -> listOf("绿地空间", "应急通道", "休息设施")
            SafetyLocationType.OPEN_AREA -> listOf("空旷地带", "远离建筑")
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
} 