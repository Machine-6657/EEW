package com.example.eewapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eewapp.data.Language
import com.example.eewapp.data.MeasurementUnit
import com.example.eewapp.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.padding

// 定义颜色常量
private val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
private val TextPrimary = Color.Black // 主要文本颜色
private val TextSecondary = Color.DarkGray // 次要文本颜色
private val BackgroundPrimary = Color.White // 主要背景色
private val DividerColor = Color.LightGray.copy(alpha = 0.5f) // 分隔线颜色

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(LocalContext.current)
    )
) {
    val settings by viewModel.settingsState.collectAsState()
    
    // 对话框状态
    var showMagnitudeDialog by remember { mutableStateOf(false) }
    var showRadiusDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 设置内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 56.dp)
        ) {
            // 通知设置标题
            SectionTitle(text = "通知设置")
            
            // 地震预警通知设置
            NotificationSettingItem(
                icon = Icons.Filled.Notifications,
                iconTint = RedEmphasis,
                title = "启用地震预警",
                description = "接收地震预警通知",
                checked = settings.notificationSettings.earthquakeWarningEnabled,
                onCheckedChange = { viewModel.updateEarthquakeWarningEnabled(it) }
            )
            
            // 声音提醒设置
            NotificationSettingItem(
                icon = Icons.Outlined.VolumeUp,
                iconTint = TextPrimary,
                title = "声音提醒",
                description = "播放警报声音",
                checked = settings.notificationSettings.soundAlertEnabled,
                onCheckedChange = { viewModel.updateSoundAlertEnabled(it) }
            )
            
            // 震动提醒设置
            NotificationSettingItem(
                icon = Icons.Outlined.Vibration,
                iconTint = TextPrimary,
                title = "震动提醒",
                description = "启用设备震动",
                checked = settings.notificationSettings.vibrationEnabled,
                onCheckedChange = { viewModel.updateVibrationEnabled(it) }
            )
            
            // 过滤设置标题
            SectionTitle(text = "过滤设置")
            
            // 最小震级设置
            SettingItem(
                icon = Icons.Filled.Warning,
                iconTint = RedEmphasis,
                title = "最小震级",
                value = "${settings.filterSettings.minMagnitude} 级以上地震将触发警报",
                onClick = { showMagnitudeDialog = true }
            )
            
            // 监测半径设置
            SettingItem(
                icon = Icons.Outlined.LocationOn,
                iconTint = TextPrimary,
                title = "监测半径",
                value = "${settings.filterSettings.monitoringRadiusKm} 公里范围内的地震将被监测",
                onClick = { showRadiusDialog = true }
            )
            
            // 应用设置标题
            SectionTitle(text = "应用设置")
            
            // 语言设置
            SettingItem(
                icon = Icons.Filled.Language,
                iconTint = TextPrimary,
                title = "语言",
                value = settings.appPreferences.language.displayName,
                onClick = { showLanguageDialog = true }
            )
            
            // 单位设置
            SettingItem(
                icon = Icons.Outlined.Settings,
                iconTint = TextPrimary,
                title = "单位",
                value = settings.appPreferences.unit.displayName,
                onClick = { showUnitDialog = true }
            )
            
            // 重置所有设置按钮
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedEmphasis,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "重置所有设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // 最小震级对话框
    if (showMagnitudeDialog) {
        MagnitudeDialog(
            currentValue = settings.filterSettings.minMagnitude,
            onDismiss = { showMagnitudeDialog = false },
            onConfirm = { magnitude ->
                viewModel.updateMinMagnitude(magnitude)
                showMagnitudeDialog = false
            }
        )
    }
    
    // 监测半径对话框
    if (showRadiusDialog) {
        RadiusDialog(
            currentValue = settings.filterSettings.monitoringRadiusKm,
            onDismiss = { showRadiusDialog = false },
            onConfirm = { radius ->
                viewModel.updateMonitoringRadius(radius)
                showRadiusDialog = false
            }
        )
    }
    
    // 语言选择对话框
    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = settings.appPreferences.language,
            onDismiss = { showLanguageDialog = false },
            onSelect = { language ->
                viewModel.updateLanguage(language)
                showLanguageDialog = false
            }
        )
    }
    
    // 单位选择对话框
    if (showUnitDialog) {
        UnitDialog(
            currentUnit = settings.appPreferences.unit,
            onDismiss = { showUnitDialog = false },
            onSelect = { unit ->
                viewModel.updateUnit(unit)
                showUnitDialog = false
            }
        )
    }
    
    // 重置确认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置设置", color = TextPrimary) },
            text = { Text("确定要将所有设置恢复为默认值吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllSettings()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedEmphasis)
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Text("取消")
                }
            },
            containerColor = BackgroundPrimary
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 16.dp),
        color = TextSecondary,
        fontSize = 16.sp
    )
}

