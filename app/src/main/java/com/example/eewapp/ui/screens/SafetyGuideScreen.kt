package com.example.eewapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 定义颜色常量
private val BlueEmphasis = Color(0xFF1E90FF) // 蓝色强调色
private val TextPrimary = Color.Black // 主要文本颜色
private val TextSecondary = Color.DarkGray // 次要文本颜色
private val BackgroundPrimary = Color(0xFFF0F2F5) // 更明显的浅灰色背景
private val BackgroundSecondary = Color(0xFFF5F5F5) // 次要背景色
private val DividerColor = Color.LightGray.copy(alpha = 0.5f) // 分隔线颜色

@Composable
fun SafetyGuideScreen(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 标签栏
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.White,
            contentColor = BlueEmphasis
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "无震时",
                        color = if (selectedTab == 0) BlueEmphasis else TextSecondary,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "地震时",
                        color = if (selectedTab == 1) BlueEmphasis else TextSecondary,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text(
                        "地震后",
                        color = if (selectedTab == 2) BlueEmphasis else TextSecondary,
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
        
        // 内容区域
        when (selectedTab) {
            0 -> BeforeEarthquakeContent()
            1 -> DuringEarthquakeContent()
            2 -> AfterEarthquakeContent()
        }
    }
}

@Composable
fun BeforeEarthquakeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // 图标
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = BlueEmphasis.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "地震前准备",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 准备事项卡片
        SafetyCard(
            title = "制定应急计划",
            content = "• 与家人讨论地震应急计划\n• 确定安全集合地点\n• 练习疏散路线\n• 学习基本急救知识"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SafetyCard(
            title = "准备应急包",
            content = "• 饮用水（每人每天3升，至少3天用量）\n• 不易腐坏的食物\n• 手电筒和电池\n• 急救包和常用药品\n• 重要文件副本"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SafetyCard(
            title = "加固家居环境",
            content = "• 固定高大家具到墙上\n• 将重物放在低处\n• 检查燃气、水、电设施\n• 清理疏散通道"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DuringEarthquakeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // 图标
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFFFF9800)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "地震发生时",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 应对措施卡片
        SafetyCard(
            title = "室内避震",
            content = "• 立即趴下、掩护、抓牢\n• 躲在坚固的桌子下\n• 远离窗户和可能坠落的物品\n• 不要试图跑出建筑物"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SafetyCard(
            title = "室外避震",
            content = "• 远离建筑物、电线和树木\n• 蹲下并保护头部\n• 避开可能坠落的招牌\n• 不要进入建筑物"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SafetyCard(
            title = "驾车时避震",
            content = "• 缓慢停车，避开桥梁和立交桥\n• 留在车内，系好安全带\n• 不要停在建筑物或树木下\n• 地震停止后谨慎驾驶"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AfterEarthquakeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // 图标
        Icon(
            imageVector = Icons.Default.Emergency,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFFD32F2F)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "地震后处理",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 处理措施卡片
        SafetyCard(
            title = "检查伤情",
            content = "• 检查自己和他人是否受伤\n• 进行必要的急救处理\n• 不要移动重伤员\n• 及时寻求医疗帮助"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SafetyCard(
            title = "检查危险",
            content = "• 检查燃气、水、电是否损坏\n• 发现燃气泄漏立即关闭阀门\n• 不要使用明火或电器\n• 远离受损建筑和电线"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SafetyCard(
            title = "获取信息",
            content = "• 收听官方广播获取信息\n• 警惕余震的发生\n• 遵循当地应急部门指示\n• 帮助邻居和社区"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SafetyCard(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = BlueEmphasis,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = content,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
} 