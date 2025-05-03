package com.example.eewapp.location

import com.example.eewapp.data.UserLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * 定位服务接口
 */
interface LocationService {
    /**
     * 用户位置流
     */
    val userLocation: StateFlow<UserLocation?>
    
    /**
     * 开始定位更新
     */
    fun startLocationUpdates()
    
    /**
     * 停止定位更新
     */
    fun stopLocationUpdates()
} 