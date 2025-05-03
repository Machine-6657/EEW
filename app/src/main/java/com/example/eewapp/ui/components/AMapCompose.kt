package com.example.eewapp.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.lang.reflect.Method

/**
 * 高德地图的Compose包装器
 */
@Composable
fun AMapCompose(
    modifier: Modifier = Modifier,
    onMapLoaded: (Any) -> Unit = {},
    onMapClick: (Any) -> Unit = {},
    content: (@Composable (Any) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 创建MapView和AMap
    val mapViewAndMap = remember {
        createMapViewWithAMap(context)
    }
    
    // 处理生命周期事件
    DisposableEffect(lifecycleOwner) {
        val mapView = mapViewAndMap.first
        val aMap = mapViewAndMap.second
        
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> callMethod(mapView, "onCreate", arrayOf<Class<*>>(android.os.Bundle::class.java), arrayOf<Any?>(null))
                Lifecycle.Event.ON_RESUME -> callMethod(mapView, "onResume")
                Lifecycle.Event.ON_PAUSE -> callMethod(mapView, "onPause")
                Lifecycle.Event.ON_DESTROY -> callMethod(mapView, "onDestroy")
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // 设置地图点击监听器
        try {
            val listenerClass = Class.forName("com.amap.api.maps.AMap\$OnMapClickListener")
            val listenerInstance = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onMapClick" && args.size == 1) {
                    onMapClick(args[0])
                }
                null
            }
            
            val setOnMapClickListenerMethod = aMap.javaClass.getMethod("setOnMapClickListener", listenerClass)
            setOnMapClickListenerMethod.invoke(aMap, listenerInstance)
        } catch (e: Exception) {
            Log.e("AMapCompose", "设置地图点击监听器失败", e)
        }
        
        // 地图加载完成回调
        try {
            val listenerClass = Class.forName("com.amap.api.maps.AMap\$OnMapLoadedListener")
            val listenerInstance = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, _ ->
                if (method.name == "onMapLoaded") {
                    onMapLoaded(aMap)
                }
                null
            }
            
            val setOnMapLoadedListenerMethod = aMap.javaClass.getMethod("setOnMapLoadedListener", listenerClass)
            setOnMapLoadedListenerMethod.invoke(aMap, listenerInstance)
        } catch (e: Exception) {
            Log.e("AMapCompose", "设置地图加载完成监听器失败", e)
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 渲染地图
    AndroidView(
        factory = { mapViewAndMap.first as android.view.View },
        modifier = modifier
    )
    
    // 渲染内容
    content?.invoke(mapViewAndMap.second)
}

/**
 * 创建MapView和AMap对象
 */
private fun createMapViewWithAMap(context: Context): Pair<Any, Any> {
    try {
        // 创建MapView
        val mapViewClass = Class.forName("com.amap.api.maps.MapView")
        val mapView = mapViewClass.getConstructor(Context::class.java).newInstance(context)
        
        // 获取AMap对象
        val getMapMethod = mapViewClass.getMethod("getMap")
        val aMap = getMapMethod.invoke(mapView)
        
        // 禁用高德地图默认的自动定位和视角移动功能
        try {
            // 禁用地图的自动定位模式
            val setMyLocationEnabledMethod = aMap.javaClass.getMethod("setMyLocationEnabled", Boolean::class.java)
            setMyLocationEnabledMethod.invoke(aMap, false)
            
            // 禁用指南针
            val getUiSettingsMethod = aMap.javaClass.getMethod("getUiSettings")
            val uiSettings = getUiSettingsMethod.invoke(aMap)
            val setCompassEnabledMethod = uiSettings.javaClass.getMethod("setCompassEnabled", Boolean::class.java)
            setCompassEnabledMethod.invoke(uiSettings, false)
            
            // 禁用缩放控制
            val setZoomControlsEnabledMethod = uiSettings.javaClass.getMethod("setZoomControlsEnabled", Boolean::class.java)
            setZoomControlsEnabledMethod.invoke(uiSettings, false)
            
            // 设置地图不跟随位置移动
            val setMyLocationType = aMap.javaClass.getMethod("setMyLocationType", Int::class.java)
            setMyLocationType.invoke(aMap, 0) // 0表示不跟随移动
        } catch (e: Exception) {
            Log.e("AMapCompose", "禁用自动定位和视角移动功能失败", e)
        }
        
        return Pair(mapView, aMap)
    } catch (e: Exception) {
        Log.e("AMapCompose", "创建MapView和AMap对象失败", e)
        throw e
    }
}

