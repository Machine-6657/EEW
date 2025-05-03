package com.example.eewapp.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
// 高德地图相关类引用
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.UserLocation
import com.example.eewapp.ui.utils.ColorUtils
import kotlinx.coroutines.delay
import com.example.eewapp.data.ShakingIntensity
// 使用Java反射机制处理可能的引用问题
import java.lang.reflect.Method

/**
 * 在地图上渲染地震相关内容
 */
@Composable
fun EarthquakeAMapContent(
    aMap: Any, // 使用Any类型避免直接引用可能导致的编译问题
    earthquake: Earthquake,
    userLocation: UserLocation?,
    impact: EarthquakeImpact?,
    zoomLevel: Float = 10f,
    modifier: Modifier = Modifier
) {
    // 创建地震震中位置的LatLng对象
    val epicenter = createLatLng(
        earthquake.location.latitude,
        earthquake.location.longitude
    )
    
    // 获取震级对应的颜色，确保使用震级颜色而非蓝色
    val magnitudeColor = getMagnitudeColor(earthquake.magnitude)
    val r = android.graphics.Color.red(magnitudeColor)
    val g = android.graphics.Color.green(magnitudeColor)
    val b = android.graphics.Color.blue(magnitudeColor)
    
    // 使用固定屏幕大小的标记点表示地震位置，使用震级对应颜色
    AMapScreenMarker(
        aMap = aMap,
        position = epicenter,
        color = magnitudeColor, // 使用震级对应的颜色而非蓝色
        size = 14f,  // 内圆大小
        outerRingSize = 24f,  // 外圈大小
        outerRingAlpha = 100  // 外圈透明度
    )
    
    // 如果有用户位置和影响数据，计算用户与震中的边界框，但不自动移动
    if (userLocation != null && impact != null) {
        val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
        
        // 使用固定屏幕大小的蓝色标记点表示用户位置
        AMapScreenMarker(
            aMap = aMap,
            position = userLatLng,
            color = android.graphics.Color.rgb(30, 144, 255), // 亮蓝色 #1E90FF - 只有用户位置使用蓝色
            size = 12f,  // 内圆大小
            outerRingSize = 20f,  // 外圈大小
            outerRingAlpha = 80   // 外圈透明度
        )
        
        // 用户位置周围的半透明圆，表示用户所在区域
        AMapCircle(
            aMap = aMap,
            center = userLatLng,
            radius = 30000.0, // 30公里范围
            strokeWidth = 1f,
            strokeColor = android.graphics.Color.rgb(30, 144, 255), // 亮蓝色 #1E90FF - 用户圈使用蓝色
            fillColor = android.graphics.Color.argb(20, 30, 144, 255) // 低透明度的亮蓝色 - 用户圈使用蓝色
        )
        
        // 连接用户和震中的直线 - 使用震级颜色，不使用蓝色
        AMapPolyline(
            aMap = aMap,
            points = listOf(userLatLng, epicenter),
            color = android.graphics.Color.argb(150, r, g, b), // 使用震级颜色的半透明版本
            width = 3f
        )
        
        // 计算边界，但不自动移动相机
        val boundsSuccess = remember { mutableStateOf(false) }
        var bounds: Any? = null
        
        try {
            val boundsBuilder = createLatLngBoundsBuilder()
            includeInBounds(boundsBuilder, userLatLng)
            includeInBounds(boundsBuilder, epicenter)
            bounds = buildBounds(boundsBuilder)
            boundsSuccess.value = true
        } catch (e: Exception) {
            Log.e("EarthquakeAMapContent", "创建边界失败", e)
            boundsSuccess.value = false
        }
        
        // 这里我们不再自动移动相机，用户可以自行使用地图控制按钮进行导航
        // 如果需要，用户可以通过点击目标按钮回到震中位置
    } else {
        // 如果没有用户位置，也不自动移动相机到震中
        // 注释掉自动移动相机的代码
        // LaunchedEffect(epicenter) {
        //     animateCamera(aMap, epicenter, zoomLevel)
        // }
    }
    
    // 添加地震波动画效果
    if (impact != null) {
        // 使用AMap的Circle实现波纹效果
        EarthquakeWaveAnimationOnMap(
            epicenter = epicenter,
            impact = impact,
            aMap = aMap
        )
    }
}

