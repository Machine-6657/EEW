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
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.UserLocation
import com.example.eewapp.ui.utils.ColorUtils
import kotlinx.coroutines.delay
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.ui.components.createLatLng
// 使用Java反射机制处理可能的引用问题
// import java.lang.reflect.Method

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
    // 新增参数用于管理用户到震中的虚线
    userToEpicenterPolyline: Any?,
    onUserToEpicenterPolylineUpdated: (Any?) -> Unit,
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
    
    // 管理连接用户和震中的直线
    LaunchedEffect(aMap, userLocation, impact, epicenter) {
        val existingPolyline = userToEpicenterPolyline // 从参数捕获，这是 EarthquakeAMap 中保存的状态

        if (userLocation != null && impact != null) {
            // 条件满足，需要绘制虚线
            Log.d("AMapContent", "条件满足，准备绘制/更新 user-epicenter 虚线")
            // 先尝试移除已存在的虚线 (如果它确实是当前地图上的那条，或者我们需要更新它)
            existingPolyline?.let {
                try {
                    // 确认这个 existingPolyline 是否还在地图上，或者只是一个旧的引用
                    // 通常，如果状态正确，这里移除的是上一条有效的线
                    it.javaClass.getMethod("remove").invoke(it)
                    Log.d("AMapContent", "旧的 user-epicenter 虚线已从地图移除 (准备重绘)")
                } catch (e: Exception) {
                    Log.e("AMapContent", "移除旧的 user-epicenter 虚线失败", e)
                }
            }

            val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
            val newPolyline = addPolylineToMapReflection(
                aMap = aMap,
                points = listOf(userLatLng, epicenter),
                color = android.graphics.Color.rgb(255, 165, 0), // 橙色
                width = 12f,
                isDashed = true
            )
            if (newPolyline != null) {
                Log.d("AMapContent", "新的 user-epicenter 虚线已创建: $newPolyline")
            } else {
                Log.w("AMapContent", "创建新的 user-epicenter 虚线失败 (返回null)")
            }
            onUserToEpicenterPolylineUpdated(newPolyline) // 更新外部状态
        } else {
            // 条件不满足，不应显示虚线
            Log.d("AMapContent", "条件不满足 (userLocation或impact为null)，准备移除 user-epicenter 虚线")
            existingPolyline?.let {
                try {
                    it.javaClass.getMethod("remove").invoke(it)
                    Log.d("AMapContent", "user-epicenter 虚线已从地图移除 (条件不满足)")
                } catch (e: Exception) {
                    Log.e("AMapContent", "移除 user-epicenter 虚线失败 (条件不满足)", e)
                }
            }
            // 确保如果之前有线，现在状态也清空
            if (existingPolyline != null) {
                onUserToEpicenterPolylineUpdated(null) // 清除外部状态
            }
        }
    }
    
    // 如果有用户位置和影响数据，计算用户与震中的边界框，但不自动移动
    if (userLocation != null && impact != null) {
        val boundsSuccess = remember { mutableStateOf(false) }
        var bounds: Any? = null
        
        try {
            val boundsBuilder = createLatLngBoundsBuilder()
            includeInBounds(boundsBuilder, createLatLng(userLocation.latitude, userLocation.longitude))
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
 * 使用反射添加折线到地图
 */
private fun addPolylineToMapReflection(
    aMap: Any,
    points: List<Any>,
    color: Int,
    width: Float,
    isDashed: Boolean = false
): Any? {
    try {
        // 创建折线选项
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
            } catch (e: Exception) {
                Log.w("EarthquakeAMapContent", "设置虚线模式失败", e)
            }
        }
        
        // 添加折线到地图
        val addPolylineMethod = aMap.javaClass.getMethod("addPolyline", polylineOptionsClass)
        return addPolylineMethod.invoke(aMap, polylineOptions)
        
    } catch (e: Exception) {
        Log.e("EarthquakeAMapContent", "添加折线失败", e)
        return null
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
    
    // 波的颜色 - 修改为根据震级获取颜色，并调亮
    val waveColor = getLighterMagnitudeColor(impact.earthquake.magnitude)
    
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
//            delay(index * 800L) // 增加错开时间，使波浪更明显
            
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
//                    delay(200)
                }
            } catch (e: Exception) {
                // 处理可能的异常
                Log.e("EarthquakeWaveAnimationOnMap", "波纹动画失败", e)
            }
        }
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
        val latLngClass = Class.forName("com.amap.api.maps.model.LatLng")
        val includeMethod = builder.javaClass.getMethod("include", latLngClass)
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
 * 获取波浪颜色
 */
private fun getWaveColor(intensity: ShakingIntensity): Color {
    return ColorUtils.getWaveColor(intensity)
}

// Helper function to get a lighter version of the magnitude color
private fun getLighterMagnitudeColor(magnitude: Double, lightnessFactor: Float = 2f, saturationFactor: Float = 0.3f): Int {
    val originalColor = getMagnitudeColor(magnitude)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(originalColor, hsv)
    // Increase Value (brightness/lightness)
    hsv[2] = (hsv[2] * lightnessFactor).coerceIn(0f, 1f)
    // Decrease Saturation slightly
    hsv[1] = (hsv[1] * saturationFactor).coerceIn(0f, 1f)
    return android.graphics.Color.HSVToColor(hsv)
}

// 根据震级获取对应的颜色
private fun getMagnitudeColor(magnitude: Double): Int {
    return when {
        magnitude >= 6.0 -> android.graphics.Color.rgb(239, 83, 80) // 柔和红色 (6级及以上)
        magnitude >= 5.0 -> android.graphics.Color.rgb(255, 167, 38) // 柔和橙色 (5-5.9级)
        magnitude >= 4.0 -> android.graphics.Color.rgb(255, 220, 79) // 更暗的黄色 (4-4.9级)
        else -> android.graphics.Color.rgb(129, 199, 132) // 柔和绿色 (小于4级)
    }
}

// 根据震级获取圆圈半径（单位：米）
private fun getCircleRadius(magnitude: Double): Double {
    // 简单估算：震级每增加1，影响范围扩大10倍
    val baseRadius = 5000.0 // 基础半径5公里
    val radiusMultiplier = Math.pow(10.0, magnitude - 4) // 4级地震为基准
    return baseRadius * radiusMultiplier.coerceAtLeast(0.2).coerceAtMost(20.0)
} 