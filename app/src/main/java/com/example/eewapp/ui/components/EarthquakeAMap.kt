package com.example.eewapp.ui.components

import android.graphics.Color
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eewapp.R
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.UserLocation
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.data.EarthquakeLocation
import com.example.eewapp.ui.theme.Primary
import com.example.eewapp.utils.EarthquakeUtils.calculateDistance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 使用高德地图的地震预警地图组件
 */
@Composable
fun EarthquakeAMap(
    userLocation: UserLocation?,
    earthquakes: List<Earthquake>,
    significantEarthquakes: List<EarthquakeImpact>,
    currentImpact: EarthquakeImpact?,
    selectedEarthquake: Earthquake? = null,
    modifier: Modifier = Modifier
) {
    // 添加调试日志
    Log.d("EarthquakeAMap", "UserLocation: $userLocation")
    Log.d("EarthquakeAMap", "Earthquakes count: ${earthquakes.size}")
    Log.d("EarthquakeAMap", "Significant earthquakes count: ${significantEarthquakes.size}")
    Log.d("EarthquakeAMap", "Selected earthquake: ${selectedEarthquake?.title}")
    
    // 地图是否已加载
    var isMapLoaded by remember { mutableStateOf(false) }
    
    // 地图实例
    var aMap by remember { mutableStateOf<Any?>(null) }
    
    // 地图当前缩放级别
    var zoomLevel by remember { mutableFloatStateOf(10f) }
    
    // 选中的地震
    var localSelectedEarthquake by remember { mutableStateOf<Earthquake?>(selectedEarthquake ?: currentImpact?.earthquake) }
    
    // 选中的地震影响
    var selectedImpact by remember { mutableStateOf(currentImpact) }
    
    // 最新模拟的地震
    var lastSimulatedEarthquake by remember { mutableStateOf<Earthquake?>(null) }
    
    // 记录是否已经定位到用户位置
    val hasMovedToUserLocation = remember { mutableStateOf(false) }
    
    // 更新选中的地震 (当通过点击设置或当前影响更新时)
    LaunchedEffect(selectedEarthquake, currentImpact) {
        val newSelection = selectedEarthquake ?: currentImpact?.earthquake
        if (newSelection != null) {
            localSelectedEarthquake = newSelection
            // 如果是当前影响的地震，同时更新影响数据
            if (currentImpact?.earthquake?.id == newSelection.id) {
                selectedImpact = currentImpact
            } else {
                // 否则尝试从significantEarthquakes中查找对应的影响数据
                selectedImpact = significantEarthquakes.find { it.earthquake.id == newSelection.id }
            }
            
            // 记录日志
            Log.d("EarthquakeAMap", "更新选中的地震: ${newSelection.title}, 震级: ${newSelection.magnitude}")
            
            // 当地图加载完成后，移动到选中的地震位置
            if (aMap != null) {
                try {
                    val earthquakeLatLng = createLatLng(
                        newSelection.location.latitude,
                        newSelection.location.longitude
                    )
                    
                    // 如果有用户位置，则移动到能同时看到地震和用户的区域
                    if (userLocation != null) {
                        val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                        
                        // 使用反射创建LatLngBounds.Builder
                        val latLngBoundsBuilderClass = Class.forName("com.amap.api.maps.model.LatLngBounds\$Builder")
                        val builder = latLngBoundsBuilderClass.newInstance()
                        
                        // 添加地震和用户位置
                        val includeMethod = latLngBoundsBuilderClass.getMethod("include", earthquakeLatLng.javaClass)
                        includeMethod.invoke(builder, earthquakeLatLng)
                        includeMethod.invoke(builder, userLatLng)
                        
                        // 构建边界
                        val buildMethod = latLngBoundsBuilderClass.getMethod("build")
                        val bounds = buildMethod.invoke(builder)
                        
                        // 移动相机到边界
                        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
                        val newLatLngBoundsMethod = cameraUpdateFactoryClass.getMethod(
                            "newLatLngBounds", bounds.javaClass, Int::class.java
                        )
                        val padding = 100 // 边界内边距（像素）
                        val cameraUpdate = newLatLngBoundsMethod.invoke(null, bounds, padding)
                        
                        val animateCameraMethod = aMap!!.javaClass.getMethod(
                            "animateCamera", Class.forName("com.amap.api.maps.CameraUpdate")
                        )
                        animateCameraMethod.invoke(aMap, cameraUpdate)
                    } else {
                        // 如果没有用户位置，仅移动到地震位置
                        animateCamera(aMap!!, earthquakeLatLng, 9f)
                    }
                } catch (e: Exception) {
                    Log.e("EarthquakeAMap", "移动相机到选中地震失败", e)
                }
            }
        }
    }
    
    // 用户位置更新时，如果是首次获取位置，就定位到用户位置
    LaunchedEffect(userLocation) {
        if (userLocation != null && !hasMovedToUserLocation.value && aMap != null) {
            val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
            animateCamera(aMap!!, userLatLng, 12f)
            hasMovedToUserLocation.value = true
            Log.d("EarthquakeAMap", "首次获取用户位置，定位到：${userLocation.latitude}, ${userLocation.longitude}")
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 渲染高德地图
        AMapCompose(
            modifier = Modifier.fillMaxSize(),
            onMapLoaded = { map ->
                isMapLoaded = true
                aMap = map
                
                // 地图加载完成后，立即定位到用户位置
                if (userLocation != null) {
                    val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                    animateCamera(map, userLatLng, 12f) // 使用较高的缩放级别以更好地显示用户位置
                    Log.d("EarthquakeAMap", "地图已加载完成，定位到用户位置：${userLocation.latitude}, ${userLocation.longitude}")
                } else {
                    Log.d("EarthquakeAMap", "地图已加载完成，但用户位置不可用")
                }
            },
            onMapClick = { latLng ->
                // 点击空白处关闭地震详情
                if (currentImpact == null) {
                    localSelectedEarthquake = null
                    selectedImpact = null
                }
            }
        ) { map ->
            // 如果有当前影响的地震，显示地震震中和波纹
            currentImpact?.let { impact ->
                val earthquake = impact.earthquake
                // 使用反射创建LatLng对象
                val epicenter = createLatLng(
                    earthquake.location.latitude,
                    earthquake.location.longitude
                )
                
                // 使用EarthquakeAMapContent处理地图内容
                EarthquakeAMapContent(
                    aMap = map,
                    earthquake = earthquake,
                    userLocation = userLocation,
                    impact = impact,
                    zoomLevel = zoomLevel
                )
            }
            
            // 如果没有当前影响的地震，显示所有地震和选中的地震
            if (currentImpact == null) {
                // 显示所有地震
                earthquakes.forEach { earthquake ->
                    // 使用反射创建LatLng对象
                    val position = createLatLng(
                        earthquake.location.latitude,
                        earthquake.location.longitude
                    )
                    
                    // 获取震级对应的颜色，确保使用震级颜色而非蓝色
                    val magnitudeColor = getMagnitudeColor(earthquake.magnitude)
                    
                    // 使用固定屏幕大小的标记点替代原来的圆形
                    AMapScreenMarker(
                        aMap = map,
                        position = position,
                        color = magnitudeColor, // 使用震级对应的颜色
                        size = 10f,  // 内圆大小
                        outerRingSize = 18f,  // 外圈大小
                        outerRingAlpha = 80   // 外圈透明度
                    )
                    
                    // 添加点击事件处理
                    AMapCircleClickable(
                        aMap = map,
                        center = position,
                        radius = 5000.0, // 稍大一些以便点击
                        strokeWidth = 0f,
                        strokeColor = android.graphics.Color.TRANSPARENT,
                        fillColor = android.graphics.Color.TRANSPARENT,
                        onClick = {
                            // 点击地震标记显示详情
                            localSelectedEarthquake = earthquake
                            
                            // 查找对应的影响
                            selectedImpact = significantEarthquakes.find { 
                                it.earthquake.id == earthquake.id 
                            }
                            
                            true
                        }
                    )
                }
                
                // 显示选中的地震详情
                if (localSelectedEarthquake != null) {
                    val earthquake = localSelectedEarthquake!!
                    // 使用反射创建LatLng对象
                    val epicenter = createLatLng(
                        earthquake.location.latitude,
                        earthquake.location.longitude
                    )
                    
                    // 获取震级对应的颜色
                    val magnitudeColor = getMagnitudeColor(earthquake.magnitude)
                    
                    // 高亮显示选中的地震，使用更大的标记点
                    AMapScreenMarker(
                        aMap = map,
                        position = epicenter,
                        color = magnitudeColor, // 确保使用震级颜色而非蓝色
                        size = 14f,  // 更大的内圆
                        outerRingSize = 24f,  // 更大的外圈
                        outerRingAlpha = 100  // 更明显的外圈
                    )
                    
                    // 如果有影响数据，显示波纹效果
                    if (selectedImpact != null) {
                        EarthquakeAMapContent(
                            aMap = map,
                            earthquake = earthquake,
                            userLocation = userLocation,
                            impact = selectedImpact,
                            zoomLevel = zoomLevel
                        )
                    }
                }
                
                // 如果用户位置可用，显示用户位置（只有这里使用蓝色标记）
                if (userLocation != null) {
                    // 使用反射创建LatLng对象
                    val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                    
                    // 使用固定屏幕大小的蓝色标记点表示用户位置
                    AMapScreenMarker(
                        aMap = map,
                        position = userLatLng,
                        color = android.graphics.Color.rgb(30, 144, 255), // 亮蓝色 #1E90FF（只有用户位置使用蓝色）
                        size = 12f,  // 内圆大小
                        outerRingSize = 20f,  // 外圈大小
                        outerRingAlpha = 80   // 外圈透明度
                    )
                }
            }
        }
        
        // 显示加载指示器
        AnimatedVisibility(
            visible = !isMapLoaded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ComposeColor(0xFF4B9AF7)
                )
            }
        }
        
        // 地图控制按钮，放置在屏幕右上角
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 我的位置按钮（蓝色）
                MapControlButton(
                    onClick = {
                        userLocation?.let { location ->
                            val userLatLng = createLatLng(location.latitude, location.longitude)
                            // 只移动镜头到用户位置，不改变其他状态
                            aMap?.let { animateCamera(it, userLatLng, zoomLevel) }
                        }
                    },
                    icon = Icons.Filled.MyLocation,
                    contentDescription = "定位到当前位置",
                    buttonColor = MaterialTheme.colorScheme.primary
                )
                
                // 定位到最新模拟的地震按钮（红色） - 保持此按钮用于定位
                MapControlButton(
                    onClick = {
                        // 定位到最新模拟的地震
                        lastSimulatedEarthquake?.let { earthquake ->
                            val epicenter = createLatLng(
                                earthquake.location.latitude,
                                earthquake.location.longitude
                            )
                            // 只移动镜头到地震位置，不改变选中状态
                            aMap?.let { animateCamera(it, epicenter, zoomLevel) }
                        }
                    },
                    icon = Icons.Filled.Warning, // 可以考虑换个图标，比如 BugReport 或 Science
                    contentDescription = "定位到最新模拟震中",
                    buttonColor = ComposeColor.Red
                )
            }
        }
        
        // --- 集成可折叠控制面板 ---
        CollapsibleControlPanel(
            modifier = Modifier
                .align(Alignment.CenterEnd) // Align to center-right
                .padding(end = 8.dp),      // Padding from edge
            onSimulate = {
                // 只有当用户位置可用时才能模拟地震
                if (userLocation != null) {
                    // 使用预定义的地点及其实际经纬度，以及对应的震级
                    // 格式: 四元组(地点名称, 纬度, 经度, 震级范围)
                    val earthquakeLocations = listOf(
                        // 强震感地震 (震级6.0-8.0)
                        Triple("四川省汶川县", 31.0023, 103.6472),
                        Triple("云南省丽江市古城区", 26.8721, 100.2299),
                        Triple("青海省玉树藏族自治州玉树市", 33.0062, 97.0085),
                        Triple("新疆维吾尔自治区喀什地区", 39.4707, 75.9922),
                        Triple("甘肃省张掖市甘州区", 38.9293, 100.4495),
                        
                        // 中等震感地震 (震级5.0-6.0)
                        Triple("四川省成都市都江堰市", 31.0023, 103.6472),
                        Triple("四川省成都市温江区", 30.6825, 103.8560),
                        Triple("甘肃省兰州市城关区", 36.0611, 103.8343),
                        Triple("陕西省宝鸡市渭滨区", 34.3609, 107.2372),
                        Triple("青海省西宁市城东区", 36.6232, 101.7804),
                        Triple("新疆维吾尔自治区乌鲁木齐市天山区", 43.8256, 87.6168),
                        
                        // 弱震感地震 (震级4.0-5.0)
                        Triple("北京市海淀区", 40.0300, 116.3000),
                        Triple("上海市浦东新区", 31.2304, 121.5404),
                        Triple("广东省广州市天河区", 23.1254, 113.3271),
                        Triple("浙江省杭州市西湖区", 30.2460, 120.1402)
                    )
                    
                    // 随机选择一个地点
                    val randomLocation = earthquakeLocations.random()
                    val randomPlace = randomLocation.first
                    val earthquakeLat = randomLocation.second
                    val earthquakeLon = randomLocation.third
                    
                    // 计算与用户位置的距离
                    val distanceKm = calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        earthquakeLat, earthquakeLon
                    )
                    
                    // 根据地点确定震级范围
                    val magnitudeRange = when (earthquakeLocations.indexOf(randomLocation)) {
                        // 强震感地震
                        in 0..4 -> 6.0 to 8.0
                        // 中等震感地震
                        in 5..10 -> 5.0 to 6.0
                        // 弱震感地震
                        else -> 4.0 to 5.0
                    }
                    
                    // 在震级范围内生成一个震级，只允许.0和.5
                    val minMagnitude = magnitudeRange.first
                    val maxMagnitude = magnitudeRange.second
                    val magnitudeSteps = ((maxMagnitude - minMagnitude) * 2).toInt() + 1
                    val magnitudeStep = (0 until magnitudeSteps).random() / 2.0
                    val magnitude = minMagnitude + magnitudeStep
                    
                    // 创建模拟地震
                    val simulatedEarthquake = Earthquake(
                        id = "simulated-${System.currentTimeMillis()}",
                        title = "M ${magnitude} - ${randomPlace}",
                        magnitude = magnitude,
                        depth = 10.0 + (Math.random() * 15.0), // 10-25公里的随机深度
                        location = EarthquakeLocation(
                            latitude = earthquakeLat,
                            longitude = earthquakeLon,
                            place = randomPlace 
                        ),
                        time = Date(),
                        url = ""
                    )
                    
                    // 计算预警时间（考虑实际的S波速度和传播路径）
                    // S波速度约为3km/s，考虑到地下传播路径会比直线距离长，
                    // 我们将实际传播距离估算为直线距离的1.2倍
                    val sWaveSpeed = 3.0 // S波速度，km/s
                    val actualDistance = distanceKm * 1.2 // 考虑实际传播路径
                    val warningTimeSeconds = (actualDistance / sWaveSpeed).toInt()
                    
                    // 计算预估到达时间（当前时间 + 预警时间）
                    val estimatedArrivalTime = System.currentTimeMillis() + (warningTimeSeconds * 1000)
                    
                    // 根据震级和距离计算震度
                    // 使用ShakingIntensity的伴生对象方法估算震感强度
                    val intensity = ShakingIntensity.estimateFromMagnitudeAndDistance(magnitude, distanceKm)
                    
                    // 创建模拟影响
                    val simulatedImpact = EarthquakeImpact(
                        earthquake = simulatedEarthquake,
                        intensity = intensity,
                        distanceFromUser = distanceKm,
                        secondsUntilArrival = warningTimeSeconds,
                        estimatedArrivalTime = estimatedArrivalTime
                    )
                    
                    // 设置为当前影响的地震
                    localSelectedEarthquake = simulatedEarthquake
                    selectedImpact = simulatedImpact
                    
                    // 移动相机到能同时看到地震和用户位置的位置
                    try {
                        // 创建用户位置和地震位置的LatLng对象
                        val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                        val earthquakeLatLng = createLatLng(earthquakeLat, earthquakeLon)
                        
                        // 使用反射创建LatLngBounds.Builder
                        val latLngBoundsBuilderClass = Class.forName("com.amap.api.maps.model.LatLngBounds\$Builder")
                        val builder = latLngBoundsBuilderClass.newInstance()
                        
                        // 添加地震和用户位置
                        val includeMethod = latLngBoundsBuilderClass.getMethod("include", earthquakeLatLng.javaClass)
                        includeMethod.invoke(builder, earthquakeLatLng)
                        includeMethod.invoke(builder, userLatLng)
                        
                        // 构建边界
                        val buildMethod = latLngBoundsBuilderClass.getMethod("build")
                        val bounds = buildMethod.invoke(builder)
                        
                        // 移动相机到边界
                        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
                        val newLatLngBoundsMethod = cameraUpdateFactoryClass.getMethod(
                            "newLatLngBounds", bounds.javaClass, Int::class.java
                        )
                        val padding = 100 // 边界内边距（像素）
                        val cameraUpdate = newLatLngBoundsMethod.invoke(null, bounds, padding)
                        
                        val animateCameraMethod = aMap!!.javaClass.getMethod(
                            "animateCamera", Class.forName("com.amap.api.maps.CameraUpdate")
                        )
                        animateCameraMethod.invoke(aMap, cameraUpdate)
                    } catch (e: Exception) {
                        Log.e("EarthquakeAMap", "移动相机失败", e)
                        // 如果边界方法失败，退回到简单地移动到地震位置
                        val epicenter = createLatLng(earthquakeLat, earthquakeLon)
                        aMap?.let { animateCamera(it, epicenter, 9f) }
                    }
                    
                    // 显示模拟地震提示
                    Log.d("EarthquakeAMap", "已创建模拟地震：震级${magnitude}，距离${distanceKm.toInt()}公里，预警时间${warningTimeSeconds}秒")
                    
                    // 更新最新模拟的地震
                    lastSimulatedEarthquake = simulatedEarthquake
                }
            },
            onCancel = {
                // TODO: 实现取消模拟地震的逻辑
                // 例如：将 localSelectedEarthquake 和 selectedImpact 设置为 null？
                // 或者如果你有专门的ViewModel状态来表示模拟状态，在这里重置它
                Log.d("CollapsibleControlPanel", "取消模拟按钮被点击")
                 localSelectedEarthquake = null // 示例：清除当前选中的模拟地震
                 selectedImpact = null         // 示例：清除当前选中的模拟影响
            }
        )
        // --- ---

        // 显示当前选中的地震信息卡片 (如果选中且无当前影响)
        AnimatedVisibility(
            visible = localSelectedEarthquake != null && currentImpact == null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            localSelectedEarthquake?.let { eq ->
                // 如果能找到对应的impact信息，则显示完整卡片，否则显示简化卡片
                val impactToShow = significantEarthquakes.find { it.earthquake.id == eq.id } ?: selectedImpact
                if (impactToShow != null){
                     EarthquakeInfoCardCompact(earthquake = eq, impact = impactToShow, onClose = { localSelectedEarthquake = null })
                 } else {
                    // 可以选择显示一个更简化的卡片，如果impact找不到
                    // SimplifiedEarthquakeCard(earthquake = eq, onClose = { localSelectedEarthquake = null })
                    Log.d("EarthquakeAMap", "Selected earthquake ${eq.title} has no matching impact data.")
                 }
            }
        }

        // 显示地震信息卡片 (显示当前影响的地震，或者由模拟地震触发的)
        AnimatedVisibility(
            visible = selectedImpact != null, // Modified: Show card if selectedImpact is not null
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedImpact?.let { impact ->
                EarthquakeInfoCardCompact(earthquake = impact.earthquake, impact = impact, onClose = {
                    selectedImpact = null
                    // 如果关闭的是模拟地震卡片，也清除选中的模拟地震
                    if (localSelectedEarthquake?.id?.startsWith("simulated-") == true) {
                         localSelectedEarthquake = null
                    }
                })
            }
        }
    }
}

