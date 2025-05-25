package com.example.eewapp.utils

import android.content.Context
import android.util.Log

/**
 * 高德地图辅助类，使用反射来初始化高德地图和相关服务
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
            
            // 初始化搜索服务
            initSearchService(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "高德地图初始化失败", e)
        }
    }
    
    /**
     * 初始化搜索服务
     */
    private fun initSearchService(context: Context) {
        try {
            // 检查搜索服务是否可用
            val serviceModuleClass = Class.forName("com.amap.api.services.core.ServiceSettings")
            val updatePrivacyShowMethod = serviceModuleClass.getMethod("updatePrivacyShow", Context::class.java, Boolean::class.java, Boolean::class.java)
            val updatePrivacyAgreeMethod = serviceModuleClass.getMethod("updatePrivacyAgree", Context::class.java, Boolean::class.java)
            
            updatePrivacyShowMethod.invoke(null, context, true, true)
            updatePrivacyAgreeMethod.invoke(null, context, true)
            
            Log.d(TAG, "搜索服务初始化成功")
            
        } catch (e: Exception) {
            Log.w(TAG, "搜索服务初始化失败，部分功能可能不可用", e)
        }
    }
    
    /**
     * 检查高德地图服务是否可用
     */
    fun isMapServiceAvailable(): Boolean {
        return try {
            Class.forName("com.amap.api.maps.AMap")
            true
        } catch (e: Exception) {
            Log.w(TAG, "高德地图服务不可用", e)
            false
        }
    }
    
    /**
     * 检查搜索服务是否可用
     */
    fun isSearchServiceAvailable(): Boolean {
        return try {
            Class.forName("com.amap.api.services.poisearch.PoiSearch")
            Class.forName("com.amap.api.services.route.RouteSearch")
            true
        } catch (e: Exception) {
            Log.w(TAG, "搜索服务不可用", e)
            false
        }
    }
} 