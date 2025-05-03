package com.example.eewapp.network

import android.util.Log
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeLocation
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * WebSocket客户端，用于连接四川地震局的地震预警API
 */
class SichuanEarthquakeWebSocketClient {
    private val TAG = "SCEarthquakeWSClient"
    
    // WebSocket连接地址
    private val WS_URL = "wss://ws-api.wolfx.jp/sc_eew"
    
    // OkHttp客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    // WebSocket实例
    private var webSocket: WebSocket? = null
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // 最新接收到的地震列表
    private val _earthquakes = MutableStateFlow<List<Earthquake>>(emptyList())
    val earthquakes: StateFlow<List<Earthquake>> = _earthquakes.asStateFlow()
    
    // 错误频道
    private val _errorChannel = Channel<String>(Channel.BUFFERED)
    val errorEvents: Flow<String> = _errorChannel.receiveAsFlow()
    
    // 连接ID
    private var connectionId: String? = null
    
    // Gson解析器
    private val gson = Gson()
    
    /**
     * 建立WebSocket连接
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || 
            _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "正在连接到四川地震局WebSocket API...")
        
        val request = Request.Builder()
            .url(WS_URL)
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket连接已建立")
                _connectionState.value = ConnectionState.CONNECTED
                
                // 发送初始查询指令，获取最新地震数据
                scope.launch {
                    sendQueryCommand()
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息: $text")
                handleMessage(text)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket连接已关闭: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                connectionId = null
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket连接失败: ${t.message}", t)
                _connectionState.value = ConnectionState.DISCONNECTED
                connectionId = null
                
                scope.launch {
                    _errorChannel.send("WebSocket连接失败: ${t.message}")
                    
                    // 尝试重新连接
                    reconnect()
                }
            }
        })
    }
    
    /**
     * 关闭WebSocket连接
     */
    fun disconnect() {
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        connectionId = null
    }
    
    /**
     * 重新连接
     */
    private fun reconnect() {
        disconnect()
        Log.d(TAG, "5秒后尝试重新连接...")
        
        scope.launch {
            kotlinx.coroutines.delay(5000)
            connect()
        }
    }
    
