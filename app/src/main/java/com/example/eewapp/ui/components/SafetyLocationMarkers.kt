package com.example.eewapp.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.eewapp.data.*
import com.example.eewapp.ui.components.createLatLng

/**
 * 在地图上添加安全地点标记（支持3D模式）
 */
@Composable
fun SafetyLocationMarkers(
    aMap: Any?,
    safetyLocations: List<SafetyLocation>,
    selectedDestination: SafetyLocation?,
    userLocation: UserLocation?,
    currentRoute: NavigationRoute?,
    is3DMode: Boolean = false // 新增3D模式参数
) {
    Log.d("SafetyMarkersDebug", "SafetyLocationMarkers Composable: currentRoute is null=${currentRoute == null}, aMap is null=${aMap == null}")

    // 添加安全地点标记
    LaunchedEffect(aMap, safetyLocations, selectedDestination, is3DMode) { // 添加 selectedDestination 以便在选择变化时也重绘标记
        if (aMap != null && safetyLocations.isNotEmpty()) {
            try {
                Log.d("SafetyMarkersDebug", "Safety Markers LaunchedEffect: Clearing existing markers before adding new ones.")
                // 清除之前的安全地点标记(如果需要更精细的控制，比如只更新变化的，则需要更复杂的逻辑)
                // 为了简单起见，这里可以先全部清除再添加，或者您有专门的 removeAllSafetyMarkers 函数
                // clearSafetyLocationMarkers(aMap) // 假设有这个函数，或者aMap.clear()如果只清除标记
                // aMap.javaClass.getMethod("clear").invoke(aMap) // 谨慎使用 aMap.clear() 除非你只想留导航线

                Log.d("SafetyMarkersDebug", "Safety Markers LaunchedEffect: Adding ${safetyLocations.size} safety location markers. 3DMode=$is3DMode")
                safetyLocations.forEach { location ->
                    addSafetyLocationMarker(aMap, location, location == selectedDestination, is3DMode)
                }
                Log.d("SafetyMarkersDebug", "Safety Markers LaunchedEffect: Finished adding markers.")
            } catch (e: Exception) {
                Log.e("SafetyMarkersDebug", "Error in Safety Markers LaunchedEffect", e)
            }
        }
    }

    // 添加或清除导航路线
    LaunchedEffect(aMap, currentRoute, userLocation, is3DMode) {
        Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect triggered. currentRoute is null=${currentRoute == null}, aMap is null=${aMap == null}, userLocation is null=${userLocation == null}")
        if (aMap == null) {
            Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: aMap is null, cannot proceed.")
            return@LaunchedEffect
        }

        if (currentRoute != null && userLocation != null) {
            Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: Condition MET (currentRoute is NOT null and userLocation is NOT null). Preparing to draw route.")
            try {
                Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: Calling clearNavigationRoute BEFORE adding new route.")
                clearNavigationRoute(aMap) // 清除旧路径
                Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: clearNavigationRoute finished.")

                Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: Calling addNavigationRoute.")
                addNavigationRoute(aMap, userLocation, currentRoute, is3DMode) // 添加新路径
                Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: addNavigationRoute finished for ${currentRoute.destination.name}.")
            } catch (e: Exception) {
                Log.e("SafetyMarkersDebug", "Error adding navigation route in LaunchedEffect", e)
            }
        } else if (currentRoute == null) {
            Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: currentRoute IS NULL. Explicitly calling clearNavigationRoute.")
            try {
                clearNavigationRoute(aMap) // 清除路径因为currentRoute是null
                Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: Explicit clearNavigationRoute finished due to null currentRoute.")
            } catch (e: Exception) {
                Log.e("SafetyMarkersDebug", "Error explicitly clearing navigation route in LaunchedEffect", e)
            }
        } else {
            // currentRoute != null 但是 userLocation == null 的情况，或者其他未预料的组合
            Log.d("SafetyMarkersDebug", "Navigation LaunchedEffect: Condition NOT MET for drawing (e.g. userLocation is null but currentRoute is not). currentRoute is null=${currentRoute == null}, userLocation is null=${userLocation == null}.")
        }
    }
}

/**
 * 添加安全地点标记（支持3D模式增强效果）
 */