/**
 * 地图上的线条
 */
@Composable
fun AMapPolyline(
    aMap: Any,
    points: List<Any>,
    color: Int,
    width: Float
) {
    DisposableEffect(points, color, width) {
        val polyline = try {
            // 使用反射创建PolylineOptions
            val polylineOptionsClass = Class.forName("com.amap.api.maps.model.PolylineOptions")
            val polylineOptions = polylineOptionsClass.newInstance()
            
            // 添加所有点
            val addAllMethod = polylineOptionsClass.getMethod("addAll", java.util.Collection::class.java)
            addAllMethod.invoke(polylineOptions, points)
            
            // 设置颜色
            val colorMethod = polylineOptionsClass.getMethod("color", Int::class.java)
            colorMethod.invoke(polylineOptions, color)
            
            // 设置宽度
            val widthMethod = polylineOptionsClass.getMethod("width", Float::class.java)
            widthMethod.invoke(polylineOptions, width)
            
            // 添加到地图
            val addPolylineMethod = aMap.javaClass.getMethod("addPolyline", polylineOptionsClass)
            addPolylineMethod.invoke(aMap, polylineOptions)
        } catch (e: Exception) {
            Log.e("AMapPolyline", "创建折线失败", e)
            null
        }
        
        onDispose {
            try {
                if (polyline != null) {
                    val removeMethod = polyline.javaClass.getMethod("remove")
                    removeMethod.invoke(polyline)
                }
            } catch (e: Exception) {
                Log.e("AMapPolyline", "移除折线失败", e)
            }
        }
    }
}

/**
 * 地震波动画效果（使用AMap的对象实现）
 */