// 辅助函数：创建LatLng对象
private fun createLatLng(latitude: Double, longitude: Double): Any {
    try {
        val latLngClass = Class.forName("com.amap.api.maps.model.LatLng")
        val constructor = latLngClass.getConstructor(Double::class.java, Double::class.java)
        return constructor.newInstance(latitude, longitude)
    } catch (e: Exception) {
        Log.e("EarthquakeAMap", "创建LatLng失败", e)
        throw e
    }
}

// 辅助函数：移动相机到指定位置
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
        Log.e("EarthquakeAMap", "移动相机失败", e)
    }
}

// 辅助函数：调整相机缩放级别
private fun animateCameraZoom(aMap: Any, zoom: Float) {
    try {
        // 获取CameraUpdateFactory类
        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
        
        // 调用静态方法zoomTo
        val zoomToMethod = cameraUpdateFactoryClass.getMethod("zoomTo", Float::class.java)
        val cameraUpdate = zoomToMethod.invoke(null, zoom)
        
        // 调用地图的animateCamera方法
        val animateCameraMethod = aMap.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
        animateCameraMethod.invoke(aMap, cameraUpdate)
    } catch (e: Exception) {
        Log.e("EarthquakeAMap", "调整缩放级别失败", e)
    }
}

/**
 * 地震信息卡片 - 紧凑样式
 */