private fun addSafetyLocationMarker(
    aMap: Any,
    location: SafetyLocation,
    isSelected: Boolean,
    is3DMode: Boolean = false
) {
    try {
        // 创建标记位置
        val latLng = createLatLng(location.latitude, location.longitude)
        
        // 根据类型选择颜色
        val markerColor = when (location.type) {
            SafetyLocationType.EMERGENCY_SHELTER -> android.graphics.Color.BLUE
            SafetyLocationType.SCHOOL_PLAYGROUND -> android.graphics.Color.GREEN
            SafetyLocationType.PUBLIC_SQUARE -> android.graphics.Color.MAGENTA
            SafetyLocationType.PARK -> android.graphics.Color.rgb(139, 195, 74)
            SafetyLocationType.SPORTS_GROUND -> android.graphics.Color.rgb(255, 152, 0)
            SafetyLocationType.OPEN_AREA -> android.graphics.Color.rgb(121, 85, 72)
            SafetyLocationType.HOSPITAL -> android.graphics.Color.RED
        }
        
        // 3D模式下使用更大的标记
        val markerSize = if (is3DMode) {
            if (isSelected) 20f else 16f // 3D模式下标记更大
        } else {
            if (isSelected) 16f else 12f // 2D模式标记大小
        }
        
        val outerRingSize = if (is3DMode) {
            if (isSelected) 36f else 28f // 3D模式下外圈更大
        } else {
            if (isSelected) 28f else 20f // 2D模式外圈大小
        }
        
        val outerRingAlpha = if (is3DMode) {
            if (isSelected) 150 else 100 // 3D模式下透明度更高
        } else {
            if (isSelected) 120 else 80 // 2D模式透明度
        }
        
        // 添加基础标记使用反射
        addMarkerToMap(aMap, latLng, markerColor, markerSize, outerRingSize, outerRingAlpha)
        
        // 在3D模式下，为选中的地点添加额外的垂直指示器效果
        if (is3DMode && isSelected) {
            try {
                // 添加一个稍高的透明标记作为3D效果
                addMarkerToMap(
                    aMap, latLng, android.graphics.Color.WHITE,
                    markerSize * 0.6f, outerRingSize * 0.8f, 60
                )
            } catch (e: Exception) {
                Log.w("SafetyLocationMarkers", "添加3D效果标记失败", e)
            }
        }
        
    } catch (e: Exception) {
        Log.e("SafetyLocationMarkers", "添加安全地点标记失败: ${location.name}", e)
    }
}

/**
 * 添加导航路线（支持3D模式增强效果）
 */
