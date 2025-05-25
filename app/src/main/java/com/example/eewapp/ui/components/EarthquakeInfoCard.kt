package com.example.eewapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.ui.theme.Accent
import com.example.eewapp.ui.theme.AlertCritical
import com.example.eewapp.ui.theme.AlertHigh
import com.example.eewapp.ui.theme.AlertLow
import com.example.eewapp.ui.theme.AlertMedium
import com.example.eewapp.ui.theme.CardBackground
import com.example.eewapp.ui.theme.Primary
import com.example.eewapp.ui.theme.Secondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

// 定义颜色常量
private val RedEmphasis = Color(0xFFD32F2F) // 红色强调色
private val TextPrimary = Color.Black // 主要文本颜色
private val TextSecondary = Color.DarkGray // 次要文本颜色
private val BackgroundPrimary = Color.White // 主要背景色
private val WarningBackground = Color(0xFFFFF3E0) // 警告背景色
private val SkyBlue = Color(0xFF2196F3) // 天蓝色
private val TenderGreen = Color(0xFF4CAF50) // 嫩绿色
private val OrangeYellow = Color(0xFFFFC107) // 橙黄色
private val RedOrange = Color(0xFFFF6F00) // 修改红橙色，使其更偏橙

/**
 * 地震信息卡片
 */
