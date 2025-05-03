package com.example.eewapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 定义颜色常量
private val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
private val TextPrimary = Color.Black // 主要文本颜色
private val TextSecondary = Color.DarkGray // 次要文本颜色
private val BackgroundPrimary = Color.White // 主要背景色
private val BackgroundSecondary = Color(0xFFF5F5F5) // 次要背景色
private val DividerColor = Color.LightGray.copy(alpha = 0.5f) // 分隔线颜色

@Composable
fun SafetyGuideScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 顶部标题栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RedEmphasis)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "安全指南",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 标签栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                TabButton(
                    text = "全部",
                    isSelected = true,
                    onClick = { /* 切换到全部标签 */ }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TabButton(
                    text = "地震前",
                    isSelected = false,
                    onClick = { /* 切换到地震前标签 */ }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TabButton(
                    text = "地震中",
                    isSelected = false,
                    onClick = { /* 切换到地震中标签 */ }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TabButton(
                    text = "地震后",
                    isSelected = false,
                    onClick = { /* 切换到地震后标签 */ }
                )
            }
            
            // 检查伤情卡片
            SafetyCard(
                title = "检查伤情",
                subtitle = "地震后",
                content = "• 检查自己和周围人员是否受伤\n• 如有伤员，立即进行必要的急救处理\n• 不要移动重伤员，除非他们处于危险中\n• 寻求专业医疗帮助"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 检查危险卡片
            SafetyCard(
                title = "检查危险",
                subtitle = "地震后",
                content = "• 检查燃气、水和电力系统是否损坏\n• 如发现燃气泄漏，立即关闭总阀门并打开窗户\n• 不要使用明火或电器\n• 远离受损建筑物和电线"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 重要提示卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RedEmphasis,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "重要提示",
                            color = RedEmphasis,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    BulletPoint(text = "请记住，每次地震情况可能不同，请根据实际情况灵活应对。")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BulletPoint(text = "提前了解您所在地区的地震风险和应急计划。")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BulletPoint(text = "定期与家人进行地震演习，确保每个人都知道如何应对。")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BulletPoint(text = "保持应急包随时可用，包含水、食物、药品和其他必需品。")
                }
            }
        }
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) RedEmphasis else Color.White,
            contentColor = if (isSelected) Color.White else TextPrimary
        ),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            text = text,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SafetyCard(
    title: String,
    subtitle: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧绿色指示条
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .background(Color(0xFF4CAF50), RoundedCornerShape(2.dp))
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 展开/收起按钮
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "展开",
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = content,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = RedEmphasis,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
        )
        
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
} 