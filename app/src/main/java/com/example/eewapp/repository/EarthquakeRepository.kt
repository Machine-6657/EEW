package com.example.eewapp.repository

import android.util.Log
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.UserLocation
import com.example.eewapp.network.SichuanEarthquakeWebSocketClient
import com.example.eewapp.utils.EarthquakeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay

/**
 * Repository for earthquake data
 */
class EarthquakeRepository {
    private val TAG = "EarthquakeRepository"
    
    // 创建四川地震局 WebSocket 客户端
    private val sichuanClient = SichuanEarthquakeWebSocketClient()
    
    // OkHttp 客户端用于 WebSocket 连接和 HTTP 请求
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // WebSocket 连接对象
    private var webSocket: WebSocket? = null
    
    // WebSocket 连接状态
    private var webSocketConnected = false
    
    // Gson 解析器
    private val gson = Gson()
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Flow of recent earthquakes
    private val _recentEarthquakes = MutableStateFlow<List<Earthquake>>(emptyList())
    val recentEarthquakes: Flow<List<Earthquake>> = _recentEarthquakes.asStateFlow()
    
    // Flow of significant earthquakes (those that might affect the user)
    private val _significantEarthquakes = MutableStateFlow<List<EarthquakeImpact>>(emptyList())
    val significantEarthquakes: Flow<List<EarthquakeImpact>> = _significantEarthquakes.asStateFlow()
    
    // Current user location
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: Flow<UserLocation?> = _userLocation.asStateFlow()
    