@Composable
fun EarthquakeInfoCardCompact(
    earthquake: Earthquake,
    impact: EarthquakeImpact,
    onClose: () -> Unit
) {
    // 定义颜色常量
    val RedEmphasis = ComposeColor(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = ComposeColor.Black // 主要文本颜色
    val TextSecondary = ComposeColor.DarkGray // 次要文本颜色
    val BackgroundPrimary = ComposeColor.White // 主要背景色
    val WarningBackground = ComposeColor(0xFFFFF3E0) // 警告背景色
    
    // 根据震感获取背景颜色
    val cardBackgroundColor = when (impact.intensity.level) {
        in 0..2 -> ComposeColor(0xFFE8F4E8) // 柔和的浅绿色背景
        in 3..4 -> ComposeColor(0xFFFFF0E0) // 柔和的浅橙色背景
        else -> ComposeColor(0xFFFBE9E7) // 柔和的浅红色背景
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor,
            contentColor = TextPrimary
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 使用三列布局，每列包含标签和值
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 震中列
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .weight(1.2f)
                ) {
                    Text(
                        text = "震中",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = earthquake.location.place,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 2, // 允许最多两行
                        lineHeight = 20.sp // 行高设置紧凑一些
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 预警震级列
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(0.4f)
                ) {
                    Text(
                        text = "预警震级",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                when {
                                    earthquake.magnitude >= 6.0 -> ComposeColor(0xFFFF0000) // 红色 (6级及以上)
                                    earthquake.magnitude >= 5.0 -> ComposeColor(0xFFFFA500) // 橙色 (5-5.9级)
                                    earthquake.magnitude >= 4.0 -> ComposeColor(0xFFFFFF00) // 黄色 (4-4.9级)
                                    else -> ComposeColor(0xFF008000) // 绿色 (小于4级)
                                }, 
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${earthquake.magnitude}",
                            color = ComposeColor.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 预估震感列
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.4f)
                ) {
                    Text(
                        text = "预估震感",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "${getIntensityText(impact.intensity)}",
                        color = getIntensityColorNew(impact.intensity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                color = ComposeColor.LightGray.copy(alpha = 0.5f),
                thickness = 1.dp
            )
            
            // 地震详细信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 位置信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    InfoRowCompact(
                        icon = Icons.Filled.LocationOn,
                        label = "震中位置",
                        value = "东经${String.format("%.1f", earthquake.location.longitude)}°, 北纬${String.format("%.1f", earthquake.location.latitude)}°"
                    )
                }
                
                // 时间信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    InfoRowCompact(
                        icon = Icons.Filled.Timer,
                        label = "发生时间",
                        value = formatDate(earthquake.time)
                    )
                }
            }
            
            // 预警信息行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 距离信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    InfoRowCompact(
                        icon = Icons.Filled.LocationOn,
                        label = "震中距离",
                        value = "${impact.distanceFromUser.toInt()}公里"
                    )
                }
                
                // 预警时间
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    // 使用 remember 和 LaunchedEffect 来实现倒计时
                    var remainingSeconds by remember { mutableStateOf(impact.secondsUntilArrival) }
                    
                    LaunchedEffect(impact.estimatedArrivalTime) {
                        while (remainingSeconds > 0) {
                            delay(1000) // 每秒更新一次
                            val currentTime = System.currentTimeMillis()
                            val newRemainingSeconds = ((impact.estimatedArrivalTime - currentTime) / 1000).toInt()
                            remainingSeconds = maxOf(0, newRemainingSeconds)
                        }
                    }
                    
                    // 预警时间信息
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WarningBackground, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = RedEmphasis,
                                modifier = Modifier.size(16.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Column {
                                Text(
                                    text = "预计到达",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                Text(
                                    text = if (remainingSeconds > 0) "${remainingSeconds}秒后" else "已到达",
                                    color = RedEmphasis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 信息行组件 - 紧凑样式
 */
@Composable
private fun InfoRowCompact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val TextPrimary = ComposeColor.Black // 主要文本颜色
    val TextSecondary = ComposeColor.DarkGray // 次要文本颜色

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// 获取地震烈度文本
private fun getIntensityText(intensity: ShakingIntensity): String {
    return when (intensity.level) {
        in 0..2 -> "弱"
        in 3..4 -> "中"
        else -> "强"
    }
}

// 获取地震烈度颜色 - 新版
private fun getIntensityColorNew(intensity: ShakingIntensity): ComposeColor {
    return when (intensity.level) {
        in 0..2 -> ComposeColor(0xFF4CAF50) // 绿色
        in 3..4 -> ComposeColor(0xFFFF9800) // 橙色
        else -> ComposeColor(0xFFD32F2F) // 红色
    }
}

/**
 * 格式化日期
 */
private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}

/**
 * 一个可从右侧展开/折叠的控制面板 Composable.
 *
 * @param modifier Modifier to be applied to the Row container (use for alignment in parent).
 * @param panelBackgroundColor 背景颜色.
 * @param handleIcon Handle icon when collapsed.
 * @param handleExpandedIcon Handle icon when expanded.
 * @param handleBackgroundColor 背景颜色.
 * @param handleContentColor icon 颜色.
 * @param onSimulate Callback when the simulate button is clicked.
 * @param onCancel Callback when the cancel button is clicked.
 */
@Composable
fun CollapsibleControlPanel(
    modifier: Modifier = Modifier,
    panelBackgroundColor: ComposeColor = MaterialTheme.colorScheme.surfaceVariant,
    handleIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ChevronLeft, // Icon when collapsed (pointing left to open)
    handleExpandedIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ChevronRight, // Icon when expanded (pointing right to close)
    handleBackgroundColor: ComposeColor = MaterialTheme.colorScheme.primaryContainer,
    handleContentColor: ComposeColor = MaterialTheme.colorScheme.onPrimaryContainer,
    onSimulate: () -> Unit,
    onCancel: () -> Unit
) {
    var isPanelExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier, // Allows caller to position this Row (e.g., align to end)
        verticalAlignment = Alignment.CenterVertically // Align handle and panel vertically
    ) {
        // Animated Panel Content (Slides in/out to the left of the handle)
        AnimatedVisibility(
            visible = isPanelExpanded,
            // Animate entering from the right edge of its container
            enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
            // Animate exiting towards the right edge of its container
            exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
        ) {
            Surface(
                // Give the panel rounded corners only on the left side
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                shadowElevation = 4.dp,
                color = panelBackgroundColor,
                // Optional: Limit the panel's height if needed
                 modifier = Modifier.height(IntrinsicSize.Min) // Adjust height based on content
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        // Control the width based on the widest button
                        .width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.Start, // Align buttons to the start (left)
                    // Space buttons and center them vertically within the column
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    // Simulate Button
                    Button(
                        onClick = onSimulate,
                        modifier = Modifier.fillMaxWidth() // Make button fill panel width
                    ) {
                        Text("模拟地震")
                    }
                    // Cancel Button
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth() // Make button fill panel width
                    ) {
                        Text("取消模拟")
                    }
                }
            }
        }

        // Handle (Icon Button) - Always visible at the very end (right) of the Row
        IconButton(
            onClick = { isPanelExpanded = !isPanelExpanded },
            modifier = Modifier
                // Small space between panel (if visible) and handle
                .padding(start = if (isPanelExpanded) 4.dp else 0.dp)
                .background(handleBackgroundColor, CircleShape) // Circular background for handle
        ) {
            Icon(
                imageVector = if (isPanelExpanded) handleExpandedIcon else handleIcon,
                contentDescription = if (isPanelExpanded) "隐藏控制面板" else "显示控制面板",
                tint = handleContentColor
            )
        }
    }
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