private fun addNavigationRoute(
    aMap: Any,
    userLocation: UserLocation,
    route: NavigationRoute,
    is3DMode: Boolean = false
) {
    try {
        Log.d("SafetyLocationMarkers", "开始添加导航路线")
        Log.d("SafetyLocationMarkers", "用户位置: ${userLocation.latitude}, ${userLocation.longitude}")
        Log.d("SafetyLocationMarkers", "目的地: ${route.destination.name} (${route.destination.latitude}, ${route.destination.longitude})")
        Log.d("SafetyLocationMarkers", "路径点数量: ${route.routePoints.size}")
        Log.d("SafetyLocationMarkers", "前5个路径点详情:")
        route.routePoints.take(5).forEachIndexed { index, point ->
            Log.d("SafetyLocationMarkers", "  点${index + 1}: ${point.latitude}, ${point.longitude} - ${point.instruction}")
        }
        if (route.routePoints.size > 5) {
            Log.d("SafetyLocationMarkers", "  ... 还有${route.routePoints.size - 5}个路径点")
        }
        Log.d("SafetyLocationMarkers", "3D模式: $is3DMode")
        
        val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
        val destinationLatLng = createLatLng(
            route.destination.latitude,
            route.destination.longitude
        )
        
        // 如果有详细路径点，使用真实路径；否则使用直线
        // 修改判断条件：只要路径点数量大于0就尝试使用详细路径
        val routePoints = if (route.routePoints.isNotEmpty()) {
            Log.d("SafetyLocationMarkers", "使用API返回的路径点（${route.routePoints.size}个）")
            // 使用API返回的路径点，无论数量多少
            route.routePoints.map { point ->
                Log.d("SafetyLocationMarkers", "路径点: ${point.latitude}, ${point.longitude}")
                createLatLng(point.latitude, point.longitude)
            }
        } else {
            Log.d("SafetyLocationMarkers", "没有路径点数据，使用简单直线连接")
            // 使用简单直线
            listOf(userLatLng, destinationLatLng)
        }
        
        Log.d("SafetyLocationMarkers", "实际绘制的路径点数量: ${routePoints.size}")
        
        // 3D模式下使用更明显的导航路线
        val lineWidth = if (is3DMode) 12f else 8f
        val lineColor = if (is3DMode) {
            android.graphics.Color.rgb(0, 150, 255) // 更亮的蓝色
        } else {
            android.graphics.Color.rgb(33, 150, 243) // 普通蓝色
        }
        
        Log.d("SafetyLocationMarkers", "线条宽度: $lineWidth, 颜色: $lineColor")
        
        // 绘制导航路线
        addPolylineToMap(
            aMap = aMap,
            points = routePoints,
            color = lineColor,
            width = lineWidth,
            isDashed = route.routePoints.isEmpty() // 只有没有路径点数据时才用虚线
        )
        
        // 在3D模式下添加额外的路线效果
        if (is3DMode && route.routePoints.isNotEmpty()) {
            try {
                Log.d("SafetyLocationMarkers", "添加3D模式中心线")
                // 添加一条稍细的白色路线作为中心线
                addPolylineToMap(
                    aMap = aMap,
                    points = routePoints,
                    color = android.graphics.Color.WHITE,
                    width = lineWidth * 0.4f,
                    isDashed = false
                )
            } catch (e: Exception) {
                Log.w("SafetyLocationMarkers", "添加3D路线中心线失败", e)
            }
        }
        
        // 在目标位置添加特殊标记
        val destMarkerSize = if (is3DMode) 22f else 18f
        val destRingSize = if (is3DMode) 40f else 32f
        val destRingAlpha = if (is3DMode) 180 else 150
        
        Log.d("SafetyLocationMarkers", "添加目标位置标记")
        addMarkerToMap(aMap, destinationLatLng, lineColor, destMarkerSize, destRingSize, destRingAlpha)
        
        Log.d("SafetyLocationMarkers", "添加${if (is3DMode) "3D" else "2D"}导航路线成功，路径点数：${routePoints.size}")
        
    } catch (e: Exception) {
        Log.e("SafetyLocationMarkers", "添加导航路线失败", e)
    }
}

/**
 * 清除安全地点标记
 */
private fun clearSafetyLocationMarkers(aMap: Any) {
    Log.d("SafetyMarkersDebug", "clearSafetyLocationMarkers: Attempting to clear all markers.")
    try {
        val clearMarkersMethod = aMap.javaClass.getMethod("clear")
        clearMarkersMethod.invoke(aMap)
        Log.d("SafetyMarkersDebug", "clearSafetyLocationMarkers: Successfully cleared all map markers using clear().")
    } catch (e: Exception) {
        try {
            val removeAllMarkersMethod = aMap.javaClass.getMethod("removeAllMarkers")
            removeAllMarkersMethod.invoke(aMap)
            Log.d("SafetyMarkersDebug", "clearSafetyLocationMarkers: Successfully cleared all map markers using removeAllMarkers().")
        } catch (e2: Exception) {
            Log.e("SafetyMarkersDebug", "clearSafetyLocationMarkers: Failed to clear markers.", e2)
        }
    }
}

/**
 * 清除导航路线
 */
private fun clearNavigationRoute(aMap: Any) {
    Log.d("SafetyMarkersDebug", "clearNavigationRoute: Attempting to remove all polylines.")
    try {
        val aMapClass = aMap.javaClass
        val removeAllPolylinesMethod = try {
            aMapClass.getMethod("removeAllPolylines")
        } catch (e: NoSuchMethodException) {
            // 尝试备用方法名，例如高德地图海外版可能是 removeAllPolyline
            Log.w("SafetyMarkersDebug", "removeAllPolylines not found, trying removeAllPolyline")
            aMapClass.getMethod("removeAllPolyline")
        }
        removeAllPolylinesMethod.invoke(aMap)
        Log.d("SafetyMarkersDebug", "clearNavigationRoute: Successfully removed all polylines.")
    } catch (e: Exception) {
        Log.e("SafetyMarkersDebug", "clearNavigationRoute: FAILED to remove polylines.", e)
    }
}

