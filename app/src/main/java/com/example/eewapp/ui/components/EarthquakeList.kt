package com.example.eewapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.ui.theme.AlertCritical
import com.example.eewapp.ui.theme.AlertHigh
import com.example.eewapp.ui.theme.AlertLow
import com.example.eewapp.ui.theme.AlertMedium
import com.example.eewapp.ui.theme.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A list of earthquakes
 */
@Composable
fun EarthquakeList(
    earthquakes: List<Earthquake>,
    significantEarthquakes: List<EarthquakeImpact>,
    onEarthquakeClick: (Earthquake) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color.White // 主要背景色
    val DividerColor = Color.LightGray.copy(alpha = 0.5f) // 分隔线颜色

    // --- START: 7-Day Filter Logic ---
    val sevenDaysAgoMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    val recentEarthquakes = earthquakes.filter { it.time.time >= sevenDaysAgoMillis }
    val recentSignificantEarthquakes = significantEarthquakes.filter { it.earthquake.time.time >= sevenDaysAgoMillis }
    // --- END: 7-Day Filter Logic ---

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "世界地震信息",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = RedEmphasis
                )
            }
        }
        
        // 检查是否有 *过滤后* 的数据
        if (recentEarthquakes.isEmpty()) {
            // 空状态UI
            EmptyStateContent(onRefresh = onRefresh, isLoading = isLoading)
        } else {
            // 有数据，显示正常内容
            // Significant earthquakes section (Use filtered list)
            if (recentSignificantEarthquakes.isNotEmpty()) {
                Text(
                    text = "重要地震 (近7天)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RedEmphasis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .height(200.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    items(recentSignificantEarthquakes) { impact ->
                        SignificantEarthquakeItem(
                            impact = impact,
                            onClick = { onEarthquakeClick(impact.earthquake) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = DividerColor
                )
            }
            
            // All earthquakes (Use filtered list)
            Text(
                text = "全部地震 (近7天)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(recentEarthquakes) { earthquake ->
                    EarthquakeItem(
                        earthquake = earthquake,
                        onClick = { onEarthquakeClick(earthquake) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * An item in the earthquake list
 */
@Composable
fun EarthquakeItem(
    earthquake: Earthquake,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color.White // 主要背景色
    val SichuanSourceColor = Color(0xFF1976D2) // 四川地震局数据来源标记颜色
    val ChinaSourceColor = Color(0xFF00796B) // 中国地震数据来源标记颜色
    val WorldSourceColor = Color(0xFF7B1FA2) // 世界地震数据来源标记颜色

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundPrimary
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Magnitude circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getMagnitudeColorNew(earthquake.magnitude)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%.1f", earthquake.magnitude),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // 地点名称
                val placeName = earthquake.location.place
                
                // 判断数据来源
                val isFromSichuan = earthquake.title.contains("[四川地震局]")
                val isFromChina = earthquake.title.contains("[中国]")
                val isFromWorld = earthquake.title.contains("[世界]")
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = placeName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (isFromSichuan) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SichuanSourceColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "四川",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = SichuanSourceColor
                            )
                        }
                    }
                    
                    if (isFromChina) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ChinaSourceColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "中国",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ChinaSourceColor
                            )
                        }
                    }
                    
                    if (isFromWorld) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(WorldSourceColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "世界",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = WorldSourceColor
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatDate(earthquake.time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * A significant earthquake item
 */
@Composable
fun SignificantEarthquakeItem(
    impact: EarthquakeImpact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color.White // 主要背景色
    val WarningBackground = Color(0xFFFFF3E0) // 警告背景色

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundPrimary
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Magnitude circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getMagnitudeColorNew(impact.earthquake.magnitude)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%.1f", impact.earthquake.magnitude),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = impact.earthquake.location.place,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "距离: ${String.format("%.1f", impact.distanceFromUser)} 公里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "预估震感: ${getIntensityText(impact.intensity)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (impact.intensity.level) {
                        in 0..2 -> Color(0xFF4CAF50) // 绿色
                        in 3..4 -> Color(0xFFFF9800) // 橙色
                        else -> Color(0xFFD32F2F) // 红色
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Time until arrival
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    modifier = Modifier
                        .background(WarningBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${impact.secondsUntilArrival}秒",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = RedEmphasis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "预计到达",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Format a date for display
 */
private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    return formatter.format(date)
}

/**
 * Get a color based on earthquake magnitude
 */
private fun getMagnitudeColor(magnitude: Double): Color {
    return when {
        magnitude >= 7.0 -> AlertCritical
        magnitude >= 6.0 -> AlertHigh
        magnitude >= 5.0 -> AlertMedium
        magnitude >= 4.0 -> AlertLow
        else -> Color.Gray
    }
}

/**
 * Get the color for an earthquake impact
 */
private fun getImpactColor(impact: EarthquakeImpact): Color {
    return when {
        impact.intensity.level >= 7 -> AlertCritical
        impact.intensity.level >= 5 -> AlertHigh
        impact.intensity.level >= 3 -> AlertMedium
        impact.intensity.level >= 1 -> AlertLow
        else -> Color.Gray
    }
}

// 获取震级颜色 - 新版
private fun getMagnitudeColorNew(magnitude: Double): Color {
    return when {
        magnitude >= 6.0 -> Color(0xFFD32F2F) // 红色
        magnitude >= 5.0 -> Color(0xFFFF9800) // 橙色
        magnitude >= 4.0 -> Color(0xFFFFC107) // 黄色
        else -> Color(0xFF4CAF50) // 绿色
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
private fun getIntensityColorNew(intensity: ShakingIntensity): Color {
    return when (intensity.level) {
        in 0..2 -> Color(0xFF4CAF50) // 绿色
        in 3..4 -> Color(0xFFFF9800) // 橙色
        else -> Color(0xFFD32F2F) // 红色
    }
}

/**
 * 空状态UI组件
 */
@Composable
private fun EmptyStateContent(
    onRefresh: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val TextPrimary = Color.Black
    val TextSecondary = Color.DarkGray
    val AccentColor = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = AccentColor.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "暂无地震数据",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "目前没有中国地震台网或四川地震局的数据，" + 
                      if (isLoading) "正在努力获取中..." else "请点击刷新按钮重试",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            androidx.compose.material3.Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AccentColor
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新"
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "刷新",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
} 