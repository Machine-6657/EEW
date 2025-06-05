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
import android.app.Activity
import androidx.compose.ui.res.stringResource
import com.example.eewapp.R

// 定义颜色常量
private val BlueEmphasis = Color(0xFF6685EC) // 蓝色强调色
private val TextPrimary = Color.Black // 主要文本颜色
private val TextSecondary = Color.DarkGray // 次要文本颜色
private val BackgroundPrimary = Color(0xFFF0F2F5) // 更明显的浅灰色背景
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
    val context = LocalContext.current
    
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
            SectionTitle(text = stringResource(id = R.string.settings_notification_settings))
            
            // 地震预警通知设置
            NotificationSettingItem(
                icon = Icons.Filled.Notifications,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_enable_earthquake_warning),
                description = stringResource(id = R.string.settings_enable_earthquake_warning_description),
                checked = settings.notificationSettings.earthquakeWarningEnabled,
                onCheckedChange = { viewModel.updateEarthquakeWarningEnabled(it) }
            )
            
            // 声音提醒设置
            NotificationSettingItem(
                icon = Icons.Outlined.VolumeUp,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_sound_alert),
                description = stringResource(id = R.string.settings_sound_alert_description),
                checked = settings.notificationSettings.soundAlertEnabled,
                onCheckedChange = { viewModel.updateSoundAlertEnabled(it) }
            )
            
            // 震动提醒设置
            NotificationSettingItem(
                icon = Icons.Outlined.Vibration,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_vibration_alert),
                description = stringResource(id = R.string.settings_vibration_alert_description),
                checked = settings.notificationSettings.vibrationEnabled,
                onCheckedChange = { viewModel.updateVibrationEnabled(it) }
            )
            
            // 过滤设置标题
            SectionTitle(text = stringResource(id = R.string.settings_filter_settings))
            
            // 最小震级设置
            SettingItem(
                icon = Icons.Filled.Warning,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_min_magnitude),
                value = stringResource(id = R.string.settings_min_magnitude_description_template, settings.filterSettings.minMagnitude),
                onClick = { showMagnitudeDialog = true }
            )
            
            // 监测半径设置
            SettingItem(
                icon = Icons.Outlined.LocationOn,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_monitoring_radius),
                value = stringResource(id = R.string.settings_monitoring_radius_description_template, settings.filterSettings.monitoringRadiusKm),
                onClick = { showRadiusDialog = true }
            )
            
            // 应用设置标题
            SectionTitle(text = stringResource(id = R.string.settings_app_settings))
            
            // 语言设置
            SettingItem(
                icon = Icons.Filled.Language,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_language),
                value = if (settings.appPreferences.language == Language.CHINESE) stringResource(R.string.language_chinese) else stringResource(R.string.language_english),
                onClick = { showLanguageDialog = true }
            )
            
            // 单位设置
            SettingItem(
                icon = Icons.Outlined.Settings,
                iconTint = BlueEmphasis,
                title = stringResource(id = R.string.settings_unit),
                value = stringResource(id = settings.appPreferences.unit.displayResId),
                onClick = { showUnitDialog = true }
            )
            
            // 重置所有设置按钮
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlueEmphasis,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_reset_all_settings),
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
                val activity = context as? Activity
                activity?.recreate()
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
            title = { Text(stringResource(id = R.string.settings_reset_dialog_title), color = TextPrimary) },
            text = { Text(stringResource(id = R.string.settings_reset_dialog_message), color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllSettings()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = BlueEmphasis)
                ) {
                    Text(stringResource(id = R.string.button_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    Text(stringResource(id = R.string.button_cancel))
                }
            },
            containerColor = Color.White
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 16.dp),
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
    }
    
    Spacer(modifier = Modifier.height(12.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 16.dp),
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
                    checkedTrackColor = BlueEmphasis,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.5f)
            )
        )
        }
    }
    
    Spacer(modifier = Modifier.height(12.dp))
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_select_min_magnitude_title),
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
                                    selectedColor = BlueEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = stringResource(id = R.string.settings_magnitude_level_template, magnitude),
                                modifier = Modifier.padding(start = 8.dp),
                                color = if (magnitude >= 5.0f) BlueEmphasis else TextPrimary
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
                        Text(stringResource(id = R.string.button_cancel))
                    }
                    
                    TextButton(
                        onClick = { onConfirm(selectedMagnitude) },
                        colors = ButtonDefaults.textButtonColors(contentColor = BlueEmphasis)
                    ) {
                        Text(stringResource(id = R.string.button_confirm))
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_select_monitoring_radius_title),
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
                                    selectedColor = BlueEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = stringResource(id = R.string.settings_radius_km_template, radius),
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
                        Text(stringResource(id = R.string.button_cancel))
                    }
                    
                    TextButton(
                        onClick = { onConfirm(selectedRadius) },
                        colors = ButtonDefaults.textButtonColors(contentColor = BlueEmphasis)
                    ) {
                        Text(stringResource(id = R.string.button_confirm))
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_language_dialog_title),
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
                                    selectedColor = BlueEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = if (language == Language.CHINESE) stringResource(R.string.language_chinese) else stringResource(R.string.language_english),
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
                        Text(stringResource(id = R.string.button_cancel))
                    }
                    
                    TextButton(
                        onClick = { onSelect(selectedLanguage) },
                        colors = ButtonDefaults.textButtonColors(contentColor = BlueEmphasis)
                    ) {
                        Text(stringResource(id = R.string.button_confirm))
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_select_unit_title),
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
                                    selectedColor = BlueEmphasis,
                                    unselectedColor = TextSecondary
                                )
                            )
                            
                            Text(
                                text = stringResource(id = unit.displayResId),
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
                        Text(stringResource(id = R.string.button_cancel))
                    }
                    
                    TextButton(
                        onClick = { onSelect(selectedUnit) },
                        colors = ButtonDefaults.textButtonColors(contentColor = BlueEmphasis)
                    ) {
                        Text(stringResource(id = R.string.button_confirm))
                    }
                }
            }
        }
    }
} 