/**
 * 使用反射添加标记到地图
 */
private fun addMarkerToMap(
    aMap: Any,
    position: Any,
    color: Int,
    size: Float,
    outerRingSize: Float,
    outerRingAlpha: Int
) {
    try {
        // 创建标记选项
        val markerOptionsClass = Class.forName("com.amap.api.maps.model.MarkerOptions")
        val markerOptions = markerOptionsClass.newInstance()
        
        // 设置位置
        val positionMethod = markerOptionsClass.getMethod("position", position.javaClass)
        positionMethod.invoke(markerOptions, position)
        
        // 创建彩色标记图标
        val bitmapDescriptorFactoryClass = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory")
        val defaultMarkerMethod = bitmapDescriptorFactoryClass.getMethod("defaultMarker", Float::class.java)
        
        // 将RGB颜色转换为HSV色相值
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        val hue = hsv[0] // 提取色相值
        
        val icon = defaultMarkerMethod.invoke(null, hue)
        
        val iconMethod = markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor"))
        iconMethod.invoke(markerOptions, icon)
        
        // 添加标记
        val addMarkerMethod = aMap.javaClass.getMethod("addMarker", markerOptionsClass)
        addMarkerMethod.invoke(aMap, markerOptions)
        
    } catch (e: Exception) {
        Log.e("SafetyLocationMarkers", "添加标记失败", e)
    }
}

/**
 * 使用反射添加折线到地图
 */
private fun addPolylineToMap(
    aMap: Any,
    points: List<Any>,
    color: Int,
    width: Float,
    isDashed: Boolean = false
) {
    try {
        Log.d("SafetyLocationMarkers", "开始添加折线到地图")
        Log.d("SafetyLocationMarkers", "点数: ${points.size}, 颜色: $color, 宽度: $width, 虚线: $isDashed")
        
        // 创建折线选项
        val polylineOptionsClass = Class.forName("com.amap.api.maps.model.PolylineOptions")
        val polylineOptions = polylineOptionsClass.newInstance()
        Log.d("SafetyLocationMarkers", "PolylineOptions 创建成功")
        
        // 添加路径点
        val addMethod = polylineOptionsClass.getMethod("add", Class.forName("com.amap.api.maps.model.LatLng"))
        points.forEachIndexed { index, point ->
            addMethod.invoke(polylineOptions, point)
            Log.d("SafetyLocationMarkers", "添加路径点 $index: $point")
        }
        
        // 设置颜色
        val colorMethod = polylineOptionsClass.getMethod("color", Int::class.java)
        colorMethod.invoke(polylineOptions, color)
        Log.d("SafetyLocationMarkers", "设置颜色成功: $color")
        
        // 设置线宽
        val widthMethod = polylineOptionsClass.getMethod("width", Float::class.java)
        widthMethod.invoke(polylineOptions, width)
        Log.d("SafetyLocationMarkers", "设置线宽成功: $width")
        
        // 设置是否虚线
        if (isDashed) {
            try {
                val setDottedLineMethod = polylineOptionsClass.getMethod("setDottedLine", Boolean::class.java)
                setDottedLineMethod.invoke(polylineOptions, true)
                Log.d("SafetyLocationMarkers", "设置虚线模式成功")
            } catch (e: Exception) {
                Log.w("SafetyLocationMarkers", "设置虚线模式失败", e)
            }
        }
        
        // 添加折线到地图
        val addPolylineMethod = aMap.javaClass.getMethod("addPolyline", polylineOptionsClass)
        val polyline = addPolylineMethod.invoke(aMap, polylineOptions)
        Log.d("SafetyLocationMarkers", "折线添加到地图成功: $polyline")
        
    } catch (e: Exception) {
        Log.e("SafetyLocationMarkers", "添加折线失败", e)
        Log.e("SafetyLocationMarkers", "错误详情: ${e.message}")
        e.printStackTrace()
    }
} 