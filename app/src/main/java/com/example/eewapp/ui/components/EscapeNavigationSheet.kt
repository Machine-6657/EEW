package com.example.eewapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eewapp.data.*
import com.example.eewapp.utils.EarthquakeUtils.calculateDistance
import kotlinx.coroutines.delay
import android.util.Log

/**
 * 逃生导航底部表单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscapeNavigationSheet(
    isVisible: Boolean,
    userLocation: UserLocation?,
    safetyLocations: List<SafetyLocation>,
    selectedDestination: SafetyLocation?,
    currentRoute: NavigationRoute?,
    isNavigating: Boolean,
    onLocationSelected: (SafetyLocation) -> Unit,
    onStartNavigation: (SafetyLocation) -> Unit,
    onStopNavigation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 添加日志，追踪参数变化
    Log.d("EscapeNavSheetDebug", "EscapeNavigationSheet Composable: isVisible=$isVisible, isNavigating=$isNavigating, currentRoute is null=${currentRoute == null}")

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f), // *** 将高度从 0.4f 增加到 0.75f (75%的屏幕高度) ***
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), // 圆角减小
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp) // 阴影减小
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp) // 从16dp减少到12dp
            ) {
                // 顶部标题栏
                EscapeNavigationHeader(
                    onDismiss = onDismiss,
                    isNavigating = isNavigating
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 根据导航状态显示不同内容
                if (isNavigating && currentRoute != null) {
                    // 导航中 - 显示导航信息
                    NavigationInstructions(
                        route = currentRoute,
                        onStopNavigation = onStopNavigation
                    )
                } else {
                    // 未导航 - 显示安全地点列表
                    SafetyLocationsList(
                        userLocation = userLocation,
                        safetyLocations = safetyLocations,
                        selectedDestination = selectedDestination,
                        onLocationSelected = onLocationSelected,
                        onStartNavigation = onStartNavigation
                    )
                }
            }
        }
    }
}

/**
 * 逃生导航标题栏
 */
@Composable
private fun EscapeNavigationHeader(
    onDismiss: () -> Unit,
    isNavigating: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isNavigating) "导航中" else "选择安全地点",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(32.dp)
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.Gray
            )
        }
    }
}

/**
 * 安全地点列表
 */
@Composable
private fun SafetyLocationsList(
    userLocation: UserLocation?,
    safetyLocations: List<SafetyLocation>,
    selectedDestination: SafetyLocation?,
    onLocationSelected: (SafetyLocation) -> Unit,
    onStartNavigation: (SafetyLocation) -> Unit
) {
    if (safetyLocations.isEmpty()) {
        // 空状态
        EmptyStateMessage()
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(safetyLocations) { location ->
                SafetyLocationItem(
                    location = location,
                    userLocation = userLocation,
                    isSelected = selectedDestination?.id == location.id,
                    onLocationSelected = onLocationSelected,
                    onStartNavigation = onStartNavigation
                )
            }
        }
    }
}

/**
 * 单个安全地点条目
 */