    /**
     * 发送查询指令
     */
    fun sendQueryCommand() {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "WebSocket未连接，无法发送查询指令")
            return
        }
        
        // 发送四川地震局地震预警查询指令
        webSocket?.send("query_sceew")
        Log.d(TAG, "已发送查询指令: query_sceew")
    }
    
    /**
     * 发送心跳包响应
     */
    private fun sendPongResponse() {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            return
        }
        
        // 构建Pong响应
        val pongJson = "{\"type\":\"pong\",\"timestamp\":${System.currentTimeMillis()}}"
        webSocket?.send(pongJson)
        Log.d(TAG, "已发送Pong响应")
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(message: String) {
        try {
            Log.d(TAG, "开始解析消息: $message")
            
            // 使用新版 Gson 的 JsonParser 方法解析 JSON
            val jsonElement = gson.fromJson(message, com.google.gson.JsonElement::class.java)
            if (!jsonElement.isJsonObject) {
                Log.w(TAG, "接收到非JSON对象消息: $message")
                return
            }
            
            val jsonObject = jsonElement.asJsonObject
            Log.d(TAG, "成功解析为JSON: $jsonObject")
            
            // 获取消息类型，考虑不同的字段名可能性
            val type = jsonObject.get("type")?.asString 
                ?: jsonObject.get("msgType")?.asString
                ?: jsonObject.get("message_type")?.asString
            
            Log.d(TAG, "消息类型: $type")
            
            when (type) {
                "heartbeat" -> {
                    // 处理心跳包
                    connectionId = jsonObject.get("id")?.asString
                    Log.d(TAG, "收到心跳包，连接ID: $connectionId")
                    sendPongResponse()
                }
                "sc_eew", "eew", "earthquake" -> {
                    // 处理四川地震局地震预警，尝试多种可能的类型名称
                    Log.d(TAG, "收到地震预警数据")
                    processEarthquakeData(jsonObject)
                }
                "pong" -> {
                    // 服务器响应Ping，不需要处理
                    Log.d(TAG, "收到Pong响应")
                }
                null -> {
                    // 如果没有类型字段，可能是地震数据格式不同，尝试直接解析
                    Log.d(TAG, "消息没有type字段，尝试直接解析为地震数据")
                    if (jsonObject.has("ID") || jsonObject.has("EventID") || 
                        jsonObject.has("Magnitude") || jsonObject.has("HypoCenter")) {
                        processEarthquakeData(jsonObject)
                    } else {
                        Log.d(TAG, "无法识别的消息格式: $jsonObject")
                    }
                }
                else -> {
                    Log.d(TAG, "收到未知类型消息: $type，尝试解析消息内容")
                    // 尝试查找消息中是否有地震相关字段
                    if (jsonObject.has("ID") || jsonObject.has("EventID") || 
                        jsonObject.has("Magnitude") || jsonObject.has("HypoCenter")) {
                        processEarthquakeData(jsonObject)
                    } else {
                        Log.d(TAG, "未知消息内容: $jsonObject")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理消息时出错: ${e.message}", e)
            e.printStackTrace() // 打印完整堆栈跟踪
        }
    }
    
    /**
     * 处理地震数据
     */
    private fun processEarthquakeData(jsonObject: JsonObject) {
        try {
            Log.d(TAG, "开始处理地震数据: $jsonObject")
            
            // 检查是否有有效的地震数据字段
            if (!jsonObject.has("ID") && !jsonObject.has("EventID") && !jsonObject.has("Magnitude")) {
                Log.w(TAG, "JSON数据缺少必要的地震字段: $jsonObject")
                return
            }
            
            val earthquake = parseEarthquakeFromJson(jsonObject)
            Log.d(TAG, "解析地震数据成功: ${earthquake.title}")
            
            // 更新地震列表（添加到列表开头）
            val currentList = _earthquakes.value.toMutableList()
            // 检查是否已存在相同ID的地震，如果存在则更新
            val existingIndex = currentList.indexOfFirst { it.id == earthquake.id }
            if (existingIndex >= 0) {
                Log.d(TAG, "更新已存在的地震: ${earthquake.id}")
                currentList[existingIndex] = earthquake
            } else {
                Log.d(TAG, "添加新地震: ${earthquake.id}")
                currentList.add(0, earthquake) // 添加到列表开头
            }
            
            // 更新状态流
            _earthquakes.value = currentList
            Log.d(TAG, "地震列表更新完成，当前有 ${currentList.size} 条数据")
            
        } catch (e: Exception) {
            Log.e(TAG, "解析地震数据时出错: ${e.message}", e)
            e.printStackTrace() // 打印完整堆栈跟踪
        }
    }
    
    /**
     * 从JSON解析地震数据
     * 根据四川地震局API的JSON字段解析
     */
    private fun parseEarthquakeFromJson(jsonObject: JsonObject): Earthquake {
        try {
            // 记录原始JSON，方便调试
            Log.d(TAG, "解析地震JSON: $jsonObject")
            
            // 获取主要字段 - 按照四川地震局API的字段名称
            val id = jsonObject.get("ID")?.asString ?: jsonObject.get("EventID")?.asString ?: UUID.randomUUID().toString()
            Log.d(TAG, "地震ID: $id")
            
            val eventId = jsonObject.get("EventID")?.asString ?: ""
            val reportTime = jsonObject.get("ReportTime")?.asString ?: ""
            val reportNum = jsonObject.get("ReportNum")?.asString ?: ""
            val originTime = jsonObject.get("OriginTime")?.asString ?: ""
            val hypoCenter = jsonObject.get("HypoCenter")?.asString ?: "未知位置"
            
            Log.d(TAG, "震源地: $hypoCenter")
            Log.d(TAG, "发生时间: $originTime")
            
            val latitude = jsonObject.get("Latitude")?.asDouble ?: 0.0
            val longitude = jsonObject.get("Longitude")?.asDouble ?: 0.0
            val magnitude = jsonObject.get("Magnitude")?.asDouble ?: 0.0
            val depth = jsonObject.get("Depth")?.asDouble ?: 0.0
            val maxIntensity = jsonObject.get("MaxIntensity")?.asString ?: ""
            
            Log.d(TAG, "地震参数 - 纬度: $latitude, 经度: $longitude, 震级: $magnitude, 深度: $depth km")
            
            // 解析时间 - 由于API返回的是字符串格式的时间，需要转换为Date
            // 尝试多种可能的日期格式
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
                        time = format.parse(originTime) ?: Date()
                        parsed = true
                        Log.d(TAG, "成功解析时间: $originTime -> $time, 使用格式: ${format.toPattern()}")
                        break
                    } catch (e: Exception) {
                        // 继续尝试下一种格式
                    }
                }
                
                if (!parsed) {
                    Log.e(TAG, "所有时间格式都无法解析: $originTime")
                }
            }
            
            // 构建标题，添加中文标记以区分来源
            val title = "M $magnitude - $hypoCenter [四川地震局]"
            Log.d(TAG, "生成标题: $title")
            
            return Earthquake(
                id = id,
                magnitude = magnitude,
                location = EarthquakeLocation(
                    latitude = latitude,
                    longitude = longitude,
                    place = hypoCenter
                ),
                depth = depth,
                time = time,
                title = title,
                url = "",
                tsunamiWarning = false // API没有提供海啸警告信息，默认为false
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析地震数据发生错误: ${e.message}", e)
            e.printStackTrace() // 打印完整堆栈跟踪
            throw e
        }
    }
    
    /**
     * 连接状态枚举
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
} 