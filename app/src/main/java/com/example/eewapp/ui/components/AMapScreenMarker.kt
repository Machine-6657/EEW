package com.example.eewapp.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 在地图上添加固定屏幕大小的标记点，具有内圈和外圈
 */
@Composable
fun AMapScreenMarker(
    aMap: Any,
    position: Any,
    color: Int,
    size: Float = 12f,  // 内圆大小（dp）
    outerRingSize: Float = 20f,  // 外圈大小（dp）
    outerRingAlpha: Int = 80  // 外圈透明度（0-255）
) {
    // 保存对象的引用，以便后续清理
    val markerRef = remember { mutableListOf<Any?>() }
    
    // 获取屏幕密度
    val density = LocalDensity.current.density
    
    DisposableEffect(position, color, size, outerRingSize, outerRingAlpha) {
        var marker: Any? = null
        
        try {
            // 创建自定义点标记图标
            val markerBitmap = createCircleMarkerBitmap(
                color = color,
                innerRadius = (size * density).toInt(),
                outerRadius = (outerRingSize * density).toInt(),
                outerRingAlpha = outerRingAlpha
            )
            
            // 创建BitmapDescriptor
            val bitmapDescriptorFactoryClass = Class.forName("com.amap.api.maps.model.BitmapDescriptorFactory")
            val fromBitmapMethod = bitmapDescriptorFactoryClass.getMethod("fromBitmap", Bitmap::class.java)
            val icon = fromBitmapMethod.invoke(null, markerBitmap)
            
            // 创建MarkerOptions
            val markerOptionsClass = Class.forName("com.amap.api.maps.model.MarkerOptions")
            val markerOptions = markerOptionsClass.newInstance()
            
            // 设置位置
            val positionMethod = markerOptionsClass.getMethod("position", position.javaClass)
            positionMethod.invoke(markerOptions, position)
            
            // 设置图标
            val iconMethod = markerOptionsClass.getMethod("icon", Class.forName("com.amap.api.maps.model.BitmapDescriptor"))
            iconMethod.invoke(markerOptions, icon)
            
            // 设置锚点为中心
            val anchorMethod = markerOptionsClass.getMethod("anchor", Float::class.java, Float::class.java)
            anchorMethod.invoke(markerOptions, 0.5f, 0.5f)
            
            // 添加标记
            val addMarkerMethod = aMap.javaClass.getMethod("addMarker", markerOptionsClass)
            marker = addMarkerMethod.invoke(aMap, markerOptions)
            markerRef.add(marker)
        } catch (e: Exception) {
            Log.e("AMapScreenMarker", "添加标记失败", e)
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
                Log.e("AMapScreenMarker", "移除标记失败", e)
            }
        }
    }
}

/**
 * 创建圆形标记位图，包含实心内圆和半透明外圈
 */
private fun createCircleMarkerBitmap(
    color: Int,
    innerRadius: Int,
    outerRadius: Int,
    outerRingAlpha: Int
): Bitmap {
    // 确保半径大于0且外圈半径大于等于内圈半径
    val safeInnerRadius = innerRadius.coerceAtLeast(1)
    val safeOuterRadius = outerRadius.coerceAtLeast(safeInnerRadius)
    
    // 创建位图，尺寸为外圈直径加上一些边距
    val padding = 4
    val size = (safeOuterRadius * 2) + (padding * 2)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // 创建绘制对象
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // 提取颜色的RGB分量
    val red = Color.red(color)
    val green = Color.green(color)
    val blue = Color.blue(color)
    
    // 绘制外圈（半透明）
    paint.color = Color.argb(outerRingAlpha, red, green, blue)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(
        size / 2f,
        size / 2f,
        safeOuterRadius.toFloat(),
        paint
    )
    
    // 绘制内圈（实心）
    paint.color = color
    paint.style = Paint.Style.FILL
    canvas.drawCircle(
        size / 2f,
        size / 2f,
        safeInnerRadius.toFloat(),
        paint
    )
    
    return bitmap
} 