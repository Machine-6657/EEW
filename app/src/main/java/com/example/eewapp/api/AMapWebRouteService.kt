package com.example.eewapp.api

import android.content.Context
import android.util.Log
import com.example.eewapp.R
import com.example.eewapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 高德地图Web API路径规划服务 (v5)
 * 使用HTTP请求调用高德地图Web服务API进行步行路径规划
 */
class AMapWebRouteService(private val context: Context) {
    
    private val TAG = "AMapWebRouteServiceV5"
    
    // 从资源文件获取高德地图Web API密钥
    private val apiKey: String
        get() = context.getString(R.string.amap_web_api_key)
    
    // 从资源文件获取安全密钥
    private val securityJsCode: String
        get() = try {
            val code = context.getString(R.string.amap_security_js_code)
            if (code == "your_security_js_code_here") "" else code
        } catch (e: Exception) {
            ""
        }
    
    // 步行路径规划API URL (更改为v5版本)
    private val WALKING_ROUTE_URL = "https://restapi.amap.com/v5/direction/walking"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 高德地图v5 API响应数据结构
     */
    @Serializable
    data class AMapRouteApiResponse(
        val status: String,
        val info: String,
        val infocode: String,
        val count: String? = null,
        val route: Route? = null
    )
    
    @Serializable
    data class Route(
        val origin: String,
        val destination: String,
        val paths: List<Path>
    )
    
    @Serializable
    data class Path(
        val distance: String,
        val steps: List<Step>,
        val cost: Cost? = null
    )
    
    @Serializable
    data class Cost(
        val duration: String? = null
    )
    
    @Serializable
    data class Step(
        val instruction: String,
        val orientation: String? = null,
        val road_name: String? = null,
        val step_distance: String,
        val polyline: String? = null,
        val action: String? = null,
        val assistant_action: String? = null,
        val cost: Cost? = null
    )
    