@Composable
fun SettingItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    title == "最小震级" -> RedEmphasis.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 文本内容
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            
            Text(
                text = value,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        // 右箭头
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun NotificationSettingItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    title == "启用地震预警" -> RedEmphasis.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 文本内容
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            
            Text(
                text = description,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        // 开关
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RedEmphasis,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
            )
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun MagnitudeDialog(
    currentValue: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val magnitudeOptions = listOf(3.0f, 3.5f, 4.0f, 4.5f, 5.0f, 5.5f, 6.0f)
    var selectedMagnitude by remember { mutableStateOf(currentValue) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "选择最小震级",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 震级选项
                Column {
                    magnitudeOptions.forEach { magnitude ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMagnitude = magnitude }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMagnitude == magnitude,
                                onClick = { selectedMagnitude = magnitude },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = RedEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = "$magnitude 级",
                                modifier = Modifier.padding(start = 8.dp),
                                color = if (magnitude >= 5.0f) RedEmphasis else TextPrimary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("取消")
                    }
                    
                    TextButton(
                        onClick = { onConfirm(selectedMagnitude) },
                        colors = ButtonDefaults.textButtonColors(contentColor = RedEmphasis)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
fun RadiusDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val radiusOptions = listOf(100, 200, 300, 500, 1000)
    var selectedRadius by remember { mutableStateOf(currentValue) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "选择监测半径",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 半径选项
                Column {
                    radiusOptions.forEach { radius ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRadius = radius }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRadius == radius,
                                onClick = { selectedRadius = radius },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = RedEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = "$radius 公里",
                                modifier = Modifier.padding(start = 8.dp),
                                color = TextPrimary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("取消")
                    }
                    
                    TextButton(
                        onClick = { onConfirm(selectedRadius) },
                        colors = ButtonDefaults.textButtonColors(contentColor = RedEmphasis)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageDialog(
    currentLanguage: Language,
    onDismiss: () -> Unit,
    onSelect: (Language) -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "选择语言",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 语言选项
                Column {
                    Language.values().forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLanguage = language }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == language,
                                onClick = { selectedLanguage = language },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = RedEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = language.displayName,
                                modifier = Modifier.padding(start = 8.dp),
                                color = TextPrimary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("取消")
                    }
                    
                    TextButton(
                        onClick = { onSelect(selectedLanguage) },
                        colors = ButtonDefaults.textButtonColors(contentColor = RedEmphasis)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
fun UnitDialog(
    currentUnit: MeasurementUnit,
    onDismiss: () -> Unit,
    onSelect: (MeasurementUnit) -> Unit
) {
    var selectedUnit by remember { mutableStateOf(currentUnit) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundPrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "选择单位",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 单位选项
                Column {
                    MeasurementUnit.values().forEach { unit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedUnit = unit }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUnit == unit,
                                onClick = { selectedUnit = unit },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = RedEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = unit.displayName,
                                modifier = Modifier.padding(start = 8.dp),
                                color = TextPrimary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("取消")
                    }
                    
                    TextButton(
                        onClick = { onSelect(selectedUnit) },
                        colors = ButtonDefaults.textButtonColors(contentColor = RedEmphasis)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
} 