@Composable
fun EarthquakeInfoCard(
    earthquake: Earthquake,
    impact: EarthquakeImpact,
    onClose: () -> Unit,
    onEscapeNavigation: (() -> Unit)? = null
) {
    val backgroundColor = Color.White
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = TextPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.LightGray.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            // 地震位置和震级信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.6f)
                        .padding(end = 12.dp)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "震中位置",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = earthquake.location.place,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 1.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .weight(1.6f)
                        .fillMaxHeight()
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "预警震级",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(getMagnitudeColor(earthquake.magnitude), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${earthquake.magnitude}",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp)) // 调整间距，之前是6，移除了横向分割线后稍微加大一点
            
            // 预计到达时间 和 预估震感 (带竖直分隔线)
            var remainingSeconds by remember { mutableStateOf(impact.secondsUntilArrival) }
            
            LaunchedEffect(impact.estimatedArrivalTime) {
                while (remainingSeconds > 0) {
                    delay(1000)
                    val currentTime = System.currentTimeMillis()
                    val newRemainingSeconds = ((impact.estimatedArrivalTime - currentTime) / 1000).toInt()
                    remainingSeconds = maxOf(0, newRemainingSeconds)
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(IntrinsicSize.Min), // 确保Divider能正确填充高度
                verticalAlignment = Alignment.CenterVertically 
            ) {
                // 左侧: 预计到达时间
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start 
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer, 
                            contentDescription = "预计到达时间",
                            tint = RedEmphasis,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "预计到达时间",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (remainingSeconds > 0) "${remainingSeconds} 秒后" else "已到达",
                                color = RedEmphasis,
                                fontSize = 16.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Divider(
                    color = Color.LightGray.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxHeight(0.8f) // 填充80%的高度，避免完全顶到边
                        .width(0.8.dp)
                )

                // 右侧: 预估震感
                Column(
                    modifier = Modifier.weight(1f).padding(start = 8.dp), // 给右侧内容加一点左边距
                    horizontalAlignment = Alignment.Start 
                ) {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning, 
                            contentDescription = "预估震感",
                            tint = getIntensityColor(impact.intensity),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "预估震感",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getIntensityText(impact.intensity),
                                color = getIntensityColor(impact.intensity),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp)) // 调整间距
            
            // 地震详细信息 (带竖直分隔线)
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min), // 确保Divider能正确填充高度
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    InfoRow(
                        icon = Icons.Default.LocationOn,
                        label = "震中坐标",
                        value = "东经${String.format("%.1f", earthquake.location.longitude)}° 北纬${String.format("%.1f", earthquake.location.latitude)}°",
                        modifier = Modifier.weight(1f).padding(end = 8.dp), // 增加右边距给分隔线空间
                        iconTint = RedOrange 
                    )
                    Divider(
                        color = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxHeight(0.9f) // 稍微调整填充高度
                            .width(0.8.dp)
                    )
                    InfoRow(
                        icon = Icons.Default.Place,
                        label = "震中距离",
                        value = "${impact.distanceFromUser.toInt()} 公里",
                        modifier = Modifier.weight(1f).padding(start = 8.dp), // 增加左边距
                        iconTint = SkyBlue
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp)) // 稍微加大行间距
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min), // 确保Divider能正确填充高度
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    InfoRow(
                        icon = Icons.Default.Info,
                        label = "震源深度",
                        value = "${earthquake.depth.toInt()} 公里",
                        modifier = Modifier.weight(1f).padding(end = 8.dp), // 增加右边距
                        iconTint = TenderGreen
                    )
                    Divider(
                        color = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxHeight(0.9f) // 稍微调整填充高度
                            .width(0.8.dp)
                    )
                    InfoRow(
                        icon = Icons.Default.Schedule,
                        label = "发震时间",
                        value = formatDate(earthquake.time),
                        modifier = Modifier.weight(1f).padding(start = 8.dp), // 增加左边距
                        iconTint = OrangeYellow
                    )
                }
            }
            
            // 逃生导航按钮区域（仅在模拟地震时显示）
            if (earthquake.id.startsWith("simulated-") && onEscapeNavigation != null) {
                Spacer(modifier = Modifier.height(6.dp)) // 进一步从8dp减少到6dp
                
                Button(
                    onClick = onEscapeNavigation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp), // 从42dp减少到36dp
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp), // 从10dp减少到8dp
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp) // 从3dp减少到2dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "逃生导航",
                            modifier = Modifier.size(16.dp) // 从18dp减少到16dp
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp)) // 从6dp减少到4dp
                        
                        Text(
                            text = "逃生导航",
                            fontSize = 13.sp, // 从14sp减少到13sp
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp)) // 从6dp减少到4dp
                        
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = "紧急逃生",
                            modifier = Modifier.size(16.dp) // 从18dp减少到16dp
                        )
                    }
                }
                
                // 提示文本
                Text(
                    text = "💡 查找附近安全避难场所",
                    fontSize = 10.sp, // 从11sp减少到10sp
                    color = Color(0xFF2196F3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp) // 从6dp减少到4dp
                )
            }
        }
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    iconTint: Color = TextSecondary // 新增 iconTint 参数，默认为 TextSecondary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint, // 使用传入的 iconTint
            modifier = Modifier
                .size(14.dp)
                .padding(top = 1.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 12.sp
            )
            
            Text(
                text = value,
                color = valueColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp
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

// 获取地震烈度颜色
private fun getIntensityColor(intensity: ShakingIntensity): Color {
    return when (intensity.level) {
        in 0..2 -> Color(0xFF4CAF50) // 绿色
        in 3..4 -> Color(0xFFFF9800) // 橙色
        else -> RedEmphasis // 红色
    }
}

// 获取震感背景颜色
private fun getShakingBackgroundColor(intensity: ShakingIntensity): Color {
    return when (intensity.level) {
        in 0..2 -> Color(0xFF91DE91) // 柔和的浅绿色背景
        in 3..4 -> Color(0xFFEFBE63) // 柔和的浅橙色背景
        else -> Color(0xFFF3867D) // 柔和的浅红色背景
    }
}

// 获取震级颜色
private fun getMagnitudeColor(magnitude: Double): Color {
    return when {
        magnitude >= 6.0 -> Color(0xFFD32F2F) // 红色
        magnitude >= 5.0 -> Color(0xFFFF9800) // 橙色
        magnitude >= 4.0 -> Color(0xFFFFC107) // 黄色
        else -> Color(0xFF4CAF50) // 绿色
    }
}

/**
 * 格式化日期
 */
private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}