    // ISO8601 date formatter for API requests
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    init {
        Log.d(TAG, "EarthquakeRepository 初始化，先加载中国地震台网和四川地震局数据")
        Log.d(TAG, "注意：应用程序现在只显示真实API返回的地震数据，不显示模拟数据")
        // 立即通过 HTTP 请求加载初始数据
        scope.launch {
            // 首先尝试获取中国地震台网数据（全国数据）
            fetchChinaEarthquakeNetworkData()
            // 然后补充四川地震局数据（可能有更详细的四川地区数据）
            fetchSichuanDataDirectly()
        }
        
        // 尝试连接四川地震局WebSocket客户端
        try {
            Log.d(TAG, "正在通过sichuanClient连接四川地震局WebSocket...")
            sichuanClient.connect()
            
            // 监听从sichuanClient收到的地震数据
            scope.launch {
                sichuanClient.earthquakes.collectLatest { earthquakes ->
                    if (earthquakes.isNotEmpty()) {
                        Log.d(TAG, "从sichuanClient收到${earthquakes.size}条地震数据")
                        // 更新地震列表，避免重复
                        val currentList = _recentEarthquakes.value.toMutableList()
                        var updated = false
                        
                        for (earthquake in earthquakes) {
                            val existingIndex = currentList.indexOfFirst { 
                                it.id == earthquake.id || 
                                (it.location.place == earthquake.location.place && 
                                 Math.abs(it.time.time - earthquake.time.time) < 60000) // 同一分钟内
                            }
                            
                            if (existingIndex < 0) {
                                currentList.add(0, earthquake)
                                updated = true
                                Log.d(TAG, "添加了来自sichuanClient的新地震信息: ${earthquake.title}")
                            }
                        }
                        
                        // 保持列表不超过20个项目
                        if (currentList.size > 20) {
                            currentList.subList(20, currentList.size).clear()
                        }
                        
                        if (updated) {
                            _recentEarthquakes.value = currentList
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化sichuanClient失败", e)
        }
        
        // 延迟初始化 WebSocket 连接，避免竞态条件
        Handler(Looper.getMainLooper()).postDelayed({
            initWebSocket()
        }, 2000)
    }
    
    /**
     * 初始化 WebSocket 连接
     */
    private fun initWebSocket() {
        try {
            Log.d(TAG, "正在尝试初始化四川地震局 WebSocket 连接")
            val request = Request.Builder()
                .url("wss://ws-api.wolfx.jp/sc_eew")
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "四川地震局 WebSocket 连接已打开")
                    webSocketConnected = true
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        Log.d(TAG, "收到四川地震局 WebSocket 消息: $text")
                        val earthquake = parseSichuanEarthquakeJson(text)
                        if (earthquake != null) {
                            // 确保不添加重复的地震 - 检查ID或相同位置和时间
                            val existingIndex = _recentEarthquakes.value.indexOfFirst { 
                                it.id == earthquake.id || 
                                (it.location.place == earthquake.location.place && 
                                 Math.abs(it.time.time - earthquake.time.time) < 60000) // 同一分钟内
                            }
                            
                            if (existingIndex < 0) {  // 如果不存在重复记录
                                val updatedList = _recentEarthquakes.value.toMutableList()
                                updatedList.add(0, earthquake)
                                // 保持列表不超过20个项目
                                if (updatedList.size > 20) {
                                    updatedList.removeAt(updatedList.size - 1)
                                }
                                _recentEarthquakes.value = updatedList
                                Log.d(TAG, "添加了来自四川地震局WebSocket的新地震信息: ${earthquake.title}")
                            } else {
                                // 更新已存在的记录
                                val updatedList = _recentEarthquakes.value.toMutableList()
                                updatedList[existingIndex] = earthquake
                                _recentEarthquakes.value = updatedList
                                Log.d(TAG, "更新已存在的地震记录(WebSocket): ${earthquake.id}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析四川地震局WebSocket消息失败", e)
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "四川地震局 WebSocket 正在关闭: $code, $reason")
                    webSocketConnected = false
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "四川地震局 WebSocket 已关闭: $code, $reason")
                    webSocketConnected = false
                    // 尝试重新连接
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            initWebSocket()
                        } catch (e: Exception) {
                            Log.e(TAG, "WebSocket 重新连接失败", e)
                        }
                    }, 5000)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "四川地震局 WebSocket 连接失败", t)
                    webSocketConnected = false
                    
                    // 打印更详细的错误信息用于调试
                    Log.e(TAG, "WebSocket连接失败详情: ${t.message}, 响应码: ${response?.code}, 响应消息: ${response?.message}")
                    
                    // WebSocket 失败后立即尝试通过 HTTP 获取数据
                    scope.launch {
                        fetchSichuanDataDirectly()
                    }
                    
                    // 5秒后尝试重新连接
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            initWebSocket()
                        } catch (e: Exception) {
                            Log.e(TAG, "WebSocket 重新连接失败", e)
                            // 重连失败后确保再次尝试直接 HTTP 请求
                            scope.launch {
                                fetchSichuanDataDirectly()
                            }
                        }
                    }, 5000)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "初始化四川地震局 WebSocket 失败", e)
            // 初始化失败后立即尝试 HTTP 请求
            scope.launch {
                fetchSichuanDataDirectly()
            }
        }
    }
    
    /**
     * 直接通过HTTP请求获取四川地震局数据
     * @return 是否成功获取到数据
     */
    suspend fun fetchSichuanDataDirectly(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "【开始】尝试获取四川地震局数据...")
        var success = false
        
        // 检查是否已经有四川地震局的数据
        val hasSichuanData = _recentEarthquakes.value.any { 
            it.title.contains("[四川地震局]") || 
            it.title.contains("[四川地震局数据]") || 
            it.title.contains("[四川地震局测试]") ||
            it.title.contains("[四川地震局参考数据]")
        }
        
        // 如果已有四川地震局数据，则不重复获取
        if (hasSichuanData) {
            Log.d(TAG, "已有四川地震局数据，不重新获取")
            return@withContext true
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url("https://api.wolfx.jp/sc_eew.json")
            .build()
            
        Log.d(TAG, "发送HTTP请求到四川地震局API: ${request.url}")
        
        try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "四川地震局API响应状态码: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null && responseBody.isNotEmpty()) {
                    Log.d(TAG, "四川地震局API完整响应内容长度: ${responseBody.length}")
                    Log.d(TAG, "四川地震局API响应内容: $responseBody")
                    
                    try {
                        val earthquake = parseSichuanEarthquakeJson(responseBody)
                        if (earthquake != null) {
                            Log.d(TAG, "成功解析四川地震局数据: ${earthquake.title}")
                            
                            // 数据校验 - 检查震级和位置是否有效
                            if (earthquake.magnitude <= 0) {
                                Log.w(TAG, "四川地震局数据震级无效: ${earthquake.magnitude}")
                            }
                            if (earthquake.location.place.isBlank() || earthquake.location.place == "未知位置") {
                                Log.w(TAG, "四川地震局数据位置无效: ${earthquake.location.place}")
                            }
                            
                            // 检查是否存在相同ID的地震记录
                            val currentList = _recentEarthquakes.value.toMutableList()
                            val existingIndex = currentList.indexOfFirst { 
                                it.id == earthquake.id || 
                                (it.location.place == earthquake.location.place && 
                                 Math.abs(it.time.time - earthquake.time.time) < 60000) // 同一分钟内的同一地点视为同一地震
                            }
                            
                            if (existingIndex >= 0) {
                                Log.d(TAG, "更新已存在的地震记录: ${earthquake.title}")
                                currentList[existingIndex] = earthquake
                            } else {
                                // 将新地震数据添加到列表中
                                Log.d(TAG, "添加新四川地震局地震记录: ${earthquake.title}")
                                currentList.add(0, earthquake)
                                
                                // 限制列表大小为20个条目
                                if (currentList.size > 20) {
                                    Log.d(TAG, "四川地震数据超过20条，移除最旧记录")
                                    currentList.removeAt(currentList.size - 1)
                                }
                            }
                            
                            _recentEarthquakes.value = currentList
                            
                            // 打印更新后的列表大小和内容摘要
                            Log.d(TAG, "成功添加/更新四川地震局数据，当前地震记录数: ${_recentEarthquakes.value.size}")
                            if (_recentEarthquakes.value.isNotEmpty()) {
                                Log.d(TAG, "地震列表第一条记录: ${_recentEarthquakes.value.first().title}")
                                if (_recentEarthquakes.value.size > 1) {
                                    Log.d(TAG, "地震列表第二条记录: ${_recentEarthquakes.value[1].title}")
                                }
                            } else {
                                Log.e(TAG, "严重错误: 添加四川地震局数据后地震列表为空!")
                            }
                            
                            success = true
                        } else {
                            Log.w(TAG, "无法解析四川地震局数据，返回为null")
                            success = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析四川地震局数据时出错: ${e.message}", e)
                        success = false
                    }
                } else {
                    Log.w(TAG, "四川地震局HTTP响应为空或内容为空")
                    success = false
                }
            } else {
                Log.e(TAG, "四川地震局HTTP请求失败，状态码: ${response.code}，消息: ${response.message}")
                success = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行四川地震局HTTP请求时出错: ${e.message}", e)
            success = false
        }
        
        Log.d(TAG, "【结束】四川地震局数据获取${if (success) "成功" else "失败"}, 当前地震记录数: ${_recentEarthquakes.value.size}")
        success
    }
    
    /**
     * 检查JSON是否有效
     */
    private fun isValidJson(json: String): Boolean {
        return try {
            gson.fromJson(json, Any::class.java)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 解析四川地震局JSON对象
     */
    private fun parseSichuanEarthquakeJson(jsonObject: com.google.gson.JsonObject): Earthquake? {
        return try {
            // 首先检查是否为心跳、pong等非地震数据消息
            if (jsonObject.has("type")) {
                val type = jsonObject.get("type").asString
                if (type == "heartbeat" || type == "pong") {
                    Log.d(TAG, "收到WebSocket心跳/pong消息，不作为地震数据处理")
                    return null
                }
            }
            
            // 检查是否包含必要的地震数据字段
            val hasEarthquakeData = jsonObject.has("Magnitude") || 
                                 jsonObject.has("Magunitude") || 
                                 (jsonObject.has("HypoCenter") && !jsonObject.get("HypoCenter").isJsonNull)
            
            if (!hasEarthquakeData) {
                Log.d(TAG, "收到的消息不包含必要的地震数据字段，忽略")
                return null
            }
            
            // 获取主要字段，记录所有字段名便于调试
            val fields = jsonObject.keySet()
            Log.d(TAG, "四川地震局JSON字段集: $fields")
            
            if (fields.isEmpty()) {
                Log.e(TAG, "四川地震局JSON没有字段")
                return null
            }
            
            // 尝试获取ID（可能有多种字段名）
            val id = try { 
                jsonObject.get("ID")?.asString 
                    ?: jsonObject.get("EventID")?.asString 
                    ?: "sc-${System.currentTimeMillis()}"
            } catch (e: Exception) {
                // 如果ID是数字类型，转为字符串
                try {
                    jsonObject.get("ID")?.asInt?.toString()
                        ?: jsonObject.get("EventID")?.asInt?.toString()
                        ?: "sc-${System.currentTimeMillis()}"
                } catch (e2: Exception) {
                    Log.e(TAG, "获取ID字段失败: ${e2.message}")
                    "sc-fallback-id-${System.currentTimeMillis()}"
                }
            }
            
            // 尝试多种可能的拼写方式（API返回中拼写可能为Magunitude）
            val magnitude = try {
                when {
                    jsonObject.has("Magnitude") -> jsonObject.get("Magnitude").asDouble
                    jsonObject.has("Magunitude") -> jsonObject.get("Magunitude").asDouble
                    else -> {
                        Log.w(TAG, "找不到震级字段，使用默认值 5.0")
                        5.0  // 默认震级
                    }
                }
            } catch (e: Exception) {
                // 可能是字符串类型，尝试转换
                try {
                    when {
                        jsonObject.has("Magnitude") -> jsonObject.get("Magnitude").asString.toDoubleOrNull() ?: 5.0
                        jsonObject.has("Magunitude") -> jsonObject.get("Magunitude").asString.toDoubleOrNull() ?: 5.0
                        else -> 5.0
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "获取震级字段失败: ${e2.message}")
                    5.0  // 默认震级
                }
            }
            
            // 获取位置信息
            val hypoCenter = try {
                jsonObject.get("HypoCenter")?.asString ?: "未知位置"
            } catch (e: Exception) {
                Log.e(TAG, "获取位置字段失败: ${e.message}")
                "四川省某地区"
            }
            
            // 获取经纬度
            val latitude = try { 
                jsonObject.get("Latitude")?.asDouble ?: 30.6570  // 默认成都坐标
            } catch (e: Exception) {
                // 可能是字符串类型
                try {
                    jsonObject.get("Latitude")?.asString?.toDoubleOrNull() ?: 30.6570
                } catch (e2: Exception) {
                    Log.e(TAG, "获取纬度字段失败: ${e2.message}")
                    30.6570  // 默认成都坐标
                }
            }
            
            val longitude = try { 
                jsonObject.get("Longitude")?.asDouble ?: 104.0650  // 默认成都坐标 
            } catch (e: Exception) {
                // 可能是字符串类型
                try {
                    jsonObject.get("Longitude")?.asString?.toDoubleOrNull() ?: 104.0650
                } catch (e2: Exception) {
                    Log.e(TAG, "获取经度字段失败: ${e2.message}")
                    104.0650  // 默认成都坐标
                }
            }
            
            // 深度可能为null或字符串
            val depth = try {
                val depthElement = jsonObject.get("Depth")
                if (depthElement != null && !depthElement.isJsonNull) {
                    depthElement.asDouble
                } else {
                    Log.d(TAG, "深度字段为null，使用默认值 10.0")
                    10.0  // 默认深度10公里
                }
            } catch (e: Exception) {
                // 可能是字符串类型
                try {
                    val depthStr = jsonObject.get("Depth")?.asString
                    if (depthStr.isNullOrEmpty() || depthStr == "null") {
                        10.0
                    } else {
                        depthStr.toDoubleOrNull() ?: 10.0
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "获取深度字段失败: ${e2.message}")
                    10.0  // 默认深度
                }
            }
            
            // 获取时间，可能是OriginTime或ReportTime
            val originTime = try {
                jsonObject.get("OriginTime")?.asString 
                    ?: jsonObject.get("ReportTime")?.asString
                    ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "获取时间字段失败: ${e.message}")
                ""
            }
            
            Log.d(TAG, "解析四川地震局数据: ID=$id, 震级=$magnitude, 位置=$hypoCenter, 时间=$originTime")
            
            // 解析时间
            val timeFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
                SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA),
                SimpleDateFormat("yyyy年MM月dd日 HH时mm分ss秒", Locale.CHINA)
            )
            
            // 为所有格式设置时区
            timeFormats.forEach { it.timeZone = TimeZone.getTimeZone("GMT+8") }
            
            var time = Date()
            if (originTime.isNotEmpty()) {
                // 尝试所有时间格式
                var parsed = false
                for (format in timeFormats) {
                    try {
                        val parsedTime = format.parse(originTime)
                        if (parsedTime != null) {
                            time = parsedTime
                            parsed = true
                            Log.d(TAG, "成功解析时间: $originTime -> $time")
                            break
                        }
                    } catch (e: Exception) {
                        // 继续尝试下一种格式
                    }
                }
                
                if (!parsed) {
                    Log.e(TAG, "无法解析时间: $originTime，使用当前时间")
                }
            } else {
                Log.w(TAG, "时间字段为空，使用当前时间")
            }
            
            // 构建标题，添加中文标记以区分来源
            val title = "M $magnitude - $hypoCenter [四川地震局]"
            
            Earthquake(
                id = id.toString(), // 确保ID是字符串
                magnitude = magnitude,
                location = com.example.eewapp.data.EarthquakeLocation(
                    latitude = latitude,
                    longitude = longitude,
                    place = hypoCenter
                ),
                depth = depth,
                time = time,
                title = title,
                url = "",
                tsunamiWarning = false
            ).also {
                Log.d(TAG, "成功创建地震对象: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析四川地震局JSON数据失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 解析四川地震局JSON字符串
     */
    private fun parseSichuanEarthquakeJson(jsonString: String): Earthquake? {
        try {
            Log.d(TAG, "开始解析四川地震局JSON字符串: ${jsonString.take(100)}...")
            
            if (jsonString.isBlank()) {
                Log.e(TAG, "四川地震局JSON字符串为空")
                return null
            }
            
            // 快速检查是否为心跳消息
            if (jsonString.contains("\"type\":\"heartbeat\"") || jsonString.contains("\"type\": \"heartbeat\"") || 
                jsonString.contains("\"type\":\"pong\"") || jsonString.contains("\"type\": \"pong\"")) {
                Log.d(TAG, "收到WebSocket心跳/pong消息字符串，不作为地震数据处理")
                return null
            }
            
            // 检查是否包含地震必要数据
            val containsEarthquakeData = jsonString.contains("\"Magnitude\":") || 
                                      jsonString.contains("\"Magnitude\": ") || 
                                      jsonString.contains("\"Magunitude\":") ||
                                      jsonString.contains("\"HypoCenter\":")
            
            if (!containsEarthquakeData) {
                Log.d(TAG, "收到的JSON字符串不包含必要的地震数据字段，忽略")
                return null
            }
            
            // 检查JSON字符串是否完整
            val trimmedJson = jsonString.trim()
            
            // 尝试修复或补全不完整的JSON
            val fixedJson = try {
                var json = trimmedJson
                
                // 检查并修复开头
                if (!json.startsWith("{")) {
                    json = "{" + json
                    Log.w(TAG, "JSON缺少开始大括号，已添加")
                }
                
                // 检查并修复结尾
                if (!json.endsWith("}")) {
                    json = json + "}"
                    Log.w(TAG, "JSON缺少结束大括号，已添加")
                }
                
                // 基本检查，如果JSON仍然不完整（比如缺少中间部分）
                if (!isValidJson(json)) {
                    Log.e(TAG, "修复后的JSON仍然无效，无法解析")
                    return null
                }
                
                json
            } catch (e: Exception) {
                Log.e(TAG, "无法修复不完整的JSON: ${e.message}")
                return null
            }
            
            Log.d(TAG, "尝试使用修复后的JSON: ${fixedJson.take(100)}...")
            
            // 解析JSON对象
            try {
                val jsonObject = gson.fromJson(fixedJson, com.google.gson.JsonObject::class.java)
                if (jsonObject == null) {
                    Log.e(TAG, "JSON解析为null")
                    return null
                }
                // 调用JsonObject版本的解析方法
                return parseSichuanEarthquakeJson(jsonObject)
            } catch (e: Exception) {
                Log.e(TAG, "JSON解析异常: ${e.message}", e)
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析四川地震局JSON字符串失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 获取四川地震局的地震数据
     * @return 是否成功获取到四川地震局数据
     */
    suspend fun fetchSichuanEarthquakeData(): Boolean {
        Log.d(TAG, "正在获取四川地震局地震数据...")
        
        // 首先检查是否已经有四川地震局的数据
        val hasSichuanData = _recentEarthquakes.value.any { 
            it.title.contains("[四川地震局]") || 
            it.title.contains("[四川地震局数据]") || 
            it.title.contains("[四川地震局测试]") ||
            it.title.contains("[四川地震局参考数据]")
        }
        
        if (hasSichuanData) {
            Log.d(TAG, "已有四川地震局数据，不重复获取")
            return true
        }
        
        // 首先尝试通过HTTP API获取，这是更可靠的方式
        val httpSuccess = fetchSichuanDataDirectly()
        
        // 如果HTTP请求成功，不再需要WebSocket
        if (httpSuccess) {
            Log.d(TAG, "HTTP请求成功获取四川地震局数据，跳过WebSocket")
            return true
        }
        
        // 检查 WebSocket 连接状态
        if (!webSocketConnected) {
            Log.d(TAG, "WebSocket未连接，尝试重新初始化...")
            initWebSocket()
        } else {
            // WebSocket 已连接，记录日志
            Log.d(TAG, "WebSocket已连接，等待数据...")
        }
        
        // 由于WebSocket是异步的，我们无法立即知道是否成功，
        // 但我们已经尝试了HTTP请求，所以返回HTTP请求的结果
        return httpSuccess
    }
    
    /**
     * Fetch recent earthquakes from the API
     * @param minMagnitude Minimum magnitude to fetch
     * @param limit Maximum number of earthquakes to fetch
     */
    suspend fun fetchRecentEarthquakes(minMagnitude: Double = 4.5, limit: Int = 100) {
        Log.d(TAG, "获取最近地震数据...")
        
        // 首先获取中国地震台网数据（全国数据）
        val cencSuccess = fetchChinaEarthquakeNetworkData()
        Log.d(TAG, "中国地震台网数据获取${if (cencSuccess) "成功" else "失败"}")
        
        // 接着获取四川地震局的数据（可能有更详细的四川地区数据）
        val sichuanSuccess = fetchSichuanEarthquakeData()
        Log.d(TAG, "四川地震局数据获取${if (sichuanSuccess) "成功" else "失败"}")
        
        // 等待一段时间，让WebSocket有机会获取数据
        delay(2000)
        
        // 如果没有获取到任何数据，显示提示信息
        if (_recentEarthquakes.value.isEmpty()) {
            Log.d(TAG, "未能获取到任何地震数据")
        }
    }
    
    /**
     * Fetch earthquakes from a specific time range
     * @param startTime Start time
     * @param endTime End time
     * @param minMagnitude Minimum magnitude to fetch
     */
    suspend fun fetchEarthquakesByTimeRange(
        startTime: Date,
        endTime: Date = Date(),
        minMagnitude: Double = 4.0
    ) {
        // 首先尝试从中国地震台网获取数据
        fetchChinaEarthquakeNetworkData()
        
        // 然后尝试从四川地震局获取数据
        fetchSichuanEarthquakeData()
        
        // 如果两个数据源都没有数据，记录一个错误日志
        if (_recentEarthquakes.value.isEmpty()) {
            Log.e(TAG, "未能获取到指定时间范围内的地震数据")
        }
    }
    
    /**
     * Fetch earthquakes from the last 24 hours
     */
    suspend fun fetchLast24HoursEarthquakes(minMagnitude: Double = 4.0) {
        val calendar = Calendar.getInstance()
        val endTime = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = calendar.time
        
        fetchEarthquakesByTimeRange(startTime, endTime, minMagnitude)
    }
    
    /**
     * Update the earthquake impacts based on the user's location
     */
    private fun updateEarthquakeImpacts() {
        val location = _userLocation.value ?: return
        val earthquakes = _recentEarthquakes.value
        
        val impacts = earthquakes.map { earthquake ->
            EarthquakeUtils.calculateEarthquakeImpact(earthquake, location)
        }.filter {
            // Only include earthquakes that might affect the user
            // For example, those that are recent and close enough
            it.secondsUntilArrival > 0 && it.intensity.level >= 2
        }.sortedBy {
            // Sort by arrival time
            it.estimatedArrivalTime
        }
        
        _significantEarthquakes.value = impacts
    }
    
    /**
     * Get the most significant earthquake impact (the one that will arrive soonest)
     */
    fun getMostSignificantImpact(): EarthquakeImpact? {
        return _significantEarthquakes.value.firstOrNull()
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        // 关闭 WebSocket 连接
        webSocket?.close(1000, "应用关闭")
        webSocket = null
        webSocketConnected = false
        
        // 旧的客户端清理
        sichuanClient.disconnect()
    }
    
    /**
     * 清除所有现有的地震数据
     */
    fun clearExistingData() {
        Log.d(TAG, "清除所有现有地震数据")
        _recentEarthquakes.value = emptyList()
        _significantEarthquakes.value = emptyList()
    }

    /**
     * Update the user's location
     */
    fun updateUserLocation(location: UserLocation) {
        Log.d(TAG, "Updating user location: $location")
        _userLocation.value = location
        // Recalculate impacts when location changes
        updateEarthquakeImpacts()
    }

    /**
     * 添加四川地震局测试数据
     * 修改后的版本不再添加测试数据，直接返回false
     */
    private fun addSichuanTestData(): Boolean {
        Log.d(TAG, "四川地震局数据获取失败，不添加测试数据")
        return false
    }

    /**
     * 直接通过HTTP请求获取中国地震台网数据
     * @return 是否成功获取到数据
     */
    suspend fun fetchChinaEarthquakeNetworkData(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "【开始】尝试获取中国地震台网数据...")
        var success = false
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url("https://api.wolfx.jp/cenc_eqlist.json")
            .build()
            
        Log.d(TAG, "发送HTTP请求到中国地震台网API: ${request.url}")
        
        try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "中国地震台网API响应状态码: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null && responseBody.isNotEmpty()) {
                    Log.d(TAG, "中国地震台网API响应内容长度: ${responseBody.length}")
                    Log.d(TAG, "中国地震台网API响应内容前100字符: ${responseBody.take(100)}...")
                    
                    try {
                        val earthquakes = parseChinaEarthquakeNetworkJson(responseBody)
                        Log.d(TAG, "解析中国地震台网数据完成，获取到 ${earthquakes.size} 条地震记录")
                        
                        // 无论是否解析到地震数据，都添加模拟数据确保UI有内容显示
                        val combinedList = mutableListOf<Earthquake>()
                        
                        // 1. 首先保留现有的四川地震局数据（如果有的话）
                        val currentList = _recentEarthquakes.value.toMutableList()
                        val sichuanData = currentList.filter { 
                            it.title.contains("[四川地震局]") || 
                            it.title.contains("[四川地震局数据]") || 
                            it.title.contains("[四川地震局测试]") ||
                            it.title.contains("[四川地震局参考数据]")
                        }
                        combinedList.addAll(sichuanData)
                        Log.d(TAG, "添加四川地震局数据后列表大小: ${combinedList.size}")
                        
                        // 2. 添加API返回的数据
                        if (earthquakes.isNotEmpty()) {
                            // 将地震数据分为国内和国外两组
                            val domesticEarthquakes = earthquakes.filter { 
                                isLocationInChina(it.location.place)
                            }
                            val foreignEarthquakes = earthquakes.filter { 
                                !isLocationInChina(it.location.place)
                            }
                            
                            Log.d(TAG, "API返回 - 国内地震: ${domesticEarthquakes.size} 条, 国外地震: ${foreignEarthquakes.size} 条")
                            
                            // 添加国内地震数据，避免重复
                            for (earthquake in domesticEarthquakes) {
                                if (!combinedList.any { it.location.place == earthquake.location.place && 
                                                    Math.abs(it.time.time - earthquake.time.time) < 180000 }) {
                                    combinedList.add(earthquake)
                                }
                            }
                            
                            // 添加国外地震数据，避免重复
                            for (earthquake in foreignEarthquakes) {
                                if (!combinedList.any { it.location.place == earthquake.location.place && 
                                                     Math.abs(it.time.time - earthquake.time.time) < 180000 }) {
                                    combinedList.add(earthquake)
                                }
                            }
                        }
                        
                        // 按时间排序，最新的在前面
                        combinedList.sortByDescending { it.time }
                        
                        // 限制数量
                        if (combinedList.size > 50) {
                            val trimmedList = combinedList.take(50)
                            _recentEarthquakes.value = trimmedList
                        } else {
                            _recentEarthquakes.value = combinedList
                        }
                        
                        Log.d(TAG, "【最终】地震列表大小: ${_recentEarthquakes.value.size}")
                        if (_recentEarthquakes.value.isNotEmpty()) {
                            for (i in 0 until minOf(3, _recentEarthquakes.value.size)) {
                                Log.d(TAG, "地震记录[$i]: ${_recentEarthquakes.value[i].title}, 时间: ${_recentEarthquakes.value[i].time}")
                            }
                        }
                        
                        // 更新震感影响
                        updateEarthquakeImpacts()
                        success = true
                    } catch (e: Exception) {
                        Log.e(TAG, "解析中国地震台网数据时出错: ${e.message}", e)
                        // 不添加模拟数据，保持列表为空或原有内容
                        success = false
                    }
                } else {
                    Log.w(TAG, "中国地震台网HTTP响应为空或内容为空")
                    success = false
                }
            } else {
                Log.e(TAG, "中国地震台网HTTP请求失败，状态码: ${response.code}，消息: ${response.message}")
                success = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行中国地震台网HTTP请求时出错: ${e.message}", e)
            success = false
        }
        
        Log.d(TAG, "【结束】中国地震台网数据获取${if (success) "成功" else "失败"}, 当前地震记录数: ${_recentEarthquakes.value.size}")
        return@withContext success
    }
    
    /**
     * 添加中国地震台网模拟数据
     */
    private fun addChinaNetworkMockData(list: MutableList<Earthquake>) {
        Log.d(TAG, "添加中国地震台网模拟数据")
        // 添加几条国内模拟地震数据
        val locations = listOf("四川省成都市", "云南省昆明市", "甘肃省兰州市", "新疆乌鲁木齐市", "北京市", "上海市")
        val magnitudes = listOf(4.5, 3.8, 5.2, 4.1, 3.5, 4.7)
        
        for (i in 0 until minOf(locations.size, magnitudes.size)) {
            val now = System.currentTimeMillis()
            val mockTime = Date(now - i * 3600 * 1000) // 每小时一个地震
            val mockEarthquake = Earthquake(
                id = "cenc-mock-${now}-$i",
                title = "M ${magnitudes[i]} - ${locations[i]} [中国]",
                time = mockTime,
                magnitude = magnitudes[i],
                location = com.example.eewapp.data.EarthquakeLocation(
                    latitude = 30.0 + i * 2.0,
                    longitude = 104.0 + i * 2.0,
                    place = locations[i]
                ),
                depth = 10.0,
                url = "",
                tsunamiWarning = false
            )
            list.add(mockEarthquake)
        }
        
        // 添加几条国外模拟地震数据
        val foreignLocations = listOf("日本东京", "美国加利福尼亚", "印度尼西亚", "菲律宾", "意大利")
        val foreignMagnitudes = listOf(6.0, 5.4, 5.8, 5.2, 4.9)
        
        for (i in 0 until minOf(foreignLocations.size, foreignMagnitudes.size)) {
            val now = System.currentTimeMillis()
            val mockTime = Date(now - i * 2400 * 1000) // 每40分钟一个地震
            val mockEarthquake = Earthquake(
                id = "cenc-foreign-mock-${now}-$i",
                title = "M ${foreignMagnitudes[i]} - ${foreignLocations[i]} [世界]",
                time = mockTime,
                magnitude = foreignMagnitudes[i],
                location = com.example.eewapp.data.EarthquakeLocation(
                    latitude = 35.0 - i * 3.0,
                    longitude = 140.0 - i * 10.0,
                    place = foreignLocations[i]
                ),
                depth = 15.0,
                url = "",
                tsunamiWarning = foreignMagnitudes[i] >= 6.0
            )
            list.add(mockEarthquake)
        }
        
        Log.d(TAG, "已添加 ${locations.size + foreignLocations.size} 条模拟地震数据")
    }
    
    /**
     * 判断位置是否在中国境内
     */
    private fun isLocationInChina(location: String): Boolean {
        // 检查地点名称是否为中国地区
        val chinaKeywords = listOf(
            "四川", "云南", "贵州", "西藏", "新疆", "青海", "甘肃", "陕西", "山西", "河南", 
            "湖北", "湖南", "广东", "广西", "海南", "福建", "江西", "安徽", "浙江", "江苏", 
            "上海", "山东", "河北", "北京", "天津", "辽宁", "吉林", "黑龙江", "内蒙古", "宁夏", "重庆",
            "台湾", "香港", "澳门"
        )
        
        return chinaKeywords.any { keyword -> location.contains(keyword) }
    }
    
    /**
     * 解析中国地震台网的JSON数据
     * @param jsonString 原始JSON字符串
     * @return 解析出的地震列表
     */
    private fun parseChinaEarthquakeNetworkJson(jsonString: String): List<Earthquake> {
        val earthquakes = mutableListOf<Earthquake>()
        
        try {
            Log.d(TAG, "开始解析中国地震台网数据，字符串长度: ${jsonString.length}")
            
            // 尝试修复可能不完整的JSON
            var processedJson = jsonString
            if (!isValidJson(processedJson)) {
                Log.w(TAG, "中国地震台网JSON字符串不是有效JSON，尝试修复")
                processedJson = fixJsonString(processedJson)
                if (!isValidJson(processedJson)) {
                    Log.e(TAG, "JSON字符串修复失败，无法解析")
                    return emptyList()
                }
                Log.d(TAG, "JSON字符串修复成功")
            }
            
            val jsonObject = gson.fromJson(processedJson, com.google.gson.JsonObject::class.java)
            
            // 检查是否包含md5字段，确认是中国地震台网的数据格式
            if (!jsonObject.has("md5")) {
                Log.w(TAG, "数据可能不是标准的中国地震台网格式，缺少md5字段")
            }
            
            // 遍历所有字段，查找格式为"No1", "No2"等的条目
            var parsedCount = 0
            var errorCount = 0
            
            for (key in jsonObject.keySet()) {
                // 跳过md5键
                if (key == "md5") continue
                
                try {
                    // 确保键是"No"开头的数字格式
                    if (!key.startsWith("No")) {
                        Log.w(TAG, "跳过非标准键: $key")
                        continue
                    }
                    
                    // 获取JSON对象，如果无法获取则尝试从字符串转换
                    val eqObject = try {
                        jsonObject.getAsJsonObject(key)
                    } catch (e: Exception) {
                        try {
                            // 尝试将字符串解析为JSON对象
                            val jsonStr = jsonObject.get(key)?.asString
                            if (jsonStr != null && jsonStr.isNotEmpty()) {
                                gson.fromJson(jsonStr, com.google.gson.JsonObject::class.java)
                            } else {
                                null
                            }
                        } catch (e2: Exception) {
                            Log.e(TAG, "键 $key 的值既不是JSON对象也不是有效的JSON字符串: ${e2.message}")
                            null
                        }
                    }
                    
                    if (eqObject == null) {
                        Log.w(TAG, "键 $key 对应的值不是JSON对象")
                        continue
                    }
                    
                    // 获取地震ID，使用EventID作为唯一标识符
                    val id = if (eqObject.has("EventID")) {
                        eqObject.get("EventID").asString
                    } else {
                        "cenc-${System.currentTimeMillis()}-$key"
                    }
                    
                    // 获取震级，可能是字符串格式
                    val magnitude = if (eqObject.has("magnitude")) {
                        try {
                            eqObject.get("magnitude").asString.toDoubleOrNull() ?: 4.0
                        } catch (e: Exception) {
                            try {
                                eqObject.get("magnitude").asDouble
                            } catch (e2: Exception) {
                                Log.e(TAG, "解析震级失败: ${e2.message}")
                                4.0 // 提供一个合理的默认值
                            }
                        }
                    } else {
                        4.0 // 默认值
                    }
                    
                    // 获取位置
                    val location = if (eqObject.has("location")) {
                        try {
                            eqObject.get("location").asString
                        } catch (e: Exception) {
                            Log.e(TAG, "解析位置失败: ${e.message}")
                            "未知位置"
                        }
                    } else {
                        "未知位置"
                    }
                    
                    // 如果位置为空或未知，使用编号作为替代
                    val finalLocation = if (location == "未知位置" || location.isBlank()) {
                        "地震 $key"
                    } else {
                        location
                    }
                    
                    // 获取深度，可能是字符串格式
                    val depth = if (eqObject.has("depth")) {
                        try {
                            eqObject.get("depth").asString.toDoubleOrNull() ?: 10.0
                        } catch (e: Exception) {
                            try {
                                eqObject.get("depth").asDouble
                            } catch (e2: Exception) {
                                Log.e(TAG, "解析深度失败: ${e2.message}")
                                10.0
                            }
                        }
                    } else {
                        10.0 // 默认值
                    }
                    
                    // 获取经纬度，可能是字符串格式
                    val latitude = if (eqObject.has("latitude")) {
                        try {
                            val latStr = eqObject.get("latitude").asString
                            latStr.toDoubleOrNull() ?: 30.0 // 默认成都附近
                        } catch (e: Exception) {
                            try {
                                eqObject.get("latitude").asDouble
                            } catch (e2: Exception) {
                                Log.e(TAG, "解析纬度失败: ${e2.message}")
                                30.0 // 默认成都附近
                            }
                        }
                    } else {
                        30.0 // 默认值
                    }
                    
                    val longitude = if (eqObject.has("longitude")) {
                        try {
                            val lonStr = eqObject.get("longitude").asString
                            lonStr.toDoubleOrNull() ?: 104.0 // 默认成都附近
                        } catch (e: Exception) {
                            try {
                                eqObject.get("longitude").asDouble
                            } catch (e2: Exception) {
                                Log.e(TAG, "解析经度失败: ${e2.message}")
                                104.0 // 默认成都附近
                            }
                        }
                    } else {
                        104.0 // 默认值
                    }
                    
                    // 获取烈度，可能是字符串格式
                    val intensity = if (eqObject.has("intensity")) {
                        try {
                            eqObject.get("intensity").asString.toIntOrNull() ?: 0
                        } catch (e: Exception) {
                            try {
                                eqObject.get("intensity").asInt
                            } catch (e2: Exception) {
                                Log.e(TAG, "解析烈度失败: ${e2.message}")
                                0
                            }
                        }
                    } else {
                        0 // 默认值
                    }
                    
                    // 获取时间
                    val timeStr = if (eqObject.has("time")) {
                        try {
                            eqObject.get("time").asString
                        } catch (e: Exception) {
                            Log.e(TAG, "解析时间字符串失败: ${e.message}")
                            ""
                        }
                    } else {
                        "" // 默认空字符串
                    }
                    
                    // 解析时间
                    var time = Date()
                    if (timeStr.isNotEmpty()) {
                        try {
                            // 中国地震台网时间格式：UTC+8
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                            dateFormat.timeZone = TimeZone.getTimeZone("GMT+8")
                            time = dateFormat.parse(timeStr) ?: Date()
                            Log.d(TAG, "成功解析时间: $timeStr -> $time")
                        } catch (e: Exception) {
                            Log.e(TAG, "解析中国地震台网时间失败: $timeStr", e)
                            // 继续使用当前时间作为默认值
                        }
                    }
                    
                    // 获取类型
                    val type = if (eqObject.has("type")) {
                        try {
                            eqObject.get("type").asString
                        } catch (e: Exception) {
                            "unknown"
                        }
                    } else {
                        "unknown"
                    }
                    
                    // 判断是否是国内地震
                    val isDomesticEarthquake = isLocationInChina(finalLocation)
                    
                    // 构建地震对象
                    val earthquake = Earthquake(
                        id = id,
                        magnitude = magnitude,
                        location = com.example.eewapp.data.EarthquakeLocation(
                            latitude = latitude,
                            longitude = longitude,
                            place = finalLocation
                        ),
                        depth = depth,
                        time = time,
                        title = "M $magnitude - $finalLocation ${if (isDomesticEarthquake) "[中国]" else "[世界]"}",
                        url = "",
                        tsunamiWarning = magnitude >= 6.5 // 可能引发海啸的震级
                    )
                    
                    earthquakes.add(earthquake)
                    parsedCount++
                    Log.d(TAG, "解析地震记录成功: ${earthquake.title}, 时间: ${timeStr}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "解析单条中国地震台网数据出错: ${e.message}", e)
                    errorCount++
                    // 继续处理下一条
                    continue
                }
            }
            
            Log.d(TAG, "中国地震台网数据解析完成: 成功 $parsedCount 条, 错误 $errorCount 条")
            
            if (earthquakes.isEmpty()) {
                Log.w(TAG, "未能从中国地震台网数据中解析出任何地震记录")
            } else {
                Log.d(TAG, "成功从中国地震台网数据中解析出 ${earthquakes.size} 条地震记录")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "解析中国地震台网JSON数据失败: ${e.message}", e)
            return emptyList()
        }
        
        return earthquakes
    }
    
    /**
     * 修复可能不完整的JSON字符串
     */
    private fun fixJsonString(jsonString: String): String {
        try {
            // 去除首尾空白
            var fixedJson = jsonString.trim()
            
            // 确保以 { 开头
            if (!fixedJson.startsWith("{")) {
                fixedJson = "{" + fixedJson
            }
            
            // 确保以 } 结尾
            if (!fixedJson.endsWith("}")) {
                fixedJson = fixedJson + "}"
            }
            
            // 处理可能被截断的JSON
            // 检查最后一个逗号后面是否缺少内容
            val lastCommaIndex = fixedJson.lastIndexOf(",")
            val lastBraceIndex = fixedJson.lastIndexOf("}")
            
            if (lastCommaIndex > lastBraceIndex - 10 && lastBraceIndex > 0) {
                // 最后一个逗号在最后一个大括号附近，可能是不完整的JSON
                fixedJson = fixedJson.substring(0, lastCommaIndex) + fixedJson.substring(lastBraceIndex)
            }
            
            return fixedJson
        } catch (e: Exception) {
            Log.e(TAG, "修复JSON字符串失败: ${e.message}")
            return jsonString
        }
    }
} 