@Composable
fun EarthquakeWaveAnimationOnMap(
    epicenter: Any,
    impact: EarthquakeImpact,
    aMap: Any
) {
    // 波的数量，增加波的数量
    val waveCount = 5
    
    // 波的颜色
    val waveColor = ColorUtils.getWaveColorInt(impact.intensity)
    
    // 波的最大半径（米）
    val maxRadius = impact.distanceFromUser * 1000 // 转为米
    
    // 波的状态管理
    val waves = remember {
        List(waveCount) { index ->
            mutableStateOf<Any?>(null)
        }
    }
    
    // 波的进度
    val waveProgresses = remember {
        List(waveCount) { index ->
            mutableStateOf(0f)
        }
    }
    
    // 初始化波对象并在组件销毁时清理
    DisposableEffect(epicenter, impact) {
        // 创建波对象
        val createdWaves = waves.mapIndexed { index, wave ->
            try {
                // 使用反射创建CircleOptions
                val circleOptionsClass = Class.forName("com.amap.api.maps.model.CircleOptions")
                val circleOptions = circleOptionsClass.newInstance()
                
                // 设置中心点
                val centerMethod = circleOptionsClass.getMethod("center", epicenter.javaClass)
                centerMethod.invoke(circleOptions, epicenter)
                
                // 设置半径
                val radiusMethod = circleOptionsClass.getMethod("radius", Double::class.java)
                radiusMethod.invoke(circleOptions, 0.0)
                
                // 设置边框宽度 - 增加边框宽度
                val strokeWidthMethod = circleOptionsClass.getMethod("strokeWidth", Float::class.java)
                strokeWidthMethod.invoke(circleOptions, 5f)
                
                // 设置边框颜色
                val strokeColorMethod = circleOptionsClass.getMethod("strokeColor", Int::class.java)
                strokeColorMethod.invoke(circleOptions, waveColor)
                
                // 设置填充颜色 - 适当地增加一些填充色透明度
                val fillColorMethod = circleOptionsClass.getMethod("fillColor", Int::class.java)
                val fillColor = android.graphics.Color.argb(
                    20, // 轻微透明度
                    android.graphics.Color.red(waveColor),
                    android.graphics.Color.green(waveColor),
                    android.graphics.Color.blue(waveColor)
                )
                fillColorMethod.invoke(circleOptions, fillColor)
                
                // 添加到地图
                val addCircleMethod = aMap.javaClass.getMethod("addCircle", circleOptionsClass)
                val circle = addCircleMethod.invoke(aMap, circleOptions)
                
                wave.value = circle
                circle
            } catch (e: Exception) {
                Log.e("EarthquakeWaveAnimationOnMap", "创建波纹失败", e)
                null
            }
        }
        
        // 清理函数
        onDispose {
            createdWaves.forEach { circle ->
                try {
                    if (circle != null) {
                        val removeMethod = circle.javaClass.getMethod("remove")
                        removeMethod.invoke(circle)
                    }
                } catch (e: Exception) {
                    Log.e("EarthquakeWaveAnimationOnMap", "移除波纹失败", e)
                }
            }
            waves.forEach { wave ->
                wave.value = null
            }
        }
    }
    
    // 波扩散动画
    waves.forEachIndexed { index, wave ->
        LaunchedEffect(wave.value, index) {
            if (wave.value == null) return@LaunchedEffect
            
            // 错开启动
            delay(index * 800L) // 增加错开时间，使波浪更明显
            
            try {
                // 动画循环
                while (true) {
                    for (progress in 0..100) {
                        waveProgresses[index].value = progress / 100f
                        val currentRadius = waveProgresses[index].value * maxRadius
                        
                        // 设置半径
                        wave.value?.let { circle ->
                            val radiusMethod = circle.javaClass.getMethod("setRadius", Double::class.java)
                            radiusMethod.invoke(circle, currentRadius)
                        }
                        
                        // 随着波扩散，透明度降低
                        val alpha = ((1f - waveProgresses[index].value) * 255).coerceIn(0f, 255f)
                        val currentColor = android.graphics.Color.argb(
                            alpha.toInt(),
                            android.graphics.Color.red(waveColor),
                            android.graphics.Color.green(waveColor),
                            android.graphics.Color.blue(waveColor)
                        )
                        
                        // 设置边框颜色
                        wave.value?.let { circle ->
                            val strokeColorMethod = circle.javaClass.getMethod("setStrokeColor", Int::class.java)
                            strokeColorMethod.invoke(circle, currentColor)
                            
                            // 同时调整填充颜色
                            val fillAlpha = (alpha * 0.2f).toInt().coerceAtMost(50)
                            val fillColor = android.graphics.Color.argb(
                                fillAlpha,
                                android.graphics.Color.red(waveColor),
                                android.graphics.Color.green(waveColor),
                                android.graphics.Color.blue(waveColor)
                            )
                            val fillColorMethod = circle.javaClass.getMethod("setFillColor", Int::class.java)
                            fillColorMethod.invoke(circle, fillColor)
                        }
                        
                        delay(20) // 加快动画速度
                    }
                    
                    // 重置为0
                    waveProgresses[index].value = 0f
                    
                    // 重置半径
                    wave.value?.let { circle ->
                        val radiusMethod = circle.javaClass.getMethod("setRadius", Double::class.java)
                        radiusMethod.invoke(circle, 0.0)
                    }
                    
                    // 在波完成一轮后稍作延迟，使波浪更加明显
                    delay(200)
                }
            } catch (e: Exception) {
                // 处理可能的异常
                Log.e("EarthquakeWaveAnimationOnMap", "波纹动画失败", e)
            }
        }
    }
}

/**
 * 创建LatLng对象
 */
