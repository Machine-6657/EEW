package com.example.eewapp.utils

import android.content.Context
import android.util.Log

/**
 * 高德地图辅助类，使用反射来初始化高德地图
 */
object AMapHelper {
    private const val TAG = "AMapHelper"
    
    /**
     * 初始化高德地图
     */
    fun init(context: Context) {
        try {
            // 使用反射获取MapsInitializer类
            val mapsInitializerClass = Class.forName("com.amap.api.maps.MapsInitializer")
            
            // 设置隐私协议
            val updatePrivacyShowMethod = mapsInitializerClass.getMethod("updatePrivacyShow", Context::class.java, Boolean::class.java, Boolean::class.java)
            val updatePrivacyAgreeMethod = mapsInitializerClass.getMethod("updatePrivacyAgree", Context::class.java, Boolean::class.java)
            
            // 调用方法
            updatePrivacyShowMethod.invoke(null, context, true, true)
            updatePrivacyAgreeMethod.invoke(null, context, true)
            
            Log.d(TAG, "高德地图初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "高德地图初始化失败", e)
        }
    }
} 