@Composable
private fun SafetyLocationItem(
    location: SafetyLocation,
    userLocation: UserLocation?,
    isSelected: Boolean,
    onLocationSelected: (SafetyLocation) -> Unit,
    onStartNavigation: (SafetyLocation) -> Unit
) {
    val distance = userLocation?.let { user ->
        calculateDistance(
            user.latitude, user.longitude,
            location.latitude, location.longitude
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLocationSelected(location) }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color(0xFF2196F3), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2196F3).copy(alpha = 0.1f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 地点基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 类型图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(location.type.color).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = location.type.icon,
                        contentDescription = location.type.displayName,
                        tint = Color(location.type.color),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 地点信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = location.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Text(
                        text = location.type.displayName,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                    
                    if (distance != null) {
                        Text(
                            text = "${String.format("%.1f", distance)} 公里",
                            fontSize = 12.sp,
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // 导航按钮
                AnimatedVisibility(visible = isSelected) {
                    Button(
                        onClick = {
                            Log.d("EscapeNavSheetDebug", "SafetyLocationItem: '导航'按钮点击, locationId=${location.id}")
                            onStartNavigation(location)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "开始导航",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "导航",
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // 详细信息
            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = location.address,
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    lineHeight = 18.sp
                )
                
                if (location.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = location.description,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
                
                // 设施信息
                if (location.facilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "设施：${location.facilities.joinToString("、")}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                // 容量信息
                location.capacity?.let { capacity ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "容纳人数：约 $capacity 人",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * 导航指令显示
 */
@Composable
private fun NavigationInstructions(
    route: NavigationRoute,
    onStopNavigation: () -> Unit
) {
    Column {
        // 导航概览
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp), // 从12dp减少到8dp
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp) // 从16dp减少到10dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = route.destination.name,
                            fontSize = 15.sp, // 从18sp减少到15sp
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "${String.format("%.1f", route.distanceInMeters / 1000)} 公里 · ${route.estimatedDurationMinutes} 分钟",
                            fontSize = 12.sp, // 从14sp减少到12sp
                            color = Color.Gray
                        )
                    }
                    
                    Button(
                        onClick = {
                            Log.d("EscapeNavSheetDebug", "NavigationInstructions: '停止'按钮点击")
                            onStopNavigation()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        ),
                        shape = RoundedCornerShape(16.dp), // 从20dp减少到16dp
                        modifier = Modifier.height(32.dp) // 添加高度限制
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止导航",
                            modifier = Modifier.size(14.dp) // 从16dp减少到14dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "停止",
                            fontSize = 12.sp // 添加字体大小
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp)) // 从16dp减少到8dp
        
        // 导航指令列表
        LazyColumn {
            items(route.instructions) { instruction ->
                InstructionItem(instruction = instruction)
            }
        }
    }
}

/**
 * 单条导航指令
 */
@Composable
private fun InstructionItem(instruction: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // 从8dp减少到4dp
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(4.dp) // 从6dp减少到4dp
                .clip(CircleShape)
                .background(Color(0xFF2196F3))
                .padding(top = 4.dp) // 从6dp减少到4dp
        )
        
        Spacer(modifier = Modifier.width(8.dp)) // 从12dp减少到8dp
        
        Text(
            text = instruction,
            fontSize = 12.sp, // 从14sp减少到12sp
            color = Color.DarkGray,
            lineHeight = 16.sp, // 从18sp减少到16sp
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 空状态消息
 */
@Composable
private fun EmptyStateMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LocationSearching,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "附近没有找到安全地点",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "请尝试扩大搜索范围或联系当地应急部门",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 逃生导航卡片 - 与地震信息卡片相同尺寸
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscapeNavigationCard(
    isVisible: Boolean,
    userLocation: UserLocation?,
    currentRoute: NavigationRoute?,
    earthquake: com.example.eewapp.data.Earthquake?,
    impact: com.example.eewapp.data.EarthquakeImpact?,
    isNavigating: Boolean,
    onStopNavigation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 添加日志，追踪参数变化
    Log.d("EscapeNavSheetDebug", "EscapeNavigationCard Composable: isVisible=$isVisible, isNavigating=$isNavigating, currentRoute is null=${currentRoute == null}")

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color.LightGray.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // 顶部：地震位置、震级和操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // 地震位置信息
                    Column(
                        modifier = Modifier
                            .weight(1.2f) // 保持原有权重
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "震中位置",
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = earthquake?.location?.place ?: "未知位置",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    // 震级显示
                    if (earthquake != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(0.6f) // 保持原有权重
                        ) {
                            Text(
                                text = "震级",
                                color = Color.DarkGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${earthquake.magnitude}",
                                color = when { 
                                    earthquake.magnitude >= 6.0 -> Color(0xFFD32F2F) 
                                    earthquake.magnitude >= 5.0 -> Color(0xFFFF9800) 
                                    earthquake.magnitude >= 4.0 -> Color(0xFFFFC107) 
                                    else -> Color(0xFF4CAF50) 
                                },
                                fontSize = 24.sp, 
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    
                    // 右侧：返回按钮 (替换原导航状态和停止按钮)
                    Column(
                        horizontalAlignment = Alignment.End, // 保持末端对齐
                        modifier = Modifier.weight(0.8f) // 保持原有权重
                    ) {
                        IconButton(
                            onClick = {
                                Log.d("EscapeNavSheetDebug", "EscapeNavigationCard: '撤销'按钮点击")
                                onStopNavigation() // 点击执行停止导航
                            },
                            modifier = Modifier // 移除或调整 top padding 使其上移
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo, // <-- 修改图标为Undo
                                contentDescription = "撤销导航", // 修改描述
                                tint = Color.DarkGray 
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // 导航目标信息
                if (currentRoute != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2196F3).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column { // 外层用 Column 包裹两行
                            Row( // 第一行：图标和目标地点
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "寻航至：${currentRoute.destination.name}",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp)) // 两行之间的间距
                            Text(
                                text = "${String.format("%.1f", currentRoute.distanceInMeters / 1000)} 公里 · ${currentRoute.estimatedDurationMinutes} 分钟",
                                color = Color(0xFF2196F3),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 28.dp) // 20dp (图标) + 8dp (间距)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 地震详细信息（压缩版）
                if (earthquake != null && impact != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 震中距离
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "震中距离",
                                    color = Color.DarkGray,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "${impact.distanceFromUser.toInt()} 公里",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 18.dp) // 14dp icon + 4dp spacer
                            )
                        }
                        
                        // 预估到达时间（带倒计时）
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "预计到达",
                                    color = Color.DarkGray,
                                    fontSize = 11.sp
                                )
                            }
                                    
                            // 倒计时逻辑
                            var remainingSeconds by remember { mutableStateOf(impact.secondsUntilArrival) }
                            
                            LaunchedEffect(impact.estimatedArrivalTime) {
                                while (remainingSeconds > 0) {
                                    delay(1000)
                                    val currentTime = System.currentTimeMillis()
                                    val newRemainingSeconds = ((impact.estimatedArrivalTime - currentTime) / 1000).toInt()
                                    remainingSeconds = maxOf(0, newRemainingSeconds)
                                }
                            }
                            
                            Text(
                                text = if (remainingSeconds > 0) "${remainingSeconds} 秒后" else "已到达",
                                color = Color(0xFFD32F2F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 18.dp) // 14dp icon + 4dp spacer
                            )
                        }
                        
                        // 预估震感
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WbTwilight,
                                    contentDescription = null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "预估震感",
                                    color = Color.DarkGray,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = when (impact.intensity.level) {
                                    in 0..2 -> "弱，请勿慌张"
                                    in 3..4 -> "中，请尽快逃生"
                                    else -> "强，请尽快自救"
                                },
                                color = when (impact.intensity.level) {
                                    in 0..2 -> Color(0xFF4CAF50)
                                    in 3..4 -> Color(0xFFFF9800)
                                    else -> Color(0xFFD32F2F)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 18.dp) // 14dp icon + 4dp spacer
                            )
                        }
                    }
                }
            }
        }
    }
} 