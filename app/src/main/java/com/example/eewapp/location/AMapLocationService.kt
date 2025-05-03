package com.example.eewapp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.eewapp.data.UserLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.lang.reflect.Proxy

/**
 * 高德地图定位服务
 */
class AMapLocationService(private val context: Context) : LocationService {
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    override val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()
    
    // 使用Any类型，通过反射操作
    private var locationClient: Any? = null
    
    init {
        try {
            // 使用反射初始化高德地图定位客户端
            val amapLocationClientClass = Class.forName("com.amap.api.location.AMapLocationClient")
            val constructor = amapLocationClientClass.getConstructor(Context::class.java)
            locationClient = constructor.newInstance(context)
            
            Log.d("AMapLocationService", "高德地图定位客户端初始化成功")
        } catch (e: Exception) {
            Log.e("AMapLocationService", "高德地图定位客户端初始化失败", e)
        }
    }
    
    /**
     * 检查是否有定位权限
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ActivityCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取最后一次位置
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): UserLocation? {
        if (!hasLocationPermission()) {
            return null
        }
        
        return try {
            val client = locationClient ?: return null
            
            // 使用反射设置定位参数
            val amapLocationClientOptionClass = Class.forName("com.amap.api.location.AMapLocationClientOption")
            val option = amapLocationClientOptionClass.newInstance()
            
            // 设置定位模式为高精度模式
            val locationModeClass = Class.forName("com.amap.api.location.AMapLocationClientOption\$AMapLocationMode")
            val highAccuracyMode = locationModeClass.getField("Hight_Accuracy").get(null)
            val setLocationModeMethod = amapLocationClientOptionClass.getMethod("setLocationMode", locationModeClass)
            setLocationModeMethod.invoke(option, highAccuracyMode)
            
            // 设置是否单次定位
            val setOnceLocationMethod = amapLocationClientOptionClass.getMethod("setOnceLocation", Boolean::class.java)
            setOnceLocationMethod.invoke(option, true)
            
            // 设置是否需要地址信息
            val setNeedAddressMethod = amapLocationClientOptionClass.getMethod("setNeedAddress", Boolean::class.java)
            setNeedAddressMethod.invoke(option, true)
            
            // 设置定位参数
            val setLocationOptionMethod = client.javaClass.getMethod("setLocationOption", amapLocationClientOptionClass)
            setLocationOptionMethod.invoke(client, option)
            
            // 使用协程获取位置
            var result: UserLocation? = null
            
            callbackFlow {
                // 创建定位监听器
                val amapLocationListenerClass = Class.forName("com.amap.api.location.AMapLocationListener")
                val listenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                    amapLocationListenerClass.classLoader,
                    arrayOf(amapLocationListenerClass)
                ) { _, _, args ->
                    val location = args[0]
                    if (location != null) {
                        val errorCodeMethod = location.javaClass.getMethod("getErrorCode")
                        val errorCode = errorCodeMethod.invoke(location) as Int
                        
                        if (errorCode == 0) {
                            val latitudeMethod = location.javaClass.getMethod("getLatitude")
                            val longitudeMethod = location.javaClass.getMethod("getLongitude")
                            val accuracyMethod = location.javaClass.getMethod("getAccuracy")
                            
                            val latitude = latitudeMethod.invoke(location) as Double
                            val longitude = longitudeMethod.invoke(location) as Double
                            val accuracy = accuracyMethod.invoke(location) as Float
                            
                            val userLocation = UserLocation(
                                latitude = latitude,
                                longitude = longitude,
                                accuracy = accuracy
                            )
                            _userLocation.value = userLocation
                            trySend(userLocation)
                            close()
                        } else {
                            val errorInfoMethod = location.javaClass.getMethod("getErrorInfo")
                            val errorInfo = errorInfoMethod.invoke(location) as String
                            close(Exception("定位失败: $errorInfo"))
                        }
                    } else {
                        close(Exception("定位失败: 位置为空"))
                    }
                    null
                }
                
                // 设置定位监听器
                val setLocationListenerMethod = client.javaClass.getMethod("setLocationListener", amapLocationListenerClass)
                setLocationListenerMethod.invoke(client, listenerProxy)
                
                // 开始定位
                val startLocationMethod = client.javaClass.getMethod("startLocation")
                startLocationMethod.invoke(client)
                
                awaitClose {
                    // 停止定位
                    val stopLocationMethod = client.javaClass.getMethod("stopLocation")
                    stopLocationMethod.invoke(client)
                    
                    // 取消定位监听器
                    val unRegisterLocationListenerMethod = client.javaClass.getMethod("unRegisterLocationListener", amapLocationListenerClass)
                    unRegisterLocationListenerMethod.invoke(client, listenerProxy)
                }
            }.collect { location ->
                result = location
            }
            
            result
        } catch (e: Exception) {
            Log.e("AMapLocationService", "获取位置失败", e)
            null
        }
    }
    
    /**
     * 请求位置更新
     */
    @SuppressLint("MissingPermission")
    fun requestLocationUpdates(): Flow<UserLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }
        
        val client = locationClient ?: run {
            close(Exception("定位客户端未初始化"))
            return@callbackFlow
        }
        
        try {
            // 使用反射设置定位参数
            val amapLocationClientOptionClass = Class.forName("com.amap.api.location.AMapLocationClientOption")
            val option = amapLocationClientOptionClass.newInstance()
            
            // 设置定位模式为高精度模式
            val locationModeClass = Class.forName("com.amap.api.location.AMapLocationClientOption\$AMapLocationMode")
            val highAccuracyMode = locationModeClass.getField("Hight_Accuracy").get(null)
            val setLocationModeMethod = amapLocationClientOptionClass.getMethod("setLocationMode", locationModeClass)
            setLocationModeMethod.invoke(option, highAccuracyMode)
            
            // 设置定位间隔
            val setIntervalMethod = amapLocationClientOptionClass.getMethod("setInterval", Long::class.java)
            setIntervalMethod.invoke(option, 5000L) // 5秒更新一次
            
            // 设置是否需要地址信息
            val setNeedAddressMethod = amapLocationClientOptionClass.getMethod("setNeedAddress", Boolean::class.java)
            setNeedAddressMethod.invoke(option, true)
            
            // 设置定位参数
            val setLocationOptionMethod = client.javaClass.getMethod("setLocationOption", amapLocationClientOptionClass)
            setLocationOptionMethod.invoke(client, option)
            
            // 创建定位监听器
            val amapLocationListenerClass = Class.forName("com.amap.api.location.AMapLocationListener")
            val listenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                amapLocationListenerClass.classLoader,
                arrayOf(amapLocationListenerClass)
            ) { _, _, args ->
                val location = args[0]
                if (location != null) {
                    val errorCodeMethod = location.javaClass.getMethod("getErrorCode")
                    val errorCode = errorCodeMethod.invoke(location) as Int
                    
                    if (errorCode == 0) {
                        val latitudeMethod = location.javaClass.getMethod("getLatitude")
                        val longitudeMethod = location.javaClass.getMethod("getLongitude")
                        val accuracyMethod = location.javaClass.getMethod("getAccuracy")
                        
                        val latitude = latitudeMethod.invoke(location) as Double
                        val longitude = longitudeMethod.invoke(location) as Double
                        val accuracy = accuracyMethod.invoke(location) as Float
                        
                        val userLocation = UserLocation(
                            latitude = latitude,
                            longitude = longitude,
                            accuracy = accuracy
                        )
                        _userLocation.value = userLocation
                        trySend(userLocation)
                    } else {
                        val errorInfoMethod = location.javaClass.getMethod("getErrorInfo")
                        val errorInfo = errorInfoMethod.invoke(location) as String
                        Log.e("AMapLocationService", "定位失败: $errorInfo")
                    }
                }
                null
            }
            
            // 设置定位监听器
            val setLocationListenerMethod = client.javaClass.getMethod("setLocationListener", amapLocationListenerClass)
            setLocationListenerMethod.invoke(client, listenerProxy)
            
            // 开始定位
            val startLocationMethod = client.javaClass.getMethod("startLocation")
            startLocationMethod.invoke(client)
            
            awaitClose {
                // 停止定位
                val stopLocationMethod = client.javaClass.getMethod("stopLocation")
                stopLocationMethod.invoke(client)
                
                // 取消定位监听器
                val unRegisterLocationListenerMethod = client.javaClass.getMethod("unRegisterLocationListener", amapLocationListenerClass)
                unRegisterLocationListenerMethod.invoke(client, listenerProxy)
            }
        } catch (e: Exception) {
            Log.e("AMapLocationService", "请求位置更新失败", e)
            close(e)
        }
    }
    
    /**
     * 停止定位
     */
    override fun stopLocationUpdates() {
        try {
            locationClient?.let { client ->
                val stopLocationMethod = client.javaClass.getMethod("stopLocation")
                stopLocationMethod.invoke(client)
            }
        } catch (e: Exception) {
            Log.e("AMapLocationService", "停止定位失败", e)
        }
    }
    
    /**
     * 销毁定位客户端
     */
    fun destroy() {
        try {
            locationClient?.let { client ->
                val onDestroyMethod = client.javaClass.getMethod("onDestroy")
                onDestroyMethod.invoke(client)
            }
            locationClient = null
        } catch (e: Exception) {
            Log.e("AMapLocationService", "销毁定位客户端失败", e)
        }
    }

    override fun startLocationUpdates() {
        try {
            // 使用反射获取AMapLocationClient类
            val locationClientClass = Class.forName("com.amap.api.location.AMapLocationClient")
            val locationClientConstructor = locationClientClass.getConstructor(Context::class.java)
            locationClient = locationClientConstructor.newInstance(context)
            
            // 获取AMapLocationClientOption类
            val locationClientOptionClass = Class.forName("com.amap.api.location.AMapLocationClientOption")
            val locationClientOption = locationClientOptionClass.newInstance()
            
            // 设置定位模式为高精度
            val setLocationModeMethod = locationClientOptionClass.getMethod("setLocationMode", Class.forName("com.amap.api.location.AMapLocationClientOption\$AMapLocationMode"))
            val locationModeClass = Class.forName("com.amap.api.location.AMapLocationClientOption\$AMapLocationMode")
            val highAccuracyMode = locationModeClass.getField("Hight_Accuracy").get(null)
            setLocationModeMethod.invoke(locationClientOption, highAccuracyMode)
            
            // 设置是否返回地址信息
            val setNeedAddressMethod = locationClientOptionClass.getMethod("setNeedAddress", Boolean::class.java)
            setNeedAddressMethod.invoke(locationClientOption, true)
            
            // 设置定位选项
            val setLocationOptionMethod = locationClientClass.getMethod("setLocationOption", locationClientOptionClass)
            setLocationOptionMethod.invoke(locationClient, locationClientOption)
            
            // 创建定位监听器代理
            val locationListenerClass = Class.forName("com.amap.api.location.AMapLocationListener")
            val locationListener = Proxy.newProxyInstance(
                locationListenerClass.classLoader,
                arrayOf(locationListenerClass)
            ) { _, method, args ->
                if (method.name == "onLocationChanged" && args.isNotEmpty()) {
                    val amapLocation = args[0]
                    handleLocationResult(amapLocation)
                }
                null
            }
            
            // 设置定位监听器
            val setLocationListenerMethod = locationClientClass.getMethod("setLocationListener", locationListenerClass)
            setLocationListenerMethod.invoke(locationClient, locationListener)
            
            // 开始定位
            val startLocationMethod = locationClientClass.getMethod("startLocation")
            startLocationMethod.invoke(locationClient)
            
            Log.d(TAG, "开始定位")
        } catch (e: Exception) {
            Log.e(TAG, "启动定位失败", e)
        }
    }
    
    /**
     * 处理定位结果
     */
    private fun handleLocationResult(amapLocation: Any?) {
        try {
            if (amapLocation == null) {
                Log.e(TAG, "定位结果为空")
                return
            }
            
            // 使用反射获取定位结果
            val errorCodeMethod = amapLocation.javaClass.getMethod("getErrorCode")
            val errorCode = errorCodeMethod.invoke(amapLocation) as Int
            
            if (errorCode == 0) {
                // 定位成功
                val getLatitudeMethod = amapLocation.javaClass.getMethod("getLatitude")
                val getLongitudeMethod = amapLocation.javaClass.getMethod("getLongitude")
                val getAddressMethod = amapLocation.javaClass.getMethod("getAddress")
                
                val latitude = getLatitudeMethod.invoke(amapLocation) as Double
                val longitude = getLongitudeMethod.invoke(amapLocation) as Double
                val address = getAddressMethod.invoke(amapLocation) as String?
                
                val location = UserLocation(
                    latitude = latitude,
                    longitude = longitude,
                    address = address ?: "未知地址"
                )
                
                _userLocation.value = location
                Log.d(TAG, "定位成功: $location")
            } else {
                // 定位失败
                val getErrorInfoMethod = amapLocation.javaClass.getMethod("getErrorInfo")
                val errorInfo = getErrorInfoMethod.invoke(amapLocation) as String
                Log.e(TAG, "定位失败: $errorCode, $errorInfo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理定位结果失败", e)
        }
    }
    
    companion object {
        private const val TAG = "AMapLocationService"
    }
} 