/**
 * 在地图上添加标记
 */
@Composable
fun AMapMarker(
    aMap: Any,
    position: Any,
    title: String? = null,
    snippet: String? = null,
    iconResourceId: Int? = null,
    markerColor: Int = android.graphics.Color.RED,
    onClick: ((Any) -> Boolean)? = null
) {
    // 保存对象的引用，以便后续清理
    val markerRef = remember { mutableListOf<Any?>() }
    
    DisposableEffect(position, title, snippet, iconResourceId, markerColor) {
        var marker: Any? = null
        
        try {
            // 创建标记选项
            val markerOptionsClass = Class.forName("com.amap.api.maps.model.MarkerOptions")
            val markerOptions = markerOptionsClass.newInstance()
            
            // 设置位置
            val positionMethod = markerOptionsClass.getMethod("position", position.javaClass)
            positionMethod.invoke(markerOptions, position)
            
            // 设置标题和描述（如果有）
            if (title != null) {
                val titleMethod = markerOptionsClass.getMethod("title", String::class.java)
                titleMethod.invoke(markerOptions, title)
            }
            
            if (snippet != null) {
                val snippetMethod = markerOptionsClass.getMethod("snippet", String::class.java)
                snippetMethod.invoke(markerOptions, snippet)
            }
            
            // 设置图标（如果有）
            try {
                val bitmapDescriptorFactoryClass = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory")
                
                if (iconResourceId != null) {
                    // 使用资源图标
                    val fromResourceMethod = bitmapDescriptorFactoryClass.getMethod("fromResource", Int::class.java)
                    val icon = fromResourceMethod.invoke(null, iconResourceId)
                    
                    val iconMethod = markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor"))
                    iconMethod.invoke(markerOptions, icon)
                } else {
                    // 使用默认图标但应用颜色
                    val defaultIconMethod = bitmapDescriptorFactoryClass.getMethod("defaultMarker", Float::class.java)
                    
                    // 将RGB颜色转换为HSV色相值
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(markerColor, hsv)
                    val hue = hsv[0] // 提取色相值，高德地图使用色相来设置标记颜色
                    
                    val icon = defaultIconMethod.invoke(null, hue)
                    
                    val iconMethod = markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor"))
                    iconMethod.invoke(markerOptions, icon)
                }
            } catch (e: Exception) {
                Log.e("AMapMarker", "设置图标失败", e)
                // 使用默认图标
            }
            
            // 添加标记
            val addMarkerMethod = aMap.javaClass.getMethod("addMarker", markerOptionsClass)
            marker = addMarkerMethod.invoke(aMap, markerOptions)
            markerRef.add(marker)
            
            // 设置点击监听器（如果有）
            if (onClick != null) {
                val listenerClass = Class.forName("com.amap.api.maps.AMap\$OnMarkerClickListener")
                val listenerInstance = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onMarkerClick" && args.size == 1) {
                        val clickedMarker = args[0]
                        if (clickedMarker == marker) {
                            onClick(clickedMarker)
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                
                val setOnMarkerClickListenerMethod = aMap.javaClass.getMethod("setOnMarkerClickListener", listenerClass)
                setOnMarkerClickListenerMethod.invoke(aMap, listenerInstance)
            }
        } catch (e: Exception) {
            Log.e("AMapMarker", "添加标记失败", e)
        }
        
        onDispose {
            try {
                markerRef.forEach { marker ->
                    if (marker != null) {
                        val removeMethod = marker.javaClass.getMethod("remove")
                        removeMethod.invoke(marker)
                    }
                }
                markerRef.clear()
            } catch (e: Exception) {
                Log.e("AMapMarker", "移除标记失败", e)
            }
        }
    }
}

/**
 * 在地图上添加圆形
 */
@Composable
fun AMapCircle(
    aMap: Any,
    center: Any,
    radius: Double,
    strokeWidth: Float = 5f,
    strokeColor: Int = Color.RED,
    fillColor: Int = Color.argb(50, 255, 0, 0),
    zoomDependent: Boolean = false // 新增参数，控制是否随缩放级别调整大小
) {
    // 保存对象的引用，以便后续清理
    val circleRef = remember { mutableListOf<Any?>() }
    
    DisposableEffect(center, radius, zoomDependent) {
        var circle: Any? = null
        var zoomChangeListener: Any? = null
        
        try {
            // 创建圆形选项
            val circleOptionsClass = Class.forName("com.amap.api.maps.model.CircleOptions")
            val circleOptions = circleOptionsClass.newInstance()
            
            // 设置中心点
            val centerMethod = circleOptionsClass.getMethod("center", center.javaClass)
            centerMethod.invoke(circleOptions, center)
            
            // 设置半径
            val radiusMethod = circleOptionsClass.getMethod("radius", Double::class.java)
            radiusMethod.invoke(circleOptions, radius)
            
            // 设置边框宽度和颜色
            try {
                val strokeWidthMethod = circleOptionsClass.getMethod("strokeWidth", Float::class.java)
                strokeWidthMethod.invoke(circleOptions, strokeWidth)
                
                val strokeColorMethod = circleOptionsClass.getMethod("strokeColor", Int::class.java)
                strokeColorMethod.invoke(circleOptions, strokeColor)
                
                val fillColorMethod = circleOptionsClass.getMethod("fillColor", Int::class.java)
                fillColorMethod.invoke(circleOptions, fillColor)
            } catch (e: Exception) {
                Log.e("AMapCircle", "设置圆形样式失败", e)
                // 使用默认样式
            }
            
            // 添加圆形
            val addCircleMethod = aMap.javaClass.getMethod("addCircle", circleOptionsClass)
            circle = addCircleMethod.invoke(aMap, circleOptions)
            circleRef.add(circle)
            
            // 如果需要根据缩放级别调整大小
            if (zoomDependent) {
                // 获取当前缩放级别
                val getCameraPositionMethod = aMap.javaClass.getMethod("getCameraPosition")
                val cameraPosition = getCameraPositionMethod.invoke(aMap)
                val getZoomMethod = cameraPosition.javaClass.getMethod("getZoom")
                val initialZoom = getZoomMethod.invoke(cameraPosition) as Float
                
                // 计算基准缩放级别下的大小
                val baseZoom = 10.0f // 基准缩放级别
                val baseRadius = radius // 基准缩放级别下的半径
                
                // 根据当前缩放级别调整圆的大小
                adjustCircleSize(circle, initialZoom, baseZoom, baseRadius)
                
                // 添加缩放级别变化监听器
                try {
                    val onCameraChangeListenerClass = Class.forName("com.amap.api.maps.AMap\$OnCameraChangeListener")
                    zoomChangeListener = java.lang.reflect.Proxy.newProxyInstance(
                        onCameraChangeListenerClass.classLoader,
                        arrayOf(onCameraChangeListenerClass)
                    ) { _, method, args ->
                        when (method.name) {
                            "onCameraChange" -> {
                                val newCameraPosition = args[0]
                                val newZoom = getZoomMethod.invoke(newCameraPosition) as Float
                                adjustCircleSize(circle, newZoom, baseZoom, baseRadius)
                                null
                            }
                            "onCameraChangeFinish" -> {
                                val newCameraPosition = args[0]
                                val newZoom = getZoomMethod.invoke(newCameraPosition) as Float
                                adjustCircleSize(circle, newZoom, baseZoom, baseRadius)
                                null
                            }
                            else -> null
                        }
                    }
                    
                    val addOnCameraChangeListenerMethod = aMap.javaClass.getMethod("setOnCameraChangeListener", onCameraChangeListenerClass)
                    addOnCameraChangeListenerMethod.invoke(aMap, zoomChangeListener)
                } catch (e: Exception) {
                    Log.e("AMapCircle", "设置缩放监听器失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AMapCircle", "添加圆形失败", e)
        }
        
        onDispose {
            try {
                // 移除缩放监听器
                if (zoomChangeListener != null) {
                    try {
                        val setOnCameraChangeListenerMethod = aMap.javaClass.getMethod("setOnCameraChangeListener", Class.forName("com.amap.api.maps.AMap\$OnCameraChangeListener"))
                        setOnCameraChangeListenerMethod.invoke(aMap, null)
                    } catch (e: Exception) {
                        Log.e("AMapCircle", "移除缩放监听器失败", e)
                    }
                }
                
                // 移除圆形
                circleRef.forEach { circle ->
                    if (circle != null) {
                        val removeMethod = circle.javaClass.getMethod("remove")
                        removeMethod.invoke(circle)
                    }
                }
                circleRef.clear()
            } catch (e: Exception) {
                Log.e("AMapCircle", "移除圆形失败", e)
            }
        }
    }
}

/**
 * 根据缩放级别调整圆的大小
 */
private fun adjustCircleSize(circle: Any?, zoom: Float, baseZoom: Float, baseRadius: Double) {
    try {
        if (circle != null) {
            // 计算缩放因子: 缩放级别越大（越近），圆应该越小
            // 使用指数为0.5的幂函数，提供更强的缩放效果
            // 在近距离(zoom大)时圆更小，远距离(zoom小)时圆更大
            val scaleFactor = Math.pow(0.5, (zoom - baseZoom).toDouble()).coerceIn(0.1, 10.0)
            
            // 根据缩放级别计算新半径
            val newRadius = baseRadius * scaleFactor
            
            // 设置新半径
            val setRadiusMethod = circle.javaClass.getMethod("setRadius", Double::class.java)
            setRadiusMethod.invoke(circle, newRadius)
        }
    } catch (e: Exception) {
        Log.e("AMapCircle", "调整圆大小失败", e)
    }
}

/**
 * 在地图上添加可点击的圆形
 */
@Composable
fun AMapCircleClickable(
    aMap: Any,
    center: Any,
    radius: Double,
    strokeWidth: Float = 5f,
    strokeColor: Int = Color.RED,
    fillColor: Int = Color.argb(50, 255, 0, 0),
    onClick: (() -> Boolean)? = null
) {
    // 保存对象的引用，以便后续清理
    val circleRef = remember { mutableListOf<Any?>() }
    
    DisposableEffect(center, radius) {
        var circle: Any? = null
        
        try {
            // 创建圆形选项
            val circleOptionsClass = Class.forName("com.amap.api.maps.model.CircleOptions")
            val circleOptions = circleOptionsClass.newInstance()
            
            // 设置中心点
            val centerMethod = circleOptionsClass.getMethod("center", center.javaClass)
            centerMethod.invoke(circleOptions, center)
            
            // 设置半径
            val radiusMethod = circleOptionsClass.getMethod("radius", Double::class.java)
            radiusMethod.invoke(circleOptions, radius)
            
            // 设置边框宽度和颜色
            try {
                val strokeWidthMethod = circleOptionsClass.getMethod("strokeWidth", Float::class.java)
                strokeWidthMethod.invoke(circleOptions, strokeWidth)
                
                val strokeColorMethod = circleOptionsClass.getMethod("strokeColor", Int::class.java)
                strokeColorMethod.invoke(circleOptions, strokeColor)
                
                val fillColorMethod = circleOptionsClass.getMethod("fillColor", Int::class.java)
                fillColorMethod.invoke(circleOptions, fillColor)
            } catch (e: Exception) {
                Log.e("AMapCircleClickable", "设置圆形样式失败", e)
                // 使用默认样式
            }
            
            // 添加圆形
            val addCircleMethod = aMap.javaClass.getMethod("addCircle", circleOptionsClass)
            circle = addCircleMethod.invoke(aMap, circleOptions)
            circleRef.add(circle)
            
            // 设置点击监听器
            if (onClick != null) {
                // 创建点击监听器
                val mapClickListenerClass = Class.forName("com.amap.api.maps.AMap\$OnMapClickListener")
                val mapClickListener = java.lang.reflect.Proxy.newProxyInstance(
                    mapClickListenerClass.classLoader,
                    arrayOf(mapClickListenerClass)
                ) { _, method, args ->
                    if (method.name == "onMapClick" && args.size == 1) {
                        val clickedLatLng = args[0]
                        
                        // 获取点击位置的坐标
                        val latMethod = clickedLatLng.javaClass.getMethod("latitude")
                        val lngMethod = clickedLatLng.javaClass.getMethod("longitude")
                        val clickedLat = latMethod.invoke(clickedLatLng) as Double
                        val clickedLng = lngMethod.invoke(clickedLatLng) as Double
                        
                        // 获取圆心坐标
                        val circleCenter = circle?.javaClass?.getMethod("getCenter")?.invoke(circle)
                        val centerLat = latMethod.invoke(circleCenter) as Double
                        val centerLng = lngMethod.invoke(circleCenter) as Double
                        
                        // 计算点击位置与圆心的距离
                        val distance = calculateDistance(centerLat, centerLng, clickedLat, clickedLng) * 1000 // 转为米
                        
                        // 如果点击位置在圆内，触发onClick
                        if (distance <= radius) {
                            onClick()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                
                val setOnMapClickListenerMethod = aMap.javaClass.getMethod("setOnMapClickListener", mapClickListenerClass)
                setOnMapClickListenerMethod.invoke(aMap, mapClickListener)
            }
        } catch (e: Exception) {
            Log.e("AMapCircleClickable", "添加可点击圆形失败", e)
        }
        
        onDispose {
            try {
                circleRef.forEach { circle ->
                    if (circle != null) {
                        val removeMethod = circle.javaClass.getMethod("remove")
                        removeMethod.invoke(circle)
                    }
                }
                circleRef.clear()
            } catch (e: Exception) {
                Log.e("AMapCircleClickable", "移除圆形失败", e)
            }
        }
    }
}

/**
 * 计算两点间的距离（单位：公里）
 */
private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadius = 6371.0 // 地球半径，单位公里
    
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    
    return earthRadius * c
}

/**
 * 通用方法调用辅助函数
 */
private fun callMethod(obj: Any, methodName: String, paramTypes: Array<Class<*>> = emptyArray(), params: Array<Any?> = emptyArray()): Any? {
    return try {
        val method = obj.javaClass.getMethod(methodName, *paramTypes)
        method.invoke(obj, *params)
    } catch (e: Exception) {
        Log.e("AMapCompose", "调用方法失败: $methodName", e)
        null
    }
}

/**
 * 在地图上添加固定屏幕大小的标记点，无论地图缩放级别如何，标记点始终保持相同的视觉大小
 */
@Composable
fun AMapScreenMarker(
    aMap: Any,
    position: Any,
    color: Int,
    size: Float = 20f,
    outerRingSize: Float = 30f,
    outerRingAlpha: Int = 80,
    zIndex: Float = 1000f
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    
    // 计算实际像素大小
    val sizePx = size * density
    val outerRingSizePx = outerRingSize * density
    
    // 创建内圆和外圆的Bitmap
    val innerBitmap = remember(sizePx, color) {
        createCircleBitmap(sizePx.toInt(), color)
    }
    
    val outerColor = Color.argb(
        outerRingAlpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
    
    val outerBitmap = remember(outerRingSizePx, outerColor) {
        createCircleBitmap(outerRingSizePx.toInt(), outerColor)
    }
    
    // 保存对象的引用，以便后续清理
    val markerRefs = remember { mutableListOf<Any?>() }
    val cameraListenerRef = remember { mutableStateOf<Any?>(null) }
    
    DisposableEffect(position, color, size, outerRingSize, outerRingAlpha) {
        val markers = mutableListOf<Any?>()
        
        try {
            // 使用反射获取BitmapDescriptorFactory类
            val bitmapDescriptorFactoryClass = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory")
            
            // 创建内圆和外圆的BitmapDescriptor
            val fromBitmapMethod = bitmapDescriptorFactoryClass.getMethod("fromBitmap", Bitmap::class.java)
            val innerIcon = fromBitmapMethod.invoke(null, innerBitmap)
            val outerIcon = fromBitmapMethod.invoke(null, outerBitmap)
            
            // 创建内圆标记
            val markerOptionsClass = Class.forName("com.amap.api.maps.model.MarkerOptions")
            val innerMarkerOptions = markerOptionsClass.newInstance()
            
            // 设置内圆标记属性
            markerOptionsClass.getMethod("position", position.javaClass).invoke(innerMarkerOptions, position)
            markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor")).invoke(innerMarkerOptions, innerIcon)
            markerOptionsClass.getMethod("anchor", Float::class.java, Float::class.java).invoke(innerMarkerOptions, 0.5f, 0.5f)
            markerOptionsClass.getMethod("zIndex", Float::class.java).invoke(innerMarkerOptions, zIndex)
            
            // 添加内圆标记
            val innerMarker = aMap.javaClass.getMethod("addMarker", markerOptionsClass).invoke(aMap, innerMarkerOptions)
            markers.add(innerMarker)
            
            // 创建外圆标记
            val outerMarkerOptions = markerOptionsClass.newInstance()
            
            // 设置外圆标记属性
            markerOptionsClass.getMethod("position", position.javaClass).invoke(outerMarkerOptions, position)
            markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor")).invoke(outerMarkerOptions, outerIcon)
            markerOptionsClass.getMethod("anchor", Float::class.java, Float::class.java).invoke(outerMarkerOptions, 0.5f, 0.5f)
            markerOptionsClass.getMethod("zIndex", Float::class.java).invoke(outerMarkerOptions, zIndex - 1)
            
            // 添加外圆标记
            val outerMarker = aMap.javaClass.getMethod("addMarker", markerOptionsClass).invoke(aMap, outerMarkerOptions)
            markers.add(outerMarker)
            
            // 保存标记引用
            markerRefs.addAll(markers)
            
        } catch (e: Exception) {
            Log.e("AMapScreenMarker", "创建固定大小标记失败", e)
        }
        
        onDispose {
            try {
                // 移除所有标记
                markerRefs.forEach { marker ->
                    if (marker != null) {
                        marker.javaClass.getMethod("remove").invoke(marker)
                    }
                }
                markerRefs.clear()
                
                // 回收Bitmap
                innerBitmap.recycle()
                outerBitmap.recycle()
            } catch (e: Exception) {
                Log.e("AMapScreenMarker", "移除标记失败", e)
            }
        }
    }
}

/**
 * 创建圆形Bitmap
 */
private fun createCircleBitmap(size: Int, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    paint.color = color
    paint.style = Paint.Style.FILL
    
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    
    return bitmap
} 