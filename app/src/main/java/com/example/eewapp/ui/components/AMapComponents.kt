package com.example.eewapp.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * 通过反射调用的高德地图折线组件
 * 支持多点路径绘制
 */
@Composable
fun AMapPolyline(
    aMap: Any?,
    points: List<Any>,
    color: Int,
    width: Float,
    isDashed: Boolean = false
) {
    val polylineRef = remember { mutableListOf<Any?>() }
    
    DisposableEffect(aMap, points, color, width, isDashed) {
        if (aMap == null || points.isEmpty()) {
            Log.w("AMapPolyline", "AMap实例为空或路径点为空，无法添加折线")
            return@DisposableEffect onDispose { }
        }

        var polyline: Any? = null
        
        try {
            // 获取PolylineOptions类
            val polylineOptionsClass = Class.forName("com.amap.api.maps.model.PolylineOptions")
            val polylineOptions = polylineOptionsClass.newInstance()
            
            // 添加路径点
            val addMethod = polylineOptionsClass.getMethod("add", Class.forName("com.amap.api.maps.model.LatLng"))
            points.forEach { point ->
                addMethod.invoke(polylineOptions, point)
            }
            
            // 设置颜色
            val colorMethod = polylineOptionsClass.getMethod("color", Int::class.java)
            colorMethod.invoke(polylineOptions, color)
            
            // 设置线宽
            val widthMethod = polylineOptionsClass.getMethod("width", Float::class.java)
            widthMethod.invoke(polylineOptions, width)
            
            // 设置是否虚线
            if (isDashed) {
                try {
                    val setDottedLineMethod = polylineOptionsClass.getMethod("setDottedLine", Boolean::class.java)
                    setDottedLineMethod.invoke(polylineOptions, true)
                    Log.d("AMapPolyline", "设置虚线模式")
                } catch (e: Exception) {
                    Log.w("AMapPolyline", "设置虚线模式失败，可能不支持该方法", e)
                    // 尝试其他虚线设置方法
                    try {
                        val dashArrayMethod = polylineOptionsClass.getMethod("setDashed", Boolean::class.java)
                        dashArrayMethod.invoke(polylineOptions, true)
                        Log.d("AMapPolyline", "使用setDashed设置虚线模式")
                    } catch (e2: Exception) {
                        Log.w("AMapPolyline", "设置虚线失败，使用实线", e2)
                    }
                }
            }
            
            // 添加折线到地图
            val addPolylineMethod = aMap.javaClass.getMethod("addPolyline", polylineOptionsClass)
            polyline = addPolylineMethod.invoke(aMap, polylineOptions)
            polylineRef.add(polyline)
            
            Log.d("AMapPolyline", "成功添加折线，路径点数：${points.size}")
            
        } catch (e: Exception) {
            Log.e("AMapPolyline", "添加折线失败", e)
        }

        onDispose {
            try {
                polylineRef.forEach { polyline ->
                    if (polyline != null) {
                        val removeMethod = polyline.javaClass.getMethod("remove")
                        removeMethod.invoke(polyline)
                    }
                }
                polylineRef.clear()
                Log.d("AMapPolyline", "折线已移除")
            } catch (e: Exception) {
                Log.e("AMapPolyline", "移除折线失败", e)
            }
        }
    }
}

/**
 * 通过反射创建LatLng对象
 */
fun createLatLng(latitude: Double, longitude: Double): Any {
    return try {
        val latLngClass = Class.forName("com.amap.api.maps.model.LatLng")
        latLngClass.getConstructor(Double::class.java, Double::class.java)
            .newInstance(latitude, longitude)
    } catch (e: Exception) {
        Log.e("AMapComponents", "创建LatLng对象失败", e)
        throw e
    }
} 