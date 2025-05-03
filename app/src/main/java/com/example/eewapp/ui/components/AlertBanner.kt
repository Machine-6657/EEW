package com.example.eewapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.ui.theme.AlertCritical
import com.example.eewapp.ui.theme.AlertHigh
import com.example.eewapp.ui.theme.AlertLow
import com.example.eewapp.ui.theme.AlertMedium
import com.example.eewapp.ui.theme.AlertNone
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 地震警报横幅
 */
@Composable
fun AlertBanner(
    isActive: Boolean,
    impact: EarthquakeImpact?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isActive && impact != null,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(durationMillis = 300)
        ) + fadeIn(initialAlpha = 0.3f),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut()
    ) {
        if (impact != null) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = getAlertColor(impact.intensity).copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 警报标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingWarningIcon(impact.intensity)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getAlertTitle(impact.intensity),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 地震信息
                    Text(
                        text = "震级 ${impact.earthquake.magnitude} 地震",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    
                    Text(
                        text = impact.earthquake.location.place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 倒计时
                    CountdownTimer(secondsRemaining = impact.secondsUntilArrival)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 预计震感
                    Text(
                        text = "预计震感: ${impact.intensity.description}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = getAlertColor(impact.intensity)
                        )
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

/**
 * 脉动警告图标
 */
@Composable
fun PulsingWarningIcon(intensity: ShakingIntensity) {
    val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            tint = getAlertColor(intensity),
            modifier = Modifier.scale(scale)
        )
    }
}

/**
 * 倒计时组件
 */
@Composable
fun CountdownTimer(secondsRemaining: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "预计到达时间",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        
        Text(
            text = formatTime(secondsRemaining),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 32.sp
        )
    }
}

/**
 * 格式化时间
 */
private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    
    return if (minutes > 0) {
        "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    } else {
        "$remainingSeconds 秒"
    }
}

/**
 * 获取警报颜色
 */
private fun getAlertColor(intensity: ShakingIntensity): Color {
    return when (intensity.level) {
        0, 1 -> AlertNone
        2, 3 -> AlertLow
        4, 5 -> AlertMedium
        6 -> AlertHigh
        7 -> AlertCritical
        else -> AlertMedium
    }
}

/**
 * 获取警报标题
 */
private fun getAlertTitle(intensity: ShakingIntensity): String {
    return when (intensity.level) {
        0, 1 -> "地震信息"
        2, 3 -> "轻微地震警报"
        4, 5 -> "中等地震警报"
        6 -> "强烈地震警报"
        7 -> "剧烈地震警报"
        else -> "地震警报"
    }
} 