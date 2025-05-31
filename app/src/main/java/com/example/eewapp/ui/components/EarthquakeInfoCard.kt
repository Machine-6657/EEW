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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import com.example.eewapp.R

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
    onEscapeNavigation: (() -> Unit)? = null,
    onNavigateToGuide: () -> Unit
) {
    // val backgroundColor = Color.White // 不再需要固定的白色背景
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent, // 1. 使卡片容器背景透明
            contentColor = TextPrimary // 保持默认内容颜色
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.LightGray.copy(alpha = 0.3f)
        )
    ) {
        var remainingSeconds by remember { mutableStateOf(impact.secondsUntilArrival) }
        LaunchedEffect(impact.estimatedArrivalTime) {
            while (remainingSeconds > 0) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                val newRemainingSeconds = ((impact.estimatedArrivalTime - currentTime) / 1000).toInt()
                remainingSeconds = maxOf(0, newRemainingSeconds)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 2. 为这个Column应用从上到下的渐变背景
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            getSoftMagnitudeColor(earthquake.magnitude), // 改为使用柔和颜色
                            Color.White                         // 底部：白色
                        )
                    )
                )
        ) {
            // 横幅 Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧和中间合并：预警震级 和 预计到达时间
                Column(
                    modifier = Modifier
                        .padding(end = 8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row( // 震级和到达时间在同一行
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "${String.format("%.1f", earthquake.magnitude)}级",
                            color = getMagnitudeColor(earthquake.magnitude),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        if (remainingSeconds > 0) {
                            Text(
                                text = "地震波于",
                                color = Color.White,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${remainingSeconds}",
                                color = getMagnitudeColor(earthquake.magnitude),
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "秒后到达",
                                color = Color.White,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "已到达",
                                color = Color.White, // "已到达" 也保持白色
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 新增一个Column来包裹横幅下方所有内容，并应用padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 修改padding，减小上内边距
                    .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)
            ) {
                Spacer(modifier = Modifier.height(0.dp)) // 这个Spacer保持0.dp

                // 原"预计到达时间 | 预估震感"行，左侧修改为"震中位置（地名）"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(IntrinsicSize.Min), // 保持IntrinsicSize.Min
                    verticalAlignment = Alignment.Top // 修改为 Alignment.Top
                ) {
                    // 左侧: 修改为显示"震中位置（地名）"
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) { // Icon 和 Label 在一行，垂直居中
                            Icon(
                                imageVector = Icons.Filled.TravelExplore,
                                contentDescription = stringResource(R.string.earthquake_info_card_epicenter_location_label),
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text( // Label: "震中位置"
                                text = stringResource(R.string.earthquake_info_card_epicenter_location_label),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp)) // 图标/标签行 与 下方地名之间的间距
                        Text( // Detail: 地名，现在直接在 Column 下，与 Icon 左对齐
                            text = earthquake.location.place,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Divider(
                        color = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxHeight(0.8f) // 保持分隔线高度
                            .width(0.8.dp)
                    )

                    // 右侧: 预估震感 (保持不变)
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                         Row(verticalAlignment = Alignment.CenterVertically) { // Icon 和 Label 在一行，垂直居中
                            Icon(
                                imageVector = Icons.Outlined.Troubleshoot,
                                contentDescription = "预估震感",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text( // Label: "预估震感"
                                text = "预估震感",
                                color = TextPrimary, // 注意：之前这里的标签颜色是 TextPrimary，保持一致
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp)) // 图标/标签行 与 下方震感描述之间的间距
                        Text( // Detail: 震感描述，现在直接在 Column 下，与 Icon 左对齐
                            text = getIntensityText(impact.intensity),
                            color = getIntensityColor(impact.intensity),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 地震详细信息 (带竖直分隔线)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        InfoRow(
                            label = "震中坐标",
                            value = "东经${String.format("%.1f", earthquake.location.longitude)}° 北纬${String.format("%.1f", earthquake.location.latitude)}°",
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Divider(
                            color = Color.LightGray.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxHeight(0.9f)
                                .width(0.8.dp)
                        )
                        InfoRow(
                            label = "震中距离",
                            value = "${impact.distanceFromUser.toInt()} 公里",
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        InfoRow(
                            label = "震源深度",
                            value = "${earthquake.depth.toInt()} 公里",
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Divider(
                            color = Color.LightGray.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxHeight(0.9f)
                                .width(0.8.dp)
                        )
                        InfoRow(
                            label = "发震时间",
                            value = formatDate(earthquake.time),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(
    icon: ImageVector? = null,
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    iconTint: Color = TextSecondary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(14.dp)
                    .padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        }
        
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
        in 0..2 -> "较弱震感，保持镇静"
        in 3..4 -> "中等震感，尽快逃生"
        else -> "强烈震感，尽快自救"
    }
}

// 获取地震烈度颜色
private fun getIntensityColor(intensity: ShakingIntensity): Color {
    return when (intensity.level) {
        in 0..2 -> Color(0xFF0dcc06) // 绿色
        in 3..4 -> Color(0xFFFE8001) // 橙色
        else -> Color(0xFFEE4432) // 红色
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
        magnitude >= 6.0 -> Color(0xFFEE4432) // 地震红色等级
        magnitude >= 5.0 -> Color(0xFFFE8001) // 地震橙色等级
        magnitude >= 4.0 -> Color(0xFFFFC200) // 地震黄色等级 
        else -> Color(0xFF68E864) // 绿色
    }
}

// 新增：获取柔和的震级颜色
private fun getSoftMagnitudeColor(magnitude: Double): Color {
    return when {
        magnitude >= 6.0 -> Color(0xFFF8A096) // 柔红
        magnitude >= 5.0 -> Color(0xFFFFB74D) // 柔橙
        magnitude >= 4.0 -> Color(0xFFFFF176) // 柔黄
        else -> Color(0xFFA5D6A7) // 柔绿
    }
}

/**
 * 格式化日期
 */
private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}

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

