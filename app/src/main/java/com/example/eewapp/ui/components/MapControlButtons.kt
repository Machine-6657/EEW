package com.example.eewapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.eewapp.ui.theme.ButtonBackground
import com.example.eewapp.ui.theme.ButtonPressed
import com.example.eewapp.ui.theme.Primary
import com.example.eewapp.ui.theme.Secondary
import com.example.eewapp.ui.theme.Accent

/**
 * 地图控制按钮组件
 */
@Composable
fun MapControlButtons(
    onMyLocation: () -> Unit,
    onNearestEarthquake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 120.dp), // 增加底部padding，使按钮向上移动
        horizontalArrangement = Arrangement.Center, // 居中对齐
        verticalAlignment = Alignment.Bottom
    ) {
        // 我的位置按钮（蓝色）
        MapControlButton(
            onClick = onMyLocation,
            icon = Icons.Filled.MyLocation,
            contentDescription = "定位到我的位置",
            buttonColor = Color(0xFF1E90FF), // 亮蓝色 #1E90FF
            modifier = Modifier.padding(end = 16.dp) // 增加按钮之间的间距
        )
        
        // 最近地震按钮（绿色）
        MapControlButton(
            onClick = onNearestEarthquake,
            icon = Icons.Filled.LocationOn,
            contentDescription = "定位到最近地震",
            buttonColor = Color(0xFF68C29F) // 绿色强调色
        )
    }
}

/**
 * 单个地图控制按钮
 */
@Composable
fun MapControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    buttonColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val BackgroundPrimary = Color.White // 主要背景色

    // 使用白色背景，彩色图标
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = BackgroundPrimary
        ),
        modifier = modifier
            .size(56.dp) // 增大按钮尺寸
            .shadow(4.dp, CircleShape),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 6.dp
        ),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (buttonColor == Color.Red) RedEmphasis else buttonColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 地图目标中心按钮 - 移除该组件，由主按钮取代
 */ 