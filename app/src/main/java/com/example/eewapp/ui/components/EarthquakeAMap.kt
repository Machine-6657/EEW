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
                // 点击空白处关闭地震详情 (Only if not simulating)
                if (currentImpact == null && selectedImpact?.earthquake?.id?.startsWith("simulated-") != true) {
                     localSelectedEarthquake = null
                     selectedImpact = null
                 }
            }
        ) { map ->
           

            // 如果有当前影响的地震，显示地震震中和波纹
            currentImpact?.let { impact ->
                val earthquake = impact.earthquake
                val epicenter = createLatLng(earthquake.location.latitude, earthquake.location.longitude)
                EarthquakeAMapContent(
                    aMap = map,
                    earthquake = earthquake,
                    userLocation = userLocation,
                    impact = impact,
                    zoomLevel = zoomLevel
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
                                 zoomLevel = zoomLevel
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

                // 如果用户位置可用，显示用户位置标记 (始终显示)
                if (userLocation != null) {
                    val userLatLng = createLatLng(userLocation.latitude, userLocation.longitude)
                    AMapScreenMarker(
                        aMap = map,
                        position = userLatLng,
                        color = android.graphics.Color.rgb(30, 144, 255),
                        size = 12f,
                        outerRingSize = 20f,
                        outerRingAlpha = 80
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally // 水平居中图标和文本
            ) {
                // 震中按钮 (红色)
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
                                // 只移动镜头到地震位置，不改变选中状态
                                aMap?.let { animateCamera(it, epicenter, zoomLevel) }
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
                        // TODO: 替换为图片中的同心圆图标，这里暂时用一个红色图标代替
                        Icon(
                            // painter = painterResource(id = R.drawable.ic_epicenter_icon), // 暂时注释掉，因为资源不存在
                            imageVector = Icons.Filled.Adjust, // 使用系统图标作为临时替代
                            contentDescription = "定位到最新模拟震中",
                            tint = ComposeColor.Red, // 图标颜色设为红色
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(1.5.dp)) // 图标和文字间距
                        Text(
                            text = "震中",
                            color = ComposeColor.Red, // 文字颜色设为红色
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 我的位置按钮（蓝色）
                Surface(
                    modifier = Modifier
                        .size(width = 56.dp, height = 56.dp) // 定义按钮尺寸
                        .shadow(4.dp, RoundedCornerShape(12.dp)) // 添加阴影和圆角
                        .clip(RoundedCornerShape(12.dp)) // 裁剪成圆角矩形
                        .clickable {
                            userLocation?.let { location ->
                                val userLatLng = createLatLng(location.latitude, location.longitude)
                                // 只移动镜头到用户位置，不改变其他状态
                                aMap?.let { animateCamera(it, userLatLng, zoomLevel) }
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
                        Icon(
                            imageVector = Icons.Filled.MyLocation, // 保持原有图标
                            contentDescription = "定位到当前位置",
                            tint = ComposeColor(0xFF1E90FF), // 图标颜色设为蓝色
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(1.5.dp)) // 图标和文字间距
                        Text(
                            text = "我的",
                            color = ComposeColor(0xFF1E90FF), // 文字颜色设为蓝色
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                        
                        // --- Use newLatLngBoundsRect for specific padding ---
                        val newLatLngBoundsRectMethod = cameraUpdateFactoryClass.getMethod(
                            "newLatLngBoundsRect", 
                            bounds.javaClass, 
                            Int::class.java, // paddingLeft
                            Int::class.java, // paddingTop
                            Int::class.java, // paddingRight
                            Int::class.java  // paddingBottom
                        )
                        val paddingLeft = 200
                        val paddingTop = 300 // Reduced top padding
                        val paddingRight = 200
                        val paddingBottom = 1000 // Further increased bottom padding
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
                        aMap?.let { animateCamera(it, epicenter, 9f) } // Fallback
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

        // 显示当前选中的地震信息卡片 (如果选中且无当前影响, 且选中的是模拟或近7天的)
        AnimatedVisibility(
             visible = localSelectedEarthquake != null && currentImpact == null &&
                     (localSelectedEarthquake?.id?.startsWith("simulated-") == true || (localSelectedEarthquake?.time?.time ?: 0) >= sevenDaysAgoMillis),
             enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
             exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
             modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 95.dp, start = 16.dp, end = 16.dp)
        ) {
            localSelectedEarthquake?.let { eq ->
                // 查找影响数据时，如果是模拟的，直接用 selectedImpact，否则从 recentSignificantEarthquakes 查找
                val impactToShow = if (eq.id.startsWith("simulated-")) {
                     selectedImpact // Should be the simulated impact
                 } else {
                     recentSignificantEarthquakes.find { it.earthquake.id == eq.id }
                 }

                if (impactToShow != null){
                     EarthquakeInfoCardCompact(
                         earthquake = eq,
                         impact = impactToShow,
                         onClose = {
                              localSelectedEarthquake = null
                              // Also clear selectedImpact if it matches the closed earthquake, unless it's the current real impact
                              if (selectedImpact?.earthquake?.id == eq.id && currentImpact?.earthquake?.id != eq.id) {
                                   selectedImpact = null
                              }
                         }
                     )
                 } else {
                     // 只显示简化卡片或日志，因为没有找到对应的近期重要影响数据
                    Log.d("EarthquakeAMap", "Selected recent earthquake ${eq.title} has no matching recent significant impact data to show full card.")
                    // Optionally show a simplified card here:
                    // SimplifiedEarthquakeCard(earthquake = eq, onClose = { localSelectedEarthquake = null })
                 }
            }
        }

        // 显示地震信息卡片 (显示当前影响的地震，或者由模拟地震触发的)
        // No change needed here as currentImpact is implicitly recent, and simulated impact doesn't need filtering.
        AnimatedVisibility(
            visible = selectedImpact != null && (selectedImpact?.earthquake?.id == currentImpact?.earthquake?.id || selectedImpact?.earthquake?.id?.startsWith("simulated-") == true),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 95.dp, start = 16.dp, end = 16.dp)
        ) {
             selectedImpact?.let { impact ->
                 EarthquakeInfoCardCompact(
                     earthquake = impact.earthquake,
                     impact = impact,
                     onClose = {
                         // Closing the card for the *current* real impact shouldn't clear the impact itself,
                         // maybe just hide the card? Or perhaps this visibility logic needs refinement.
                         // For now, closing a simulated impact card will clear both states.
                         if (impact.earthquake.id.startsWith("simulated-")) {
                              localSelectedEarthquake = null // Clear simulated selection
                              selectedImpact = null      // Clear simulated impact
                         } else if (impact.earthquake.id == currentImpact?.earthquake?.id) {
                              // If closing the card for the current real impact, maybe only clear localSelectedEarthquake?
                              // Depends on desired behavior. Let's clear local for now.
                              if (localSelectedEarthquake?.id == impact.earthquake.id) {
                                   localSelectedEarthquake = null
                              }
                              // Do NOT clear selectedImpact if it's the currentImpact
                         }
                     }
                 )
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
                .padding(horizontal = 12.dp, vertical = 12.dp)
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
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = earthquake.location.place,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2, // 允许最多两行
                        lineHeight = 18.sp // 行高设置紧凑一些
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
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                when {
                                    earthquake.magnitude >= 6.0 -> ComposeColor(android.graphics.Color.rgb(239, 83, 80)) // 柔和红色 (6级及以上)
                                    earthquake.magnitude >= 5.0 -> ComposeColor(android.graphics.Color.rgb(255, 167, 38)) // 柔和橙色 (5-5.9级)
                                    earthquake.magnitude >= 4.0 -> ComposeColor(android.graphics.Color.rgb(255, 220, 79)) // 更暗的黄色 (4-4.9级)
                                    else -> ComposeColor(android.graphics.Color.rgb(129, 199, 132)) // 柔和绿色 (小于4级)
                                }, 
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${earthquake.magnitude}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 预估震感列
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(0.4f)
                ) {
                    Text(
                        text = "预估震感",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "${getIntensityText(impact.intensity)}",
                        color = getIntensityColorNew(impact.intensity),
                        style = MaterialTheme.typography.titleLarge,
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
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 位置信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
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
                        .padding(start = 4.dp)
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
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 距离信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
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
                        .padding(start = 4.dp)
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
                    
                    // 使用 InfoRowCompact 替换原来的 Box 布局
                    InfoRowCompact(
                        icon = Icons.Filled.Timer,
                        label = "预计到达时间",
                        value = if (remainingSeconds > 0) "${remainingSeconds}秒后" else "已到达",
                        valueColor = ComposeColor(0xFFE57373), // Pass the specific red color for value
                        iconTint = ComposeColor(0xFFE57373) // Pass the specific red color for icon
                    )
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
 *
 * @param modifier Modifier to be applied to the Row container (use for alignment in parent).
 * @param panelBackgroundColor 背景颜色.
 * @param handleIcon Handle icon when collapsed.
 * @param handleExpandedIcon Handle icon when expanded.
 * @param handleBackgroundColor 背景颜色.
 * @param handleContentColor icon 颜色.
 * @param buttonContainerColor Background for buttons inside panel
 * @param buttonContentColor Black text for buttons
 * @param onSimulate Callback when the simulate button is clicked.
 * @param onCancel Callback when the cancel button is clicked.
 */
@Composable
fun CollapsibleControlPanel(
    modifier: Modifier = Modifier,
    panelBackgroundColor: ComposeColor = ComposeColor.White.copy(alpha = 0.8f),
    handleIcon: ImageVector = Icons.Default.ChevronRight,
    handleExpandedIcon: ImageVector = Icons.Default.ChevronLeft,
    handleBackgroundColor: ComposeColor = ComposeColor(0xFF68C29F),
    handleContentColor: ComposeColor = ComposeColor.White,
    buttonContainerColor: ComposeColor = ComposeColor(0xFF68C29F),
    buttonContentColor: ComposeColor = ComposeColor.Black,
    onSimulate: () -> Unit,
    onCancel: () -> Unit
) {
    var isPanelExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Handle (Icon Button)
        IconButton(
            onClick = { isPanelExpanded = !isPanelExpanded },
            modifier = Modifier
                .padding(end = if (isPanelExpanded) 4.dp else 0.dp)
                .background(handleBackgroundColor, CircleShape)
        ) {
            Icon(
                imageVector = if (isPanelExpanded) handleExpandedIcon else handleIcon,
                contentDescription = if (isPanelExpanded) "隐藏控制面板" else "显示控制面板",
                tint = handleContentColor
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