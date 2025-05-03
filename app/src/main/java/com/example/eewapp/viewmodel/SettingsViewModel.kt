package com.example.eewapp.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.eewapp.data.AppPreferences
import com.example.eewapp.data.AppSettings
import com.example.eewapp.data.FilterSettings
import com.example.eewapp.data.Language
import com.example.eewapp.data.MeasurementUnit
import com.example.eewapp.data.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(private val context: Context) : ViewModel() {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )
    
    // 设置状态
    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<AppSettings> = _settingsState.asStateFlow()
    
    init {
        // 初始化时应用当前语言设置
        applyLanguageSetting(_settingsState.value.appPreferences.language)
    }
    
    /**
     * 更新地震预警通知设置
     */
    fun updateEarthquakeWarningEnabled(enabled: Boolean) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                notificationSettings = currentSettings.notificationSettings.copy(
                    earthquakeWarningEnabled = enabled
                )
            )
        }
        saveSettings()
    }
    
    /**
     * 更新声音提醒设置
     */
    fun updateSoundAlertEnabled(enabled: Boolean) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                notificationSettings = currentSettings.notificationSettings.copy(
                    soundAlertEnabled = enabled
                )
            )
        }
        saveSettings()
    }
    
    /**
     * 更新震动提醒设置
     */
    fun updateVibrationEnabled(enabled: Boolean) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                notificationSettings = currentSettings.notificationSettings.copy(
                    vibrationEnabled = enabled
                )
            )
        }
        saveSettings()
    }
    
    /**
     * 更新最小震级
     */
    fun updateMinMagnitude(magnitude: Float) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                filterSettings = currentSettings.filterSettings.copy(
                    minMagnitude = magnitude
                )
            )
        }
        saveSettings()
    }
    
    /**
     * 更新监测半径
     */
    fun updateMonitoringRadius(radiusKm: Int) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                filterSettings = currentSettings.filterSettings.copy(
                    monitoringRadiusKm = radiusKm
                )
            )
        }
        saveSettings()
    }
    
    /**
     * 更新语言设置
     */
    fun updateLanguage(language: Language) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                appPreferences = currentSettings.appPreferences.copy(
                    language = language
                )
            )
        }
        saveSettings()
        
        // 应用语言设置
        applyLanguageSetting(language)
    }
    
    /**
     * 应用语言设置
     */
    private fun applyLanguageSetting(language: Language) {
        val locale = when (language) {
            Language.CHINESE -> Locale.CHINESE
            Language.ENGLISH -> Locale.ENGLISH
        }
        
        try {
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying language setting", e)
        }
    }
    
    /**
     * 更新单位设置
     */
    fun updateUnit(unit: MeasurementUnit) {
        _settingsState.update { currentSettings ->
            currentSettings.copy(
                appPreferences = currentSettings.appPreferences.copy(
                    unit = unit
                )
            )
        }
        saveSettings()
    }
    
    /**
     * 重置所有设置
     */
    fun resetAllSettings() {
        _settingsState.update { AppSettings() }
        saveSettings()
        
        // 重置后应用默认语言
        applyLanguageSetting(Language.CHINESE)
    }
    
    /**
     * 从SharedPreferences加载设置
     */
    private fun loadSettings(): AppSettings {
        return try {
            // 通知设置
            val earthquakeWarningEnabled = sharedPreferences.getBoolean(KEY_EARTHQUAKE_WARNING_ENABLED, true)
            val soundAlertEnabled = sharedPreferences.getBoolean(KEY_SOUND_ALERT_ENABLED, true)
            val vibrationEnabled = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
            
            // 过滤设置
            val minMagnitude = sharedPreferences.getFloat(KEY_MIN_MAGNITUDE, 4.0f)
            val monitoringRadius = sharedPreferences.getInt(KEY_MONITORING_RADIUS, 300)
            
            // 应用设置
            val languageOrdinal = sharedPreferences.getInt(KEY_LANGUAGE, Language.CHINESE.ordinal)
            val unitOrdinal = sharedPreferences.getInt(KEY_UNIT, MeasurementUnit.METRIC.ordinal)
            
            val language = Language.values().getOrElse(languageOrdinal) { Language.CHINESE }
            val unit = MeasurementUnit.values().getOrElse(unitOrdinal) { MeasurementUnit.METRIC }
            
            AppSettings(
                notificationSettings = NotificationSettings(
                    earthquakeWarningEnabled = earthquakeWarningEnabled,
                    soundAlertEnabled = soundAlertEnabled,
                    vibrationEnabled = vibrationEnabled
                ),
                filterSettings = FilterSettings(
                    minMagnitude = minMagnitude,
                    monitoringRadiusKm = monitoringRadius
                ),
                appPreferences = AppPreferences(
                    language = language,
                    unit = unit
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
            AppSettings() // 返回默认设置
        }
    }
    
    /**
     * 保存设置到SharedPreferences
     */
    private fun saveSettings() {
        viewModelScope.launch {
            try {
                val currentSettings = _settingsState.value
                sharedPreferences.edit().apply {
                    // 保存通知设置
                    putBoolean(KEY_EARTHQUAKE_WARNING_ENABLED, currentSettings.notificationSettings.earthquakeWarningEnabled)
                    putBoolean(KEY_SOUND_ALERT_ENABLED, currentSettings.notificationSettings.soundAlertEnabled)
                    putBoolean(KEY_VIBRATION_ENABLED, currentSettings.notificationSettings.vibrationEnabled)
                    
                    // 保存过滤设置
                    putFloat(KEY_MIN_MAGNITUDE, currentSettings.filterSettings.minMagnitude)
                    putInt(KEY_MONITORING_RADIUS, currentSettings.filterSettings.monitoringRadiusKm)
                    
                    // 保存应用设置
                    putInt(KEY_LANGUAGE, currentSettings.appPreferences.language.ordinal)
                    putInt(KEY_UNIT, currentSettings.appPreferences.unit.ordinal)
                    
                    apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "SettingsViewModel"
        private const val PREFERENCES_NAME = "eewapp_settings"
        
        // SharedPreferences 键 - 通知设置
        private const val KEY_EARTHQUAKE_WARNING_ENABLED = "earthquake_warning_enabled"
        private const val KEY_SOUND_ALERT_ENABLED = "sound_alert_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        
        // SharedPreferences 键 - 过滤设置
        private const val KEY_MIN_MAGNITUDE = "min_magnitude"
        private const val KEY_MONITORING_RADIUS = "monitoring_radius"
        
        // SharedPreferences 键 - 应用设置
        private const val KEY_LANGUAGE = "language"
        private const val KEY_UNIT = "unit"
    }
    
    /**
     * ViewModel工厂
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 