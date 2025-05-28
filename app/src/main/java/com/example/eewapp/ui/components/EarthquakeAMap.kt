package com.example.eewapp.ui.components

import android.content.Context
import android.content.res.Resources
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eewapp.R
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.UserLocation
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.data.EarthquakeLocation
import com.example.eewapp.data.EscapeNavigationState
import com.example.eewapp.data.SafetyLocation
import com.example.eewapp.data.NavigationRoute
import com.example.eewapp.ui.theme.Primary
import com.example.eewapp.utils.EarthquakeUtils.calculateDistance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.eewapp.ui.components.switchMapTo3D
import com.example.eewapp.ui.components.switchMapTo2D
import com.example.eewapp.ui.components.createLatLng

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
    // 逃生导航相关参数
    escapeNavigationState: EscapeNavigationState = EscapeNavigationState(),
    onEscapeNavigationStart: ((UserLocation) -> Unit)? = null,
    onSafetyLocationSelected: ((SafetyLocation) -> Unit)? = null,
    onNavigationStart: ((UserLocation, SafetyLocation) -> Unit)? = null,
    onNavigationStop: (() -> Unit)? = null,
    onEscapeNavigationDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 添加调试日志
    Log.d("EarthquakeAMap", "UserLocation: $userLocation")
    Log.d("EarthquakeAMap", "Earthquakes count: ${earthquakes.size}")
    Log.d("EarthquakeAMap", "Significant earthquakes count: ${significantEarthquakes.size}")
    Log.d("EarthquakeAMap", "Selected earthquake: ${selectedEarthquake?.title}")
    Log.d("EarthquakeAMap", "Escape navigation active: ${escapeNavigationState.isActive}")
    
    // 地图是否已加载
    var isMapLoaded by remember { mutableStateOf(false) }
    
    // 地图实例
    var aMap by remember { mutableStateOf<Any?>(null) }
    
    // 地图当前缩放级别
    var zoomLevel by remember { mutableFloatStateOf(10f) }
    
    // 3D地图状态管理
    var is3DMode by remember { mutableStateOf(false) }
    var mapPitch by remember { mutableFloatStateOf(0f) }
    var mapRotation by remember { mutableFloatStateOf(0f) }
    
    // 选中的地震
    var localSelectedEarthquake by remember { mutableStateOf<Earthquake?>(selectedEarthquake ?: currentImpact?.earthquake) }
    
    // 选中的地震影响
    var selectedImpact by remember { mutableStateOf(currentImpact) }
    
    // 最新模拟的地震
    var lastSimulatedEarthquake by remember { mutableStateOf<Earthquake?>(null) }
    
    // 地图内容刷新标识，用于强制重新渲染
    var mapContentRefreshKey by remember { mutableIntStateOf(0) }
    
    // 记录是否已经定位到用户位置
    val hasMovedToUserLocation = remember { mutableStateOf(false) }
    
    // 用于存储由 SafetyLocationMarkers 绘制的当前安全点 Marker 对象
    var drawnSafetyPointMarkers by remember { mutableStateOf<List<Any>>(emptyList()) }
    
    // 用于存储由 SafetyLocationMarkers 绘制的当前导航路线 Polyline 对象
    var activeRoutePolyline by remember { mutableStateOf<Any?>(null) }
    
    // 用于存储由 EarthquakeAMapContent 绘制的连接用户位置和当前震中的虚线 Polyline 对象
    var userToEpicenterPolyline by remember { mutableStateOf<Any?>(null) }
    
    val context = LocalContext.current // 获取 Context
    val scope = rememberCoroutineScope() // 获取 CoroutineScope for flashlight

    // --- START: 7-Day Filter Logic (inside map content) ---
    val sevenDaysAgoMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    // Filter the lists passed into the Composable for use within the map content
    val recentEarthquakes = earthquakes.filter { it.time.time >= sevenDaysAgoMillis }
    val recentSignificantEarthquakes = significantEarthquakes.filter { it.earthquake.time.time >= sevenDaysAgoMillis }
    // --- END: 7-Day Filter Logic ---

    // --- START: SoundPool Management ---
    // Remember the SoundPool and soundId
    val soundPool = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
    }
    var soundId by remember { mutableIntStateOf(0) } // Store soundId in state
    var soundLoaded by remember { mutableStateOf(false) } // Track loading status

    // Load sound and release SoundPool on dispose
    DisposableEffect(Unit) {
        try {
            soundId = soundPool.load(context, R.raw.alarm_sound, 1)
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (sampleId == soundId && status == 0) {
                    soundLoaded = true
                    Log.d("EarthquakeAMapSound", "Sound R.raw.alarm_sound loaded successfully.")
                } else {
                    Log.e("EarthquakeAMapSound", "Failed to load sound R.raw.alarm_sound, status: $status")
                }
            }
            Log.d("EarthquakeAMapSound", "Initiated sound loading for R.raw.alarm_sound")
        } catch (e: Resources.NotFoundException) {
            Log.e("EarthquakeAMapSound", "Sound resource R.raw.alarm_sound not found.", e)
        } catch (e: Exception) {
            Log.e("EarthquakeAMapSound", "Error loading sound", e)
        }

        onDispose {
            Log.d("EarthquakeAMapSound", "Releasing SoundPool.")
            soundPool.release()
        }
    }
    // --- END: SoundPool Management ---

    // --- START: Alert Logic LaunchedEffect ---
    LaunchedEffect(selectedImpact, soundLoaded) { // Add soundLoaded as a key
        val currentSelectedImpact = selectedImpact

        if (currentSelectedImpact != null && currentSelectedImpact.earthquake.id.startsWith("simulated-")) {
            Log.d("EarthquakeAMapAlert", "模拟地震状态检测到")

            // --- 1. 震动 --- (Keep as before)
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
             if (vibrator?.hasVibrator() == true) {
                 try {
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                         val vibrationEffect = VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE)
                         vibrator.vibrate(vibrationEffect)
                         Log.d("EarthquakeAMapAlert", "触发震动 (Android O+)")
                     } else {
                         @Suppress("DEPRECATION")
                         vibrator.vibrate(1000)
                         Log.d("EarthquakeAMapAlert", "触发震动 (< Android O)")
                     }
                 } catch (e: Exception) {
                      Log.e("EarthquakeAMapAlert", "震动失败", e)
                 }
             } else {
                  Log.w("EarthquakeAMapAlert", "设备不支持震动")
             }

            // --- 2. 声音 (Modified) ---
            if (soundLoaded && soundId != 0) { // Check if sound is loaded
                 try {
                    soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                    Log.d("EarthquakeAMapAlert", "播放警报声音 (R.raw.alarm_sound)")
                 } catch (e: Exception) {
                     Log.e("EarthquakeAMapAlert", "播放声音失败", e)
                 }
            } else if (soundId != 0) {
                 Log.w("EarthquakeAMapAlert", "声音 R.raw.alarm_sound 尚未加载完成，无法播放")
                 // Optionally, wait or retry, but simply logging might be sufficient
            } else {
                 Log.e("EarthquakeAMapAlert", "无法播放声音，soundId 无效 (可能加载失败或未找到资源)")
            }

            // --- 3. 闪光灯 --- (Keep as before)
            scope.launch {
                 val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
                 var cameraId: String? = null
                 try {
                     if (cameraManager == null) {
                         Log.w("EarthquakeAMapAlert", "无法获取 CameraManager")
                         return@launch
                     }
                     cameraId = cameraManager.cameraIdList.firstOrNull { camId ->
                         val characteristics = cameraManager.getCameraCharacteristics(camId)
                         characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                         characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                     }

                     if (cameraId != null) {
                         Log.d("EarthquakeAMapAlert", "找到支持闪光灯的后置摄像头: $cameraId")
                         for (i in 1..6) { // Flash 3 times
                             try {
                                 cameraManager.setTorchMode(cameraId, i % 2 != 0)
                                 delay(300)
                             } catch (camE: CameraAccessException) {
                                 Log.e("EarthquakeAMapAlert", "控制闪光灯失败", camE)
                                 break
                             }
                         }
                         try { cameraManager.setTorchMode(cameraId, false) } catch (ignore: Exception) {}
                         Log.d("EarthquakeAMapAlert", "闪光灯闪烁完成")
                     } else {
                         Log.w("EarthquakeAMapAlert", "未找到支持闪光灯的后置摄像头")
                     }
                 } catch (e: Exception) {
                      Log.e("EarthquakeAMapAlert", "闪光灯操作失败", e)
                     if (cameraId != null && cameraManager != null) {
                         try { cameraManager.setTorchMode(cameraId, false) } catch (ignore: Exception) {}
                     }
                 }
            } // end scope.launch
        }
    } // End of LaunchedEffect
    // --- END: Alert Logic LaunchedEffect ---
    
    // 清理安全地点标记当逃生导航不再激活时
    LaunchedEffect(escapeNavigationState.isActive, drawnSafetyPointMarkers) {
        if (!escapeNavigationState.isActive && drawnSafetyPointMarkers.isNotEmpty()) {
            Log.d("EarthquakeAMap", "逃生导航不再激活，清理 ${drawnSafetyPointMarkers.size} 个安全点标记。")
            drawnSafetyPointMarkers.forEach { marker ->
                try {
                    marker.javaClass.getMethod("remove").invoke(marker)
                } catch (e: Exception) {
                    Log.e("EarthquakeAMap", "移除安全点标记失败: $marker", e)
                }
            }
            drawnSafetyPointMarkers = emptyList()
        }
    }
    
    // 逃生导航状态监听 - 自动切换3D模式
    LaunchedEffect(escapeNavigationState.isNavigating, aMap) {
        val currentAMap = aMap // 将委托属性赋值给局部变量
        if (currentAMap != null) {
            if (escapeNavigationState.isNavigating && escapeNavigationState.currentRoute != null) {
                // 切换到3D模式进行导航
                if (!is3DMode) {
                    is3DMode = true
                    mapPitch = 45f // 适中的倾斜角度
                    mapRotation = 0f // 北向
                    switchMapTo3D(currentAMap, mapPitch, mapRotation)
                    Log.d("EarthquakeAMap", "逃生导航激活，切换到3D模式")
                }
                
                // 调整视角以更好地展示导航路线
                val route = escapeNavigationState.currentRoute!!
                userLocation?.let { user ->
                    try {
                        val userLatLng = createLatLng(user.latitude, user.longitude)
                        val destLatLng = createLatLng(
                            route.destination.latitude,
                            route.destination.longitude
                        )
                        
                        // 创建包含起点和终点的边界，并添加适当的内边距
                        val latLngBoundsBuilderClass = Class.forName("com.amap.api.maps.model.LatLngBounds\$Builder")
                        val builder = latLngBoundsBuilderClass.newInstance()
                        val includeMethod = latLngBoundsBuilderClass.getMethod("include", userLatLng.javaClass)
                        includeMethod.invoke(builder, userLatLng)
                        includeMethod.invoke(builder, destLatLng)
                        val buildMethod = latLngBoundsBuilderClass.getMethod("build")
                        val bounds = buildMethod.invoke(builder)
                        
                        // 使用特殊的3D导航视角设置
                        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
                        val newLatLngBoundsRectMethod = cameraUpdateFactoryClass.getMethod(
                            "newLatLngBoundsRect", 
                            bounds.javaClass, 
                            Int::class.java, Int::class.java, Int::class.java, Int::class.java
                        )
                        val cameraUpdate = newLatLngBoundsRectMethod.invoke(
                            null, bounds, 150, 200, 150, 400 // 3D模式下的特殊内边距
                        )
                        
                        val animateCameraMethod = currentAMap.javaClass.getMethod(
                            "animateCamera", 
                            Class.forName("com.amap.api.maps.CameraUpdate")
                        )
                        animateCameraMethod.invoke(currentAMap, cameraUpdate)
                        
                        Log.d("EarthquakeAMap", "3D导航视角设置完成")
                    } catch (e: Exception) {
                        Log.e("EarthquakeAMap", "设置3D导航视角失败", e)
                    }
                }
            } else if (!escapeNavigationState.isNavigating && is3DMode) {
                // 导航结束，切换回2D模式
                is3DMode = false
                mapPitch = 0f
                mapRotation = 0f
                switchMapTo2D(currentAMap)
                Log.d("EarthquakeAMap", "逃生导航结束，切换回2D模式")
            }
        }
    }
    
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
            val currentAMap = aMap
            if (currentAMap != null) {
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
                        
                        // 移动相机到边界，使用特殊padding让内容显示在上半部分
                        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
                        val newLatLngBoundsRectMethod = cameraUpdateFactoryClass.getMethod(
                            "newLatLngBoundsRect", 
                            bounds.javaClass, 
                            Int::class.java, Int::class.java, Int::class.java, Int::class.java
                        )
                        val paddingLeft = 100
                        val paddingTop = 100
                        val paddingRight = 100
                        val paddingBottom = 800 // 增加底部padding，避免被卡片遮挡
                        val cameraUpdate = newLatLngBoundsRectMethod.invoke(
                            null, bounds, paddingLeft, paddingTop, paddingRight, paddingBottom
                        )
                        
                        val animateCameraMethod = currentAMap.javaClass.getMethod(
                            "animateCamera", Class.forName("com.amap.api.maps.CameraUpdate")
                        )
                        animateCameraMethod.invoke(currentAMap, cameraUpdate)
                    } else {
                        // 如果没有用户位置，仅移动到地震位置，显示在上半部分
                        animateCameraWithPadding(currentAMap, earthquakeLatLng, 9f)
                    }
                } catch (e: Exception) {
                    Log.e("EarthquakeAMap", "移动相机到选中地震失败", e)
                }
            }
        }
    }
    
    // 用户位置更新时，如果是首次获取位置，就定位到用户位置
    LaunchedEffect(userLocation) {
        val currentAMap = aMap
        if (userLocation != null && !hasMovedToUserLocation.value && currentAMap != null) {
            val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
            animateCameraWithPadding(currentAMap, userLatLng, 12f)
            hasMovedToUserLocation.value = true
            Log.d("EarthquakeAMap", "首次获取用户位置，定位到：${userLocation.latitude}, ${userLocation.longitude}")
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 渲染高德地图
        AMapCompose(
            modifier = Modifier.fillMaxSize(),
            // 3D地图参数
            is3DMode = is3DMode,
            pitch = mapPitch,
            rotation = mapRotation,
            enableRotate = true,
            enablePitch = true,
            onMapLoaded = { map ->
                isMapLoaded = true
                aMap = map
                
                // 地图加载完成后，立即定位到用户位置，显示在上半部分
                if (userLocation != null) {
                    val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                    animateCameraWithPadding(map, userLatLng, 12f) // 使用较高的缩放级别以更好地显示用户位置
                    Log.d("EarthquakeAMap", "地图已加载完成，定位到用户位置：${userLocation.latitude}, ${userLocation.longitude}")
                } else {
                    Log.d("EarthquakeAMap", "地图已加载完成，但用户位置不可用")
                }
            },
            onMapClick = { latLng ->
                // 点击空白处关闭地震详情 (Only if not simulating)
                if (currentImpact == null && selectedImpact?.earthquake?.id?.startsWith("simulated-") != true) {
                     localSelectedEarthquake = null
                     selectedImpact = null
                 }
            }
        ) { map ->
            // 用户位置标记（地图加载后立即显示，并确保持续存在）
            // LaunchedEffect(map, userLocation) { // 开始注释掉原生标记的添加
            //     if (userLocation != null) {
            //         Log.d("EarthquakeAMap", "开始添加用户位置标记")
            //         Log.d("EarthquakeAMap", "用户位置: ${userLocation.latitude}, ${userLocation.longitude}")
            //         Log.d("EarthquakeAMap", "地图实例: $map")
                        
            //         try {
            //             val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
            //             Log.d("EarthquakeAMap", "LatLng创建成功: $userLatLng")
                        
            //             // 使用原生API添加用户位置标记
            //             val latLngClass = Class.forName("com.amap.api.maps.model.LatLng")
            //             val markerOptionsClass = Class.forName("com.amap.api.maps.model.MarkerOptions")
            //             val bitmapDescriptorFactoryClass = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory")
                        
            //             val markerOptions = markerOptionsClass.newInstance()
            //             val positionMethod = markerOptionsClass.getMethod("position", latLngClass)
            //             positionMethod.invoke(markerOptions, userLatLng)
                        
            //             val defaultMarkerMethod = bitmapDescriptorFactoryClass.getMethod("defaultMarker", Float::class.java)
            //             val HUE_BLUE = 240f
            //             val userMarkerIcon = defaultMarkerMethod.invoke(null, HUE_BLUE)
            //             val iconMethod = markerOptionsClass.getMethod("icon", bitmapDescriptorFactoryClass.getSuperclass())
            //             iconMethod.invoke(markerOptions, userMarkerIcon)
                        
            //             val titleMethod = markerOptionsClass.getMethod("title", String::class.java)
            //             titleMethod.invoke(markerOptions, "我的位置")
                        
            //             val addMarkerMethod = map.javaClass.getMethod("addMarker", markerOptionsClass)
            //             val userMarker = addMarkerMethod.invoke(map, markerOptions)
                        
            //             Log.d("EarthquakeAMap", "用户位置标记添加成功: $userMarker")
            //         } catch (e: Exception) {
            //             Log.e("EarthquakeAMap", "添加用户位置标记失败", e)
            //         }
            //     } else {
            //         Log.w("EarthquakeAMap", "用户位置为空，跳过标记添加")
            //     }
            // } // 结束注释掉原生标记的添加
            
            // 用户位置标记（使用AMapScreenMarker组件） - 这个将作为唯一的用户位置标记
            if (userLocation != null) {
                val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                AMapScreenMarker(
                    aMap = map,
                    position = userLatLng,
                    color = android.graphics.Color.rgb(33, 150, 243), // 蓝色
                    size = 14f,
                    outerRingSize = 24f,
                    outerRingAlpha = 120
                )
            }
            
            // 使用mapContentRefreshKey作为依赖来触发重新渲染
            key(mapContentRefreshKey) {
            // 如果有当前影响的地震，显示地震震中和波纹
            currentImpact?.let { impact ->
                val earthquake = impact.earthquake
                EarthquakeAMapContent(
                    aMap = map,
                    earthquake = earthquake,
                    userLocation = userLocation,
                    impact = impact,
                    zoomLevel = zoomLevel,
                    userToEpicenterPolyline = userToEpicenterPolyline, // 传递状态
                    onUserToEpicenterPolylineUpdated = { polyline -> userToEpicenterPolyline = polyline } // 传递回调
                )
            }

            // 如果没有当前影响的地震...
            if (currentImpact == null) {
                val isSimulating = selectedImpact != null && localSelectedEarthquake?.id?.startsWith("simulated-") == true

                // --- Only draw recent earthquakes if NOT simulating ---
                if (!isSimulating) {
                    // 显示 *近7天* 的所有地震 (Use filtered list)
                    recentEarthquakes.forEach { earthquake -> // Iterate over filtered list
                        val position = createLatLng(earthquake.location.latitude, earthquake.location.longitude)
                        val magnitudeColor = getMagnitudeColor(earthquake.magnitude)
                        // Draw small background marker
                        AMapScreenMarker(
                            aMap = map,
                            position = position,
                            color = magnitudeColor,
                            size = 10f, // Size for non-selected markers
                            outerRingSize = 18f,
                            outerRingAlpha = 80
                        )
                        // Make it clickable
                        AMapCircleClickable(
                            aMap = map,
                            center = position,
                            radius = 5000.0, // Clickable radius
                            strokeWidth = 0f,
                            strokeColor = android.graphics.Color.TRANSPARENT,
                            fillColor = android.graphics.Color.TRANSPARENT,
                            onClick = {
                                // Set the selected earthquake (it's inherently recent because it's from recentEarthquakes)
                                localSelectedEarthquake = earthquake
                                // Find impact data only from the *recent* significant list
                                selectedImpact = recentSignificantEarthquakes.find { it.earthquake.id == earthquake.id }
                                Log.d("EarthquakeAMap", "Clicked recent earthquake: ${earthquake.title}, Found impact: ${selectedImpact != null}")
                                true
                            }
                        )
                    }
                }
                // --- ------------------------------------------- ---

                // 显示选中的地震详情 (可以是模拟的，也可以是手动点的 *近7天* 的地震)
                if (localSelectedEarthquake != null) {
                     // Ensure the selected earthquake is either simulated or recent before showing details/highlight
                     val earthquake = localSelectedEarthquake!! // Use non-null assertion as we check non-null condition
                     if (earthquake.id.startsWith("simulated-") || earthquake.time.time >= sevenDaysAgoMillis) {
                         val epicenter = createLatLng(earthquake.location.latitude, earthquake.location.longitude)
                         val magnitudeColor = getMagnitudeColor(earthquake.magnitude)

                         // 高亮显示选中的地震 (模拟或手动选中的近期地震)
                         AMapScreenMarker(
                             aMap = map,
                             position = epicenter,
                             color = magnitudeColor,
                             size = 14f, // Larger size for selected/highlighted
                             outerRingSize = 24f,
                             outerRingAlpha = 100
                         )

                         // 如果有影响数据 (模拟或手选对应的 *近7天* 的)，显示波纹效果和连线
                         // selectedImpact would have been found from recentSignificantEarthquakes or set by simulation
                         if (selectedImpact != null && selectedImpact!!.earthquake.id == earthquake.id) { // Ensure impact matches selected
                             EarthquakeAMapContent(
                                 aMap = map,
                                 earthquake = earthquake,
                                 userLocation = userLocation,
                                 impact = selectedImpact!!, // Use the found/simulated impact
                                 zoomLevel = zoomLevel,
                                 userToEpicenterPolyline = userToEpicenterPolyline, // 传递状态
                                 onUserToEpicenterPolylineUpdated = { polyline -> userToEpicenterPolyline = polyline } // 传递回调
                             )
                         }
                     } else {
                          // If a non-recent, non-simulated earthquake is somehow selected, clear it to avoid confusion
                          LaunchedEffect(localSelectedEarthquake) { // Use LaunchedEffect to avoid state issues during composition
                               Log.w("EarthquakeAMap", "Stale non-recent earthquake selected (${localSelectedEarthquake?.title}), clearing selection.")
                               localSelectedEarthquake = null
                               selectedImpact = null
                          }
                     }
                }
                }
            } // 地图内容刷新key的结束
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally // 水平居中图标和文本
            ) {
                // 震中按钮 (白底蓝字)
                Surface(
                    modifier = Modifier
                        .size(width = 56.dp, height = 56.dp) // 定义按钮尺寸
                        .shadow(4.dp, RoundedCornerShape(12.dp)) // 添加阴影和圆角
                        .clip(RoundedCornerShape(12.dp)) // 裁剪成圆角矩形
                        .clickable {
                            // 定位到最新模拟的地震
                            lastSimulatedEarthquake?.let { earthquake ->
                                val epicenter = createLatLng(
                                    earthquake.location.latitude,
                                    earthquake.location.longitude
                                )
                                // 移动镜头到地震位置，使用特殊padding显示在上半部分
                                val currentAMap = aMap
                                currentAMap?.let { animateCameraWithPadding(it, epicenter, zoomLevel) }
                            }
                        },
                    color = ComposeColor.White, // 背景色设为白色
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(4.dp) // 内部间距
                    ) {
                        // TODO: 替换为图片中的同心圆图标，这里暂时用一个玫红色图标代替
                        Icon(
                            // painter = painterResource(id = R.drawable.ic_epicenter_icon), // 暂时注释掉，因为资源不存在
                            imageVector = Icons.Filled.Adjust, // 使用系统图标作为临时替代
                            contentDescription = "定位到最新模拟震中",
                            tint = ComposeColor(0xFFCD7F7F), // 图标颜色改为饱和度低的玫红色
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(1.5.dp)) // 图标和文字间距
                        Text(
                            text = "震中",
                            color = ComposeColor(0xFFCD7F7F), // 文字颜色改为饱和度低的玫红色
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 我的位置按钮 (白底蓝字)
                Surface(
                    modifier = Modifier
                        .size(width = 56.dp, height = 56.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            // 定位到用户位置，显示在上半部分
                            if (userLocation != null) {
                                val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                                val currentAMap = aMap
                                currentAMap?.let { animateCameraWithPadding(it, userLatLng, 12f) }
                            }
                        },
                    color = ComposeColor.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(4.dp) // 内部间距
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "我的位置",
                            tint = ComposeColor(0xFF1E90FF), // 保持蓝色
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(1.5.dp)) // 图标和文字间距
                        Text(
                            text = "我的",
                            color = ComposeColor(0xFF1E90FF), // 添加蓝色文字
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 移除3D模式指示器 - 用户不需要这个按钮
                // AnimatedVisibility(...) { 3D模式指示器内容 }
            }
        }
        
        // --- 集成可折叠控制面板 (移动到左上角) ---
        CollapsibleControlPanel(
            modifier = Modifier
                .align(Alignment.TopStart) // Align to top-start
                .padding(start = 8.dp, top = 16.dp), // Padding from top-left edge
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
                        
                        // 中等震感地震 (震级5.0-6.0)
                        Triple("四川省成都市都江堰市", 31.0023, 103.6472),
                        Triple("四川省成都市温江区", 30.6825, 103.8560),
                        Triple("陕西省宝鸡市渭滨区", 34.3609, 107.2372),
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
                    
                    // 在设置新的模拟地震前，智能清理地震相关内容
                    val currentAMap = aMap
                    currentAMap?.let { 
                        clearEarthquakeMapContent(it, userLocation) { 
                            mapContentRefreshKey++ 
                        } 
                    }
                    // 显式移除旧的 userToEpicenterPolyline (如果存在)
                    userToEpicenterPolyline?.let { line ->
                        try {
                            line.javaClass.getMethod("remove").invoke(line)
                            Log.d("EarthquakeAMap", "已手动移除旧的 userToEpicenterPolyline (onSimulate)")
                        } catch (e: Exception) {
                            Log.e("EarthquakeAMap", "移除旧的 userToEpicenterPolyline 失败 (onSimulate)", e)
                        }
                    }
                    userToEpicenterPolyline = null // 清空状态
                    
                    // 设置为当前影响的地震
                    localSelectedEarthquake = simulatedEarthquake
                    selectedImpact = simulatedImpact
                    
                    // 移动相机到能同时看到地震和用户位置的位置
                    try {
                        val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                        val earthquakeLatLng = createLatLng(earthquakeLat, earthquakeLon)
                        val latLngBoundsBuilderClass = Class.forName("com.amap.api.maps.model.LatLngBounds\$Builder")
                        val builder = latLngBoundsBuilderClass.newInstance()
                        val includeMethod = latLngBoundsBuilderClass.getMethod("include", earthquakeLatLng.javaClass)
                        includeMethod.invoke(builder, earthquakeLatLng)
                        includeMethod.invoke(builder, userLatLng)
                        val buildMethod = latLngBoundsBuilderClass.getMethod("build")
                        val bounds = buildMethod.invoke(builder)
                        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
                        
                        // --- 使用特殊的padding让内容显示在上半部分 ---
                        val newLatLngBoundsRectMethod = cameraUpdateFactoryClass.getMethod(
                            "newLatLngBoundsRect", 
                            bounds.javaClass, 
                            Int::class.java, // paddingLeft
                            Int::class.java, // paddingTop
                            Int::class.java, // paddingRight
                            Int::class.java  // paddingBottom
                        )
                        val paddingLeft = 250
                        val paddingTop = 250 // 顶部padding较小
                        val paddingRight = 250
                        val paddingBottom = 1200 // 增加底部padding，将内容推向上半部分
                        val cameraUpdate = newLatLngBoundsRectMethod.invoke(
                            null, 
                            bounds, 
                            paddingLeft, 
                            paddingTop, 
                            paddingRight, 
                            paddingBottom
                        )
                        // --- -------------------------------------------- ---
                        
                        val animateCameraMethod = aMap!!.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
                        animateCameraMethod.invoke(aMap, cameraUpdate)
                    } catch (e: Exception) {
                        Log.e("EarthquakeAMap", "移动相机失败", e)
                        val epicenter = createLatLng(earthquakeLat, earthquakeLon)
                        val currentAMap = aMap
                        currentAMap?.let { animateCamera(it, epicenter, 9f) } // Fallback
                    }
                    
                    // 显示模拟地震提示
                    Log.d("EarthquakeAMap", "已创建模拟地震：震级${magnitude}，距离${distanceKm.toInt()}公里，预警时间${warningTimeSeconds}秒")
                    
                    // 更新最新模拟的地震
                    lastSimulatedEarthquake = simulatedEarthquake
                }
            },
            onCancel = {
                // 智能清理地震相关内容
                val currentAMap = aMap
                currentAMap?.let { 
                    clearEarthquakeMapContent(it, userLocation) { 
                        mapContentRefreshKey++ 
                    } 
                }
                // 显式移除 userToEpicenterPolyline (如果存在)
                userToEpicenterPolyline?.let { line ->
                    try {
                        line.javaClass.getMethod("remove").invoke(line)
                        Log.d("EarthquakeAMap", "已手动移除 userToEpicenterPolyline (onCancel)")
                    } catch (e: Exception) {
                        Log.e("EarthquakeAMap", "移除 userToEpicenterPolyline 失败 (onCancel)", e)
                    }
                }
                userToEpicenterPolyline = null // 清空状态
                
                // 清除当前选中的模拟地震和影响
                localSelectedEarthquake = null
                selectedImpact = null
                lastSimulatedEarthquake = null
                
                Log.d("CollapsibleControlPanel", "已取消模拟并清理地图内容")
            }
        )
        // --- ---


        // 显示地震信息卡片（导航时隐藏）
        AnimatedVisibility(
            visible = selectedImpact != null && !escapeNavigationState.isNavigating,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp) // 为底部导航栏留出空间
        ) {
             selectedImpact?.let { impact ->
                EarthquakeInfoCard(
                     earthquake = impact.earthquake,
                     impact = impact,
                     onClose = {
                                   localSelectedEarthquake = null
                        selectedImpact = null
                    },
                    onEscapeNavigation = if (impact.earthquake.id.startsWith("simulated-") && userLocation != null && onEscapeNavigationStart != null) {
                        { onEscapeNavigationStart.invoke(userLocation) }
                    } else null
                 )
             }
         }
        
        // 安全地点标记（在逃生导航激活时显示）
        if (escapeNavigationState.isActive) { // 首先检查 escapeNavigationState.isActive
            val currentAMapInstance = aMap // 将委托属性的值赋给局部变量
            if (currentAMapInstance != null) { // 然后检查局部变量是否为 null
                // 添加详细的调试日志
                Log.d("EarthquakeAMap", "逃生导航激活，准备显示安全地点标记")
                Log.d("EarthquakeAMap", "安全地点数量: ${escapeNavigationState.safetyLocations.size}")
                Log.d("EarthquakeAMap", "选中的目的地: ${escapeNavigationState.selectedDestination?.name}")
                Log.d("EarthquakeAMap", "当前路线: ${escapeNavigationState.currentRoute}")
                Log.d("EarthquakeAMap", "是否正在导航: ${escapeNavigationState.isNavigating}")
                Log.d("EarthquakeAMap", "用户位置: $userLocation")
                Log.d("EarthquakeAMap", "3D模式: $is3DMode")
                
                if (escapeNavigationState.currentRoute != null) {
                    val route = escapeNavigationState.currentRoute
                    Log.d("EarthquakeAMap", "路线详情:")
                    Log.d("EarthquakeAMap", "  目的地: ${route.destination.name}")
                    Log.d("EarthquakeAMap", "  距离: ${route.distanceInMeters}米")
                    Log.d("EarthquakeAMap", "  预计时间: ${route.estimatedDurationMinutes}分钟")
                    Log.d("EarthquakeAMap", "  路径点数量: ${route.routePoints.size}")
                    if (route.routePoints.isNotEmpty()) {
                        Log.d("EarthquakeAMap", "  起点: ${route.routePoints.first()}")
                        Log.d("EarthquakeAMap", "  终点: ${route.routePoints.last()}")
    }
}

                SafetyLocationMarkers(
                    aMap = currentAMapInstance, // 使用局部非空变量
                    safetyLocations = escapeNavigationState.safetyLocations,
                    selectedDestination = escapeNavigationState.selectedDestination,
                    userLocation = userLocation,
                    currentRoute = escapeNavigationState.currentRoute,
                    is3DMode = is3DMode, // 传入3D模式状态
                    drawnMarkers = drawnSafetyPointMarkers, // 传入当前绘制的标记列表
                    onUpdateMarkers = { newMarkers -> drawnSafetyPointMarkers = newMarkers }, // 回调以更新列表
                    activeRoutePolyline = activeRoutePolyline, // 传入当前路线Polyline对象
                    onActiveRoutePolylineUpdated = { polyline ->
                        Log.i("EarthquakeAMap", "[RoutePolyline CB] activeRoutePolyline WILL BE updated to: $polyline")
                        activeRoutePolyline = polyline
                    } // 回调以更新Polyline对象
                )
            } else {
                Log.w("EarthquakeAMap", "SafetyLocationMarkers 未调用，因为 aMap 实例为 null")
            }
        }
        
        // 逃生导航卡片（显示在地震信息卡片位置，导航时覆盖地震卡片）
        if (escapeNavigationState.isNavigating) {
            EscapeNavigationCard(
                isVisible = true,
                userLocation = userLocation,
                currentRoute = escapeNavigationState.currentRoute,
                earthquake = selectedImpact?.earthquake,
                impact = selectedImpact,
                isNavigating = escapeNavigationState.isNavigating,
                onStopNavigation = {
                    // 停止导航后直接回到地震信息卡片显示状态
                    onNavigationStop?.invoke()
                    onEscapeNavigationDismiss?.invoke() // 同时隐藏安全地点选择页面
                    aMap?.let { map ->
                        clearEarthquakeMapContent(map, userLocation) { 
                            mapContentRefreshKey++ 
                        }
                    }
                },
                onDismiss = { // 卡片本身的关闭按钮，也应清理地图
                    onEscapeNavigationDismiss?.invoke()
                    aMap?.let { map ->
                        clearEarthquakeMapContent(map, userLocation) { 
                            mapContentRefreshKey++ 
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp) // 与地震信息卡片相同位置
            )
        } else {
            // 未导航时显示安全地点选择列表（底部表单）
            EscapeNavigationSheet(
                isVisible = escapeNavigationState.isActive && !escapeNavigationState.isNavigating,
                userLocation = userLocation,
                safetyLocations = escapeNavigationState.safetyLocations,
                selectedDestination = escapeNavigationState.selectedDestination,
                currentRoute = escapeNavigationState.currentRoute,
                isNavigating = escapeNavigationState.isNavigating,
                onLocationSelected = { location ->
                    onSafetyLocationSelected?.invoke(location)
                },
                onStartNavigation = { location ->
                    userLocation?.let { userLoc ->
                        onNavigationStart?.invoke(userLoc, location)
                    }
                },
                onStopNavigation = { // 底部面板中的停止导航按钮
                    onNavigationStop?.invoke()
                    aMap?.let { map ->
                        clearEarthquakeMapContent(map, userLocation) { 
                            mapContentRefreshKey++ 
                        }
                    }
                },
                onDismiss = { // 底部面板被关闭时
                    onEscapeNavigationDismiss?.invoke()
                    aMap?.let { map ->
                        clearEarthquakeMapContent(map, userLocation) { 
                            mapContentRefreshKey++ 
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // --- START: New Parent-level Cleanup Logic for activeRoutePolyline ---
    LaunchedEffect(escapeNavigationState.isActive, escapeNavigationState.currentRoute, activeRoutePolyline, aMap) {
        val mapInstance = aMap
        if (mapInstance == null) {
            Log.w("EarthquakeAMapDebug", "Parent Cleanup LE: aMap is null. Cannot perform polyline cleanup.")
            return@LaunchedEffect
        }

        val polylineObjectInState = activeRoutePolyline // Capture for consistent use

        if ((!escapeNavigationState.isActive || escapeNavigationState.currentRoute == null)) {
            // Condition to clean: Nav is not active OR there is no current route.
            if (polylineObjectInState != null) {
                Log.d("EarthquakeAMapDebug", "Parent Cleanup LE: Nav inactive/no route, AND activeRoutePolyline exists ($polylineObjectInState). Attempting removal.")
                try {
                    polylineObjectInState.javaClass.getMethod("remove").invoke(polylineObjectInState)
                    Log.d("EarthquakeAMapDebug", "Parent Cleanup LE: Successfully removed polyline $polylineObjectInState from map.")
    } catch (e: Exception) {
                    Log.e("EarthquakeAMapDebug", "Parent Cleanup LE: Error removing polyline $polylineObjectInState from map.", e)
                }
                // Update the state to reflect removal, only if it wasn't already null by another means.
                if (activeRoutePolyline != null) { // Check the actual mutableState variable before setting
                    activeRoutePolyline = null
                    Log.d("EarthquakeAMapDebug", "Parent Cleanup LE: Set activeRoutePolyline state to null.")
                }
            }
        } else {
            // Navigation is active and there is a route. 
            // If polylineObjectInState is null here, SafetyLocationMarkers should be responsible for drawing it.
            // If polylineObjectInState is not null, things are as expected.
            Log.d("EarthquakeAMapDebug", "Parent Cleanup LE: Nav active and has route. No cleanup needed by parent. Polyline state: $polylineObjectInState")
        }
    }
    // --- END: New Parent-level Cleanup Logic ---
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

// 辅助函数：带padding的相机移动，让目标点显示在屏幕上半部分
private fun animateCameraWithPadding(aMap: Any, latLng: Any, zoom: Float) {
    try {
        // 获取CameraUpdateFactory类
        val cameraUpdateFactoryClass = Class.forName("com.amap.api.maps.CameraUpdateFactory")
        
        // 使用newLatLngZoomPadding方法，添加底部padding
        val newLatLngZoomMethod = cameraUpdateFactoryClass.getMethod("newLatLngZoom", latLng.javaClass, Float::class.java)
        val cameraUpdate = newLatLngZoomMethod.invoke(null, latLng, zoom)
        
        // 创建带padding的相机位置
        try {
            // 先获取当前相机位置
            val getCameraPositionMethod = aMap.javaClass.getMethod("getCameraPosition")
            val currentPosition = getCameraPositionMethod.invoke(aMap)
            
            // 获取当前位置信息
            val cameraPositionClass = Class.forName("com.amap.api.maps.model.CameraPosition")
            val targetField = cameraPositionClass.getField("target")
            val zoomField = cameraPositionClass.getField("zoom")
            val tiltField = cameraPositionClass.getField("tilt")
            val bearingField = cameraPositionClass.getField("bearing")
            
            val currentTilt = tiltField.get(currentPosition) as Float
            val currentBearing = bearingField.get(currentPosition) as Float
            
            // 创建新的相机位置构建器
            val builderClass = Class.forName("com.amap.api.maps.model.CameraPosition\$Builder")
            val builder = builderClass.newInstance()
            
            // 设置目标位置、缩放级别等
            val targetMethod = builderClass.getMethod("target", latLng.javaClass)
            val zoomMethod = builderClass.getMethod("zoom", Float::class.java)
            val tiltMethod = builderClass.getMethod("tilt", Float::class.java)
            val bearingMethod = builderClass.getMethod("bearing", Float::class.java)
            
            targetMethod.invoke(builder, latLng)
            zoomMethod.invoke(builder, zoom)
            tiltMethod.invoke(builder, currentTilt)
            bearingMethod.invoke(builder, currentBearing)
            
            // 构建相机位置
            val buildMethod = builderClass.getMethod("build")
            val newCameraPosition = buildMethod.invoke(builder)
            
            // 创建相机更新
            val newCameraPositionMethod = cameraUpdateFactoryClass.getMethod("newCameraPosition", cameraPositionClass)
            val paddedCameraUpdate = newCameraPositionMethod.invoke(null, newCameraPosition)
            
            // 应用相机更新
            val animateCameraMethod = aMap.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
            animateCameraMethod.invoke(aMap, paddedCameraUpdate)
            
            // 通过调整地图的padding来将内容显示在上半部分
            try {
                val setPaddingMethod = aMap.javaClass.getMethod("setPadding", Int::class.java, Int::class.java, Int::class.java, Int::class.java)
                setPaddingMethod.invoke(aMap, 0, 0, 0, 800) // 增加底部padding到800px，将内容推向上半部分
                Log.d("EarthquakeAMap", "设置地图padding成功，目标点将显示在上半部分")
            } catch (e: Exception) {
                Log.w("EarthquakeAMap", "设置地图padding失败，使用普通移动", e)
                // 如果setPadding方法失败，则使用普通的移动方式
                val animateCameraMethodFallback = aMap.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
                animateCameraMethodFallback.invoke(aMap, cameraUpdate)
            }
        } catch (e: Exception) {
            Log.w("EarthquakeAMap", "带padding的相机移动失败，使用普通移动", e)
            // 如果复杂的方法失败，则使用简单的移动方式
            val animateCameraMethod = aMap.javaClass.getMethod("animateCamera", Class.forName("com.amap.api.maps.CameraUpdate"))
            animateCameraMethod.invoke(aMap, cameraUpdate)
        }
    } catch (e: Exception) {
        Log.e("EarthquakeAMap", "带padding的相机移动完全失败", e)
    }
}


/**
 * 信息行组件 - 紧凑样式
 */
@Composable
private fun InfoRowCompact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: ComposeColor = ComposeColor.Black, // Default to TextPrimary color
    iconTint: ComposeColor = ComposeColor.DarkGray // Default to TextSecondary color
) {
    val TextSecondary = ComposeColor.DarkGray // Keep this for label color

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint, // Use the passed iconTint
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            
            Text(
                text = value,
                color = valueColor, // Use the passed valueColor
                style = MaterialTheme.typography.bodySmall
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
 */
@Composable
fun CollapsibleControlPanel(
    modifier: Modifier = Modifier,
    panelBackgroundColor: ComposeColor = ComposeColor.White.copy(alpha = 0.8f),
    handleIcon: ImageVector = Icons.Default.ChevronRight,
    handleExpandedIcon: ImageVector = Icons.Default.ChevronLeft,
    handleBackgroundColor: ComposeColor = ComposeColor.White, // 改为白色背景
    handleIconColor: ComposeColor = ComposeColor(0xFF1E90FF), // 改为蓝色图标
    buttonContainerColor: ComposeColor = ComposeColor(0xFF1E90FF),
    buttonContentColor: ComposeColor = ComposeColor.White,
    onSimulate: () -> Unit,
    onCancel: () -> Unit
) {
    var isPanelExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Handle (Icon Button) - 白色背景，蓝色图标
        IconButton(
            onClick = { isPanelExpanded = !isPanelExpanded },
            modifier = Modifier
                .padding(end = if (isPanelExpanded) 4.dp else 0.dp)
                .background(handleBackgroundColor, CircleShape)
                .shadow(4.dp, CircleShape) // 添加阴影效果
        ) {
            Icon(
                imageVector = if (isPanelExpanded) handleExpandedIcon else handleIcon,
                contentDescription = if (isPanelExpanded) "隐藏控制面板" else "显示控制面板",
                tint = handleIconColor
            )
        }

        // Animated Panel Content
        AnimatedVisibility(
            visible = isPanelExpanded,
            enter = slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }),
            exit = slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth })
        ) {
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                shadowElevation = 4.dp,
                color = panelBackgroundColor,
                 modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    // Simulate Button
                    Button(
                        onClick = onSimulate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        )
                    ) {
                        Text("模拟地震")
                    }
                    // Cancel Button
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        )
                    ) {
                        Text("取消模拟")
                    }
                }
            }
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

/**
 * 智能清理地震相关内容 - 保留用户位置标记
 */
private fun clearEarthquakeMapContent(
    aMap: Any, 
    userLocation: UserLocation?,
    onRefreshTriggered: () -> Unit
) {
    Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 函数开始")
    
    try {
        var clearSuccessActuallyHappened = false 
        
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 准备尝试清理圆圈 (removeAllCircles)")
        try {
            // val removeAllCirclesMethod = aMap.javaClass.getMethod("removeAllCircles")
            // removeAllCirclesMethod.invoke(aMap)
            // Log.d("EarthquakeAMapDebug", "已清理所有圆圈(Circles) - 若调用未注释")
            // clearSuccessActuallyHappened = true
            Log.w("EarthquakeAMapDebug", "clearEarthquakeMapContent: removeAllCircles() 调用已被注释以避免 NoSuchMethodException.")
        } catch (e2: Exception) {
            Log.e("EarthquakeAMapDebug", "clearEarthquakeMapContent: 尝试清理圆圈时发生异常 (即使调用被注释了):", e2)
        }
        
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: removeAllPolylines 在此函数中已被注释，Polyline的清理应由 SafetyLocationMarkers 负责。")
        
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 准备尝试清理多边形 (removeAllPolygons)")
        try {
            Log.w("EarthquakeAMapDebug", "clearEarthquakeMapContent: removeAllPolygons() 调用已被注释以避免 NoSuchMethodException.")
        } catch (e2: Exception) {
            // Log.w("EarthquakeAMapDebug", "清理多边形失败 (即使注释掉，也记录潜在的捕获逻辑)", e2) // 已在上方记录，避免重复
        }
        
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 准备尝试清理弧线 (removeAllArcs)")
        try {
            Log.w("EarthquakeAMapDebug", "clearEarthquakeMapContent: removeAllArcs() 调用已被注释以避免 NoSuchMethodException.")
        } catch (e2: Exception) {
            // Log.w("EarthquakeAMapDebug", "清理弧线失败 (即使注释掉，也记录潜在的捕获逻辑)", e2) // 已在上方记录，避免重复
        }
        
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 保留用户位置和其他重要标记")
        
        if (clearSuccessActuallyHappened) {
            Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 部分地图内容清理完成（若方法未注释），触发状态刷新")
        } else {
            Log.w("EarthquakeAMapDebug", "clearEarthquakeMapContent: 地图内容清理方法调用均被注释或失败，仍触发状态刷新")
        }
        
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 调用 onRefreshTriggered")
        onRefreshTriggered()
        
    } catch (e: Exception) {
        Log.e("EarthquakeAMapDebug", "clearEarthquakeMapContent 执行时发生未知异常", e)
        Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 异常后调用 onRefreshTriggered")
        onRefreshTriggered()
    }
    Log.d("EarthquakeAMapDebug", "clearEarthquakeMapContent: 函数结束")
} 

@Composable
private fun SafetyLocationMarkers(
    aMap: Any,
    safetyLocations: List<SafetyLocation>,
    selectedDestination: SafetyLocation?,
    userLocation: UserLocation?,
    currentRoute: NavigationRoute?, 
    is3DMode: Boolean,
    drawnMarkers: List<Any>,
    onUpdateMarkers: (List<Any>) -> Unit,
    activeRoutePolyline: Any?,
    onActiveRoutePolylineUpdated: (Any?) -> Unit
) {
    // Log.d("LocalSafetyMarkersDebug", "Local SafetyLocationMarkers recomposing. currentRoute is null=${currentRoute == null}, received activeRoutePolyline is null=${activeRoutePolyline == null}")
    // ^ 日志可以暂时注释掉或者按需保留

    // 清理旧的安全点标记 (逻辑不变)
    LaunchedEffect(safetyLocations, selectedDestination) {
        Log.d("LocalSafetyMarkersDebug", "SafetyPoint Markers LaunchedEffect: Old drawnMarkers count: ${drawnMarkers.size}. selectedDestination: ${selectedDestination?.id}")
        drawnMarkers.forEach { marker ->
            try {
                marker.javaClass.getMethod("remove").invoke(marker)
            } catch (e: Exception) {
                Log.e("LocalSafetyMarkersDebug", "Error removing old safety point marker: $marker", e)
            }
        }
        if (drawnMarkers.isNotEmpty()) {
            onUpdateMarkers(emptyList())
            Log.d("LocalSafetyMarkersDebug", "SafetyPoint Markers LaunchedEffect: Cleared old markers, onUpdateMarkers(emptyList) called.")
        }
    }

    val newMarkersList = remember(safetyLocations, selectedDestination) { mutableStateListOf<Any>() }

    safetyLocations.forEach { location ->
        val position = createLatLng(location.latitude, location.longitude)
        try {
            val markerOptionsClass = Class.forName("com.amap.api.maps.model.MarkerOptions")
            val markerOptions = markerOptionsClass.newInstance()
            val positionMethod = markerOptionsClass.getMethod("position", Class.forName("com.amap.api.maps.model.LatLng"))
            positionMethod.invoke(markerOptions, position)
            val iconMethod = markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor"))
            val bitmapDescriptorFactoryClass = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory")
            val defaultMarkerMethod = bitmapDescriptorFactoryClass.getMethod("defaultMarker", Float::class.javaPrimitiveType)
            
            // --- 修改目的地标记颜色 ---
            // 原来的: val iconColorHue = if (location == selectedDestination) 240f else 120f // 240f is HUE_BLUE, 120f is HUE_GREEN
            val iconColorHue = if (location == selectedDestination) {
                210f // HUE_AZURE (蔚蓝色/天蓝色) - 一个比 HUE_BLUE (240f) 浅的蓝色
            } else {
                120f // HUE_GREEN (绿色) - 保持不变
            }
            // --- 结束颜色修改 ---
            
            val safetyMarkerBitmapDescriptor = defaultMarkerMethod.invoke(null, iconColorHue)
            iconMethod.invoke(markerOptions, safetyMarkerBitmapDescriptor)
            val anchorMethod = markerOptionsClass.getMethod("anchor", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            anchorMethod.invoke(markerOptions, 0.5f, 1.0f) 
            val addMarkerMethod = aMap.javaClass.getMethod("addMarker", markerOptionsClass)
            val addedMarker = addMarkerMethod.invoke(aMap, markerOptions)
            addedMarker?.let { newMarkersList.add(it) }
        } catch (e: Exception) {
            Log.e("LocalSafetyMarkersDebug", "Error adding safety location marker for ${location.name}", e)
        }
    }
    
    LaunchedEffect(newMarkersList.toList()) { 
        Log.d("LocalSafetyMarkersDebug", "SafetyPoint Markers UpdateEffect: newMarkersList size ${newMarkersList.size}. Calling onUpdateMarkers.")
        onUpdateMarkers(newMarkersList.toList())
    }

    // --- 导航路线 Polyline 逻辑 ---
    LaunchedEffect(aMap, currentRoute, userLocation) { 
        val polylinePreviouslyOnMap = activeRoutePolyline 
        // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LaunchedEffect triggered. currentRoute is null=${currentRoute == null}, userLocation is null=${userLocation == null}, polylinePreviouslyOnMap (from parent state) is null=${polylinePreviouslyOnMap == null}")

        if (aMap == null) {
            // Log.w("LocalSafetyMarkersDebug", "Navigation Polyline LE: aMap is null, cannot proceed.")
            return@LaunchedEffect
        }

        if (currentRoute != null && userLocation != null) {
            // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: currentRoute is for '${currentRoute.destination.name}'. Preparing to draw/update.")
            
            polylinePreviouslyOnMap?.let {
                try {
                    it.javaClass.getMethod("remove").invoke(it)
                    // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: Removed polylinePreviouslyOnMap ($it) before drawing new one.")
                } catch (e: Exception) { 
                    // Log.e("LocalSafetyMarkersDebug", "Navigation Polyline LE: Error removing polylinePreviouslyOnMap for redraw ($it)", e) 
                }
            }

            val routePoints = mutableListOf<Any>()
            routePoints.add(createLatLng(userLocation.latitude, userLocation.longitude))
            currentRoute.routePoints.forEach { point -> routePoints.add(createLatLng(point.latitude, point.longitude)) }

            if (routePoints.size >= 2) {
                var newDrawnPolylineObject: Any? = null
                try {
                    val polylineOptionsClass = Class.forName("com.amap.api.maps.model.PolylineOptions")
                    val polylineOptions = polylineOptionsClass.newInstance()
                    val addAllMethod = polylineOptionsClass.getMethod("addAll", Class.forName("java.lang.Iterable"))
                    addAllMethod.invoke(polylineOptions, routePoints)
                    val widthMethod = polylineOptionsClass.getMethod("width", Float::class.javaPrimitiveType)
                    widthMethod.invoke(polylineOptions, 20f) // 路径宽度保持不变
                    
                    // --- 修改导航路径颜色 ---
                    val colorMethod = polylineOptionsClass.getMethod("color", Int::class.javaPrimitiveType)
                    val userLocationMarkerColor = android.graphics.Color.rgb(33, 150, 243) // 用户位置标记的蓝色
                    colorMethod.invoke(polylineOptions, userLocationMarkerColor)
                    // --- 结束颜色修改 ---
                    
                    val zIndexMethod = polylineOptionsClass.getMethod("zIndex", Float::class.javaPrimitiveType)
                    zIndexMethod.invoke(polylineOptions, 10f)
                    val addPolylineMethod = aMap.javaClass.getMethod("addPolyline", polylineOptionsClass)
                    newDrawnPolylineObject = addPolylineMethod.invoke(aMap, polylineOptions)
                    // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: New polyline drawn ($newDrawnPolylineObject).")
                } catch (e: Exception) {
                    // Log.e("LocalSafetyMarkersDebug", "Navigation Polyline LE: Error drawing new polyline.", e)
                    newDrawnPolylineObject = null 
                }
                if (polylinePreviouslyOnMap != newDrawnPolylineObject) { 
                     onActiveRoutePolylineUpdated(newDrawnPolylineObject)
                    // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: Called onActiveRoutePolylineUpdated with $newDrawnPolylineObject.")
                }
            } else {
                 // Log.w("LocalSafetyMarkersDebug", "Navigation Polyline LE: Not enough points (${routePoints.size}) to draw. Removing if any was present.")
                 polylinePreviouslyOnMap?.let {
                    try { it.javaClass.getMethod("remove").invoke(it) } catch (e: Exception) { /* ignore */ }
                 }
                 if (polylinePreviouslyOnMap != null) { 
                    onActiveRoutePolylineUpdated(null)
                    // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: Not enough points, called onActiveRoutePolylineUpdated(null).")
                 }
            }
        } else {
            // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: currentRoute or userLocation is null. Attempting to remove polylinePreviouslyOnMap: $polylinePreviouslyOnMap")
            polylinePreviouslyOnMap?.let {
                try {
                    it.javaClass.getMethod("remove").invoke(it)
                    // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: Polyline $it removed.")
                } catch (e: Exception) { 
                    // Log.e("LocalSafetyMarkersDebug", "Navigation Polyline LE: Error removing polyline $it.", e) 
                }
            }
            if (polylinePreviouslyOnMap != null) { 
                 onActiveRoutePolylineUpdated(null)
                 // Log.d("LocalSafetyMarkersDebug", "Navigation Polyline LE: currentRoute null, called onActiveRoutePolylineUpdated(null).")
            }
        }
    }
} 