    /**
     * 计算步行路径规划 (v5 API)
     */
    suspend fun calculateWalkingRoute(
        userLocation: UserLocation,
        destination: SafetyLocation
    ): NavigationRoute? = withContext(Dispatchers.IO) {
        try {
            // 检查API密钥
            if (apiKey == "your_amap_web_api_key_here") {
                Log.w(TAG, "请在strings.xml中配置正确的高德地图Web API密钥")
                return@withContext null
            }
            
            Log.d(TAG, "=== 开始步行路径规划 (v5 API) ===")
            Log.d(TAG, "起点: ${userLocation.latitude}, ${userLocation.longitude}")
            Log.d(TAG, "终点: ${destination.latitude}, ${destination.longitude}")
            Log.d(TAG, "目标: ${destination.name}")
            Log.d(TAG, "使用API密钥: ${apiKey.take(8)}...${apiKey.takeLast(4)}")
            
            // 验证输入参数
            if (userLocation.latitude !in -90.0..90.0 || userLocation.longitude !in -180.0..180.0) {
                Log.e(TAG, "用户位置坐标无效: ${userLocation.latitude}, ${userLocation.longitude}")
                return@withContext null
            }
            if (destination.latitude !in -90.0..90.0 || destination.longitude !in -180.0..180.0) {
                Log.e(TAG, "目标位置坐标无效: ${destination.latitude}, ${destination.longitude}")
                return@withContext null
            }
            
            // 构建请求参数 (v5格式: 经度在前，纬度在后) - 使用实际坐标
            val params = buildString {
                append("key=${URLEncoder.encode(apiKey, "UTF-8")}")
                // 使用实际用户位置和目标位置
                append("&origin=${userLocation.longitude},${userLocation.latitude}") 
                append("&destination=${destination.longitude},${destination.latitude}")
                append("&show_fields=polyline,cost")
                append("&output=json")
                // 移除数字签名和其他可选参数，使用最基础的API调用
            }
            
            val requestUrl = "$WALKING_ROUTE_URL?$params"
            Log.d(TAG, "请求URL: ${requestUrl.replace(apiKey, "***API_KEY***")}")
            
            // 发送HTTP请求
            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            // 设置连接参数
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000 // 增加超时时间
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "EarthquakeEarlyWarning/1.0")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Charset", "UTF-8")
            
            Log.d(TAG, "开始连接到服务器...")
            
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            Log.d(TAG, "响应代码: $responseCode")
            Log.d(TAG, "响应消息: $responseMessage")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "HTTP请求成功，开始读取响应...")
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "响应长度: ${response.length} 字符")
                Log.d(TAG, "API响应前500字符: ${response.take(500)}")
                
                // 验证响应是否为空
                if (response.isBlank()) {
                    Log.e(TAG, "API返回空响应")
                    return@withContext null
                }
                
                // 验证响应是否为有效JSON
                if (!response.trim().startsWith("{")) {
                    Log.e(TAG, "API返回的不是有效JSON格式: ${response.take(100)}")
                    return@withContext null
                }
                
                // 解析JSON响应
                val apiResponse = try {
                    json.decodeFromString<AMapRouteApiResponse>(response)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON解析失败", e)
                    Log.e(TAG, "完整响应内容: $response")
                    return@withContext null
                }
                
                Log.d(TAG, "JSON解析成功")
                Log.d(TAG, "状态: ${apiResponse.status}")
                Log.d(TAG, "信息: ${apiResponse.info}")
                Log.d(TAG, "信息代码: ${apiResponse.infocode}")
                Log.d(TAG, "路径数量: ${apiResponse.count}")
                
                if (apiResponse.status == "1" && apiResponse.route != null) {
                    Log.d(TAG, "路径规划成功，开始解析路径...")
                    val result = parseRouteResponseV5(apiResponse, userLocation, destination)
                    if (result != null) {
                        Log.d(TAG, "=== 路径解析成功 (v5) ===")
                        Log.d(TAG, "总距离: ${result.distanceInMeters}米")
                        Log.d(TAG, "预计时间: ${result.estimatedDurationMinutes}分钟")
                        Log.d(TAG, "路径点数量: ${result.routePoints.size}")
                        return@withContext result
                    } else {
                        Log.e(TAG, "路径解析失败 (v5)")
                    }
                } else {
                    Log.e(TAG, "API返回错误 (v5):")
                    Log.e(TAG, "  状态: ${apiResponse.status}")
                    Log.e(TAG, "  信息: ${apiResponse.info}")
                    Log.e(TAG, "  信息代码: ${apiResponse.infocode}")
                    if (apiResponse.infocode == "10000") {
                        Log.i(TAG, "API调用成功但没有找到路径")
                    } else {
                        Log.e(TAG, "API调用失败，错误代码: ${apiResponse.infocode}")
                        when (apiResponse.infocode) {
                            "10001" -> Log.e(TAG, "key不正确或过期")
                            "10002" -> Log.e(TAG, "没有权限使用相应的服务或者请求接口的路径拼写错误")
                            "10003" -> Log.e(TAG, "访问已超出日访问量")
                            "10004" -> Log.e(TAG, "单位时间内访问过于频繁")
                            "10005" -> Log.e(TAG, "IP白名单出错，发送请求的服务器IP不在IP白名单内")
                            "20000" -> Log.e(TAG, "请求参数非法")
                            "20001" -> Log.e(TAG, "缺少必填参数")
                            "20002" -> Log.e(TAG, "请求协议非法")
                            "20003" -> Log.e(TAG, "其他未知错误")
                        }
                    }
                    return@withContext null
                }
            } else {
                Log.e(TAG, "HTTP请求失败")
                Log.e(TAG, "响应代码: $responseCode")
                Log.e(TAG, "响应消息: $responseMessage")
                
                // 读取错误响应
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    try {
                        val errorReader = BufferedReader(InputStreamReader(errorStream, "UTF-8"))
                        val errorResponse = errorReader.readText()
                        Log.e(TAG, "错误响应内容: $errorResponse")
                        errorReader.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "读取错误响应失败", e)
                    }
                }
                
                return@withContext null
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "网络连接失败: 无法解析主机名", e)
            return@withContext null
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "网络连接失败: 连接被拒绝", e)
            return@withContext null
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "网络连接失败: 连接超时", e)
            return@withContext null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "网络IO异常", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "路径规划请求异常", e)
            return@withContext null
        }
        
        Log.w(TAG, "路径规划失败，所有尝试均未成功")
        return@withContext null
    }
    
    /**
     * 解析路径规划响应 (v5)
     */
    private fun parseRouteResponseV5(
        response: AMapRouteApiResponse,
        userLocation: UserLocation,
        destination: SafetyLocation
    ): NavigationRoute? {
        try {
            val route = response.route ?: return null
            val paths = route.paths
            
            if (paths.isEmpty()) {
                Log.w(TAG, "没有找到路径方案 (v5)")
                return null
            }
            
            // 使用第一个路径方案
            val firstPath = paths[0]
            val distanceInMeters = firstPath.distance.toDoubleOrNull() ?: 0.0
            val durationInSeconds = firstPath.cost?.duration?.toLongOrNull() ?: 0L
            val estimatedDurationMinutes = (durationInSeconds / 60).toInt()
            
            Log.d(TAG, "=== 开始解析路径详情 (v5) ===")
            Log.d(TAG, "路径距离: ${distanceInMeters}米")
            Log.d(TAG, "路径耗时: ${estimatedDurationMinutes}分钟 (从API获取)")
            Log.d(TAG, "路径步骤数: ${firstPath.steps.size}")
            
            // 解析路径点
            val routePoints = mutableListOf<RoutePoint>()
            
            // 添加起点
            routePoints.add(RoutePoint(userLocation.latitude, userLocation.longitude, "起点"))
            Log.d(TAG, "添加起点: ${userLocation.latitude}, ${userLocation.longitude}")
            
            // 解析每个步骤的polyline
            var totalDecodedPoints = 0
            for ((stepIndex, step) in firstPath.steps.withIndex()) {
                Log.d(TAG, "--- 步骤 ${stepIndex + 1} (v5) ---")
                Log.d(TAG, "指示: ${step.instruction}")
                Log.d(TAG, "距离: ${step.step_distance}米")
                Log.d(TAG, "道路: ${step.road_name ?: "未知道路"}")
                
                step.polyline?.let { polyline ->
                    Log.d(TAG, "polyline长度: ${polyline.length}")
                    Log.d(TAG, "polyline前100字符: ${polyline.take(100)}")
                    
                    // 验证polyline格式
                    if (polyline.isBlank()) {
                        Log.w(TAG, "步骤${stepIndex + 1}的polyline为空")
                        return@let
                    }
                    
                    if (!polyline.contains(",") || !polyline.contains(";")) {
                        Log.w(TAG, "步骤${stepIndex + 1}的polyline格式可能不正确: ${polyline.take(50)}")
                    }
                    
                    val points = decodePolyline(polyline)
                    totalDecodedPoints += points.size
                    
                    Log.d(TAG, "解码得到${points.size}个路径点")
                    if (points.isNotEmpty()) {
                        Log.d(TAG, "第一个点: ${points.first()}")
                        Log.d(TAG, "最后一个点: ${points.last()}")
                    } else {
                        Log.w(TAG, "步骤${stepIndex + 1}没有解码出任何路径点！")
                    }
                    
                    // 为每个点添加指示信息
                    points.forEachIndexed { index, point ->
                        val instruction = if (index == 0) step.instruction else "继续前进"
                        routePoints.add(RoutePoint(point.latitude, point.longitude, instruction))
                    }
                } ?: run {
                    Log.w(TAG, "步骤${stepIndex + 1}没有polyline数据 (v5)")
                }
            }
            
            // 确保终点在路径中
            if (routePoints.isNotEmpty()) {
                val lastPoint = routePoints.last()
                val distanceToDestination = calculateDistance(
                    lastPoint.latitude, lastPoint.longitude,
                    destination.latitude, destination.longitude
                )
                
                Log.d(TAG, "最后路径点到目标点距离: ${distanceToDestination * 1000}米")
                
                // 如果最后一个点距离目标点超过50米，添加目标点
                if (distanceToDestination > 0.05) { // 50米
                    routePoints.add(RoutePoint(destination.latitude, destination.longitude, "到达${destination.name}"))
                    Log.d(TAG, "添加目标点: ${destination.latitude}, ${destination.longitude}")
                }
            } else {
                // 如果没有解析到任何路径点，至少添加终点
                routePoints.add(RoutePoint(destination.latitude, destination.longitude, "到达${destination.name}"))
                Log.w(TAG, "没有解析到路径点，仅添加终点")
            }
            
            Log.d(TAG, "=== 路径解析完成 (v5) ===")
            Log.d(TAG, "总路径点数: ${routePoints.size}")
            Log.d(TAG, "总解码点数: $totalDecodedPoints")
            Log.d(TAG, "前5个路径点详情:")
            routePoints.take(5).forEachIndexed { index, point ->
                Log.d(TAG, "  点${index + 1}: ${point.latitude}, ${point.longitude} - ${point.instruction}")
            }
            if (routePoints.size > 5) {
                Log.d(TAG, "  ... 还有${routePoints.size - 5}个路径点")
            }
            
            // 使用API返回的耗时，如果API没有返回，则按4km/h估算
            val finalEstimatedDurationMinutes = if (estimatedDurationMinutes > 0) {
                estimatedDurationMinutes
            } else {
                (distanceInMeters / 1000.0 * 15).toInt() // 15分钟/公里
            }
            Log.d(TAG, "最终预估时间: ${finalEstimatedDurationMinutes}分钟")
            
            return NavigationRoute(
                destination = destination,
                routePoints = routePoints,
                distanceInMeters = distanceInMeters,
                estimatedDurationMinutes = finalEstimatedDurationMinutes
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "解析路径响应失败 (v5)", e)
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 解码高德地图的polyline字符串
     * 高德地图使用自定义的polyline编码格式
     */
    private fun decodePolyline(polyline: String): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()
        
        try {
            Log.d(TAG, "开始解码polyline: ${polyline.take(50)}...")
            Log.d(TAG, "完整polyline长度: ${polyline.length} 字符")
            
            // 高德地图的polyline格式：经度,纬度;经度,纬度;...
            val coordPairs = polyline.split(";")
            Log.d(TAG, "分割得到${coordPairs.size}个坐标对")
            
            for ((index, coordPair) in coordPairs.withIndex()) {
                if (coordPair.isBlank()) {
                    Log.d(TAG, "跳过空白坐标对 at index $index")
                    continue
                }
                
                val coords = coordPair.split(",")
                if (coords.size >= 2) {
                    try {
                        val longitude = coords[0].toDouble()
                        val latitude = coords[1].toDouble()
                        
                        // 验证坐标范围
                        if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                            points.add(RoutePoint(latitude, longitude))
                            if (index < 10) { // 记录前10个点的详细信息
                                Log.d(TAG, "点${index + 1}: $latitude, $longitude")
                            }
                        } else {
                            Log.w(TAG, "无效坐标范围: $latitude, $longitude")
                        }
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "坐标解析失败: $coordPair", e)
                    }
                } else {
                    Log.w(TAG, "坐标格式错误(少于2个部分): $coordPair")
                }
            }
            
            Log.d(TAG, "polyline解码完成，共${points.size}个有效点")
            if (points.size < 2) {
                Log.w(TAG, "⚠️ 警告：解码得到的点数过少，可能影响路径显示")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "polyline解码失败", e)
        }
        
        return points
    }
    
    /**
     * 计算两点间距离（公里）
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // 地球半径（公里）
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * 测试API密钥配置是否正确
     */
    suspend fun testApiConfiguration(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== 测试API配置 (使用v3 geocode接口测试key有效性) ===")
            Log.d(TAG, "API密钥: ${apiKey.take(8)}...${apiKey.takeLast(4)}")
            
            // 测试简单的地理编码API (v3)
            val testUrl = "https://restapi.amap.com/v3/geocode/geo?key=${URLEncoder.encode(apiKey, "UTF-8")}&address=成都市天府广场&output=json"
            
            Log.d(TAG, "测试URL: ${testUrl.replace(apiKey, "***API_KEY***")}")
            
            val url = URL(testUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            Log.d(TAG, "测试响应代码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "测试响应: ${response.take(200)}")
                
                val jsonResponse = json.decodeFromString<kotlinx.serialization.json.JsonObject>(response)
                val status = jsonResponse["status"]?.toString()?.trim('"')
                
                return@withContext status == "1"
            } else {
                Log.e(TAG, "测试失败，HTTP状态码: $responseCode")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "API配置测试失败", e)
            return@withContext false
        }
    }
} 