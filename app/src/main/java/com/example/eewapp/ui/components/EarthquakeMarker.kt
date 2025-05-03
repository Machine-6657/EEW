package com.example.eewapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 震源标记组件
 */
@Composable
fun EarthquakeMarker(
    modifier: Modifier = Modifier,
    magnitude: Double,
    color: Color = Color(0xFFE53935) // 默认红色
) {
    // 根据震级调整大小
    val size = (40 + magnitude * 4).dp
    
    // 波纹动画
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )
    
    val rippleRadius2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 波纹效果
        Canvas(
            modifier = Modifier.size(size)
        ) {
            // 第一个波纹
            drawCircle(
                color = color.copy(alpha = (1f - rippleRadius) * 0.5f),
                radius = size.toPx() * rippleRadius / 2,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // 第二个波纹
            drawCircle(
                color = color.copy(alpha = (1f - rippleRadius2) * 0.5f),
                radius = size.toPx() * rippleRadius2 / 2,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // 中心点
        Box(
            modifier = Modifier
                .size(size / 3)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * 多重波纹效果
 */
@Composable
fun EarthquakeRippleEffect(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE53935), // 默认红色
    size: Float = 200f,
    rippleCount: Int = 3
) {
    val ripples = List(rippleCount) { index ->
        remember { Animatable(initialValue = 0f) }
    }
    
    // 为每个波纹创建单独的LaunchedEffect
    for (index in 0 until rippleCount) {
        val animatable = ripples[index]
        LaunchedEffect(key1 = animatable, key2 = index) {
            // 错开启动时间
            kotlinx.coroutines.delay(index * (3000L / rippleCount))
            
            // 循环动画
            while (true) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 3000,
                        easing = LinearEasing
                    )
                )
                animatable.snapTo(0f)
            }
        }
    }
    
    Canvas(modifier = modifier.size(size.dp)) {
        ripples.forEach { animatable ->
            val radius = animatable.value * size / 2
            val alpha = (1f - animatable.value) * 0.5f
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // 中心实心圆
        drawCircle(
            color = color,
            radius = size / 10
        )
    }
} 