// 以下是新的设计风格的地震信息卡片

/**
 * 地震信息卡片 - 新的设计风格
 */
@Composable
fun EarthquakeInfoCardNew(
    impact: EarthquakeImpact,
    modifier: Modifier = Modifier
) {
    val earthquake = impact.earthquake
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // 创建一个渐变背景
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            CardBackground,
            CardBackground.copy(alpha = 0.9f)
        )
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 12.dp
        )
    ) {
        Box(
            modifier = Modifier
                .background(gradientBackground)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题区域：震中和预警信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "震中",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "预警震级",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "预估烈度",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 地震主要信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = earthquake.location.place,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Accent.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${earthquake.magnitude}",
                            color = Accent,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(getShakingColor(impact.intensity).copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${impact.intensity.level}",
                            color = getShakingColor(impact.intensity),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    thickness = 1.dp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 位置坐标
                InfoRowNew(
                    icon = Icons.Filled.LocationOn,
                    iconTint = Secondary,
                    iconBackgroundColor = Secondary.copy(alpha = 0.15f),
                    label = "位置坐标",
                    value = "东经${earthquake.location.longitude}度，北纬${earthquake.location.latitude}度"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 发震时刻
                InfoRowNew(
                    icon = Icons.Filled.Info,
                    iconTint = Primary,
                    iconBackgroundColor = Primary.copy(alpha = 0.15f),
                    label = "发震时刻",
                    value = dateFormat.format(earthquake.time)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 震中距离
                InfoRowNew(
                    icon = Icons.Filled.Place,
                    iconTint = Accent,
                    iconBackgroundColor = Accent.copy(alpha = 0.15f),
                    label = "震中距您",
                    value = "${impact.distanceFromUser.toInt()}km"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 预警时间
                InfoRowNew(
                    icon = Icons.Filled.Notifications,
                    iconTint = AlertHigh,
                    iconBackgroundColor = AlertHigh.copy(alpha = 0.15f),
                    label = "预警时间",
                    value = "${impact.secondsUntilArrival}秒"
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 数据来源
                Text(
                    text = "信息来自 中国地震预警网",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                // 震感描述
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(getShakingColor(impact.intensity).copy(alpha = 0.1f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getDetailedShakingDescription(impact.intensity),
                        color = getShakingColor(impact.intensity),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 现代化信息行
 */
@Composable
private fun InfoRowNew(
    icon: ImageVector,
    iconTint: Color,
    iconBackgroundColor: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 图标容器
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBackgroundColor)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 根据震感强度返回描述文本
 */
private fun getShakingDescription(intensity: ShakingIntensity): String {
    return when {
        intensity.level >= 6 -> "确定"
        intensity.level >= 4 -> "较强"
        intensity.level >= 2 -> "可能"
        else -> "微弱"
    }
}

/**
 * 根据震感强度返回详细描述文本
 */
private fun getDetailedShakingDescription(intensity: ShakingIntensity): String {
    return when {
        intensity.level >= 7 -> "极强烈震感，可能会发生建筑物严重损坏，请立即寻找掩护"
        intensity.level >= 6 -> "强烈震感，室内物品可能掉落，请保持冷静并找掩护"
        intensity.level >= 4 -> "明显震感，大部分人能感觉到摇晃，请保持警惕"
        intensity.level >= 2 -> "轻微震感，部分人能感觉到轻微摇晃"
        else -> "微弱震感，几乎感觉不到"
    }
}

/**
 * 根据震感强度返回颜色
 */
private fun getShakingColor(intensity: ShakingIntensity): Color {
    return when {
        intensity.level >= 7 -> AlertCritical // 极危险红色
        intensity.level >= 5 -> AlertHigh // 橙色
        intensity.level >= 3 -> AlertMedium // 黄色
        else -> AlertLow // 绿色
    }
} 