private fun createLatLng(latitude: Double, longitude: Double): Any {
    try {
        val latLngClass = Class.forName("com.amap.api.maps.model.LatLng")
        val constructor = latLngClass.getConstructor(Double::class.java, Double::class.java)
        return constructor.newInstance(latitude, longitude)
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "创建LatLng失败", e)
        throw e
    }
}

/**
 * 创建LatLngBounds.Builder对象
 */
private fun createLatLngBoundsBuilder(): Any {
    try {
        val builderClass = Class.forName("com.amap.api.maps.model.LatLngBounds\$Builder")
        return builderClass.newInstance()
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "创建LatLngBounds.Builder失败", e)
        throw e
    }
}

/**
 * 在LatLngBounds.Builder中添加点
 */
private fun includeInBounds(builder: Any, latLng: Any) {
    try {
        val includeMethod = builder.javaClass.getMethod("include", latLng.javaClass)
        includeMethod.invoke(builder, latLng)
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "添加点到LatLngBounds.Builder失败", e)
        throw e
    }
}

/**
 * 构建LatLngBounds
 */
private fun buildBounds(builder: Any): Any {
    try {
        val buildMethod = builder.javaClass.getMethod("build")
        return buildMethod.invoke(builder)
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "构建LatLngBounds失败", e)
        throw e
    }
}

/**
 * 移动相机到指定位置
 */
private fun animateCamera(aMap: Any, latLng: Any, zoom: Float) {
    try {
        // 获取CameraUpdateFactory类
        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
        
        // 调用静态方法newLatLngZoom
        val newLatLngZoomMethod = cameraUpdateFactoryClass.getMethod("newLatLngZoom", latLng.javaClass, Float::class.java)
        val cameraUpdate = newLatLngZoomMethod.invoke(null, latLng, zoom)
        
        // 调用地图的animateCamera方法
        val animateCameraMethod = aMap.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
        animateCameraMethod.invoke(aMap, cameraUpdate)
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "移动相机失败", e)
    }
}

/**
 * 移动相机到指定边界
 */
private fun animateCameraWithBounds(aMap: Any, bounds: Any, padding: Int) {
    try {
        // 获取CameraUpdateFactory类
        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
        
        // 调用静态方法newLatLngBounds
        val newLatLngBoundsMethod = cameraUpdateFactoryClass.getMethod("newLatLngBounds", bounds.javaClass, Int::class.java)
        val cameraUpdate = newLatLngBoundsMethod.invoke(null, bounds, padding)
        
        // 调用地图的animateCamera方法
        val animateCameraMethod = aMap.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
        animateCameraMethod.invoke(aMap, cameraUpdate)
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "移动相机到边界失败", e)
    }
}

/**
 * 获取波浪颜色
 */
private fun getWaveColor(intensity: ShakingIntensity): Color {
    return ColorUtils.getWaveColor(intensity)
}

// 根据震级获取对应的颜色
private fun getMagnitudeColor(magnitude: Double): Int {
    return when {
        magnitude >= 6.0 -> android.graphics.Color.rgb(255, 0, 0) // 红色 (6级及以上)
        magnitude >= 5.0 -> android.graphics.Color.rgb(255, 165, 0) // 橙色 (5-5.9级)
        magnitude >= 4.0 -> android.graphics.Color.rgb(255, 255, 0) // 黄色 (4-4.9级)
        else -> android.graphics.Color.rgb(0, 128, 0) // 绿色 (小于4级)
    }
}

// 根据震级获取圆圈半径（单位：米）
private fun getCircleRadius(magnitude: Double): Double {
    // 简单估算：震级每增加1，影响范围扩大10倍
    val baseRadius = 5000.0 // 基础半径5公里
    val radiusMultiplier = Math.pow(10.0, magnitude - 4) // 4级地震为基准
    return baseRadius * radiusMultiplier.coerceAtLeast(0.2).coerceAtMost(20.0)
} 