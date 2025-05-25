package com.example.eewapp.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.example.eewapp.data.Earthquake
import com.example.eewapp.data.EarthquakeImpact
import com.example.eewapp.data.ShakingIntensity
import com.example.eewapp.data.UserLocation
import com.example.eewapp.location.AMapLocationService
import com.example.eewapp.repository.EarthquakeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 地震预警ViewModel
 */
class EarthquakeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = EarthquakeRepository()
    private val locationService = AMapLocationService(application)
    
    // 用户位置
    val userLocation = locationService.userLocation.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    // 最近的地震列表
    val recentEarthquakes: StateFlow<List<Earthquake>> = repository.recentEarthquakes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 重要地震列表（可能影响用户的地震）
    val significantEarthquakes: StateFlow<List<EarthquakeImpact>> = repository.significantEarthquakes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 当前最重要的地震影响
    private val _currentImpact = MutableStateFlow<EarthquakeImpact?>(null)
    val currentImpact: StateFlow<EarthquakeImpact?> = _currentImpact.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误状态
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // 警报状态
    private val _isAlertActive = MutableStateFlow(false)
    val isAlertActive: StateFlow<Boolean> = _isAlertActive.asStateFlow()
    
    // 媒体播放器（用于警报声音）
    private var mediaPlayer: MediaPlayer? = null
    
    // 震动器
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getApplication<Application>().getSystemService(Application.VIBRATOR_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Application.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    // 警报任务
    private var alertJob: Job? = null
    
    init {
        // 开始定位
        locationService.startLocationUpdates()
        
        // 加载地震数据
        loadEarthquakes()
    }
    
    /**
     * 加载地震数据
     */
    private fun loadEarthquakes() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 加载中国地震台网和四川地震局数据
                Log.d("EarthquakeViewModel", "应用启动：加载中国地震台网和四川地震局数据...")
                
                // 清空现有数据，确保看到最新加载的数据
                (repository as? com.example.eewapp.repository.EarthquakeRepository)?.clearExistingData()
                
                // 先加载中国地震台网数据（全国数据）
                val cencSuccess = (repository as? com.example.eewapp.repository.EarthquakeRepository)?.fetchChinaEarthquakeNetworkData() ?: false
                Log.d("EarthquakeViewModel", "中国地震台网数据加载${if (cencSuccess) "成功" else "失败"}")
                
                // 然后加载四川地震局数据（四川地区可能更详细的数据）
                val sichuanSuccess = (repository as? com.example.eewapp.repository.EarthquakeRepository)?.fetchSichuanDataDirectly() ?: false
                Log.d("EarthquakeViewModel", "四川地震局数据加载${if (sichuanSuccess) "成功" else "失败"}")
                
                // 等待足够长的时间，确保HTTP请求完成
                delay(3000)
                
                // 检查是否成功获取到数据
                val earthquakesCount = recentEarthquakes.value.size
                if (earthquakesCount > 0) {
                    Log.d("EarthquakeViewModel", "成功加载到 $earthquakesCount 条地震数据")
                } else {
                    Log.w("EarthquakeViewModel", "未能加载到任何地震数据")
                }
                
                // 开始监控新地震
                startEarthquakeMonitoring()
            } catch (e: Exception) {
                Log.e("EarthquakeViewModel", "初始加载地震数据失败: ${e.message}", e)
                _error.value = "加载地震数据失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 获取最近的地震
     */
    private fun fetchRecentEarthquakes() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // 首先尝试获取中国地震台网数据
                Log.d("EarthquakeViewModel", "正在尝试获取中国地震台网数据...")
                (repository as? com.example.eewapp.repository.EarthquakeRepository)?.fetchChinaEarthquakeNetworkData()
                
                // 然后尝试直接通过 HTTP 从四川地震局获取数据
                Log.d("EarthquakeViewModel", "正在尝试直接通过 HTTP 从四川地震局获取数据...")
                (repository as? com.example.eewapp.repository.EarthquakeRepository)?.fetchSichuanDataDirectly()
                
                // 等待一段时间，让数据有机会加载
                delay(2000)
                
                // 检查是否成功获取到数据
                Log.d("EarthquakeViewModel", "从本地数据源获取数据后，现有地震数量: ${recentEarthquakes.value.size}")
                
                if (recentEarthquakes.value.isEmpty()) {
                    Log.d("EarthquakeViewModel", "未能获取到任何地震数据")
                } else {
                    Log.d("EarthquakeViewModel", "成功获取地震数据，共 ${recentEarthquakes.value.size} 条")
                }
            } catch (e: Exception) {
                Log.e("EarthquakeViewModel", "获取地震数据失败", e)
                _error.value = "获取地震数据失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 开始监控地震
     */
    private fun startEarthquakeMonitoring() {
        viewModelScope.launch {
            while (true) {
                try {
                    repository.fetchLast24HoursEarthquakes()
                    checkForAlerts()
                } catch (e: Exception) {
                    _error.value = "监控地震错误: ${e.message}"
                }
                // 每30秒检查一次新地震
                delay(30000)
            }
        }
    }
    
    /**
     * 根据重要地震检查警报
     */
    private fun checkForAlerts() {
        val mostSignificantImpact = repository.getMostSignificantImpact()
        
        if (mostSignificantImpact != null && mostSignificantImpact.secondsUntilArrival > 0) {
            _currentImpact.value = mostSignificantImpact
            
            // 如果震感强度大于等于4级，触发警报
            if (mostSignificantImpact.intensity.level >= ShakingIntensity.LEVEL_4.level && !_isAlertActive.value) {
                triggerAlert(mostSignificantImpact)
            }
        } else {
            // 如果没有重要地震，停止警报
            if (_isAlertActive.value) {
                stopAlerts()
            }
            _currentImpact.value = null
        }
    }
    
    /**
     * 触发警报
     */
    private fun triggerAlert(impact: EarthquakeImpact) {
        _isAlertActive.value = true
        
        // 播放警报声音
        playAlertSound()
        
        // 震动
        vibrate()
        
        // 倒计时到地震到达
        startCountdown(impact)
    }
    
    /**
     * 播放警报声音
     */
    private fun playAlertSound() {
        // 实现警报声音播放
        // 这里应该使用MediaPlayer播放警报声音
    }
    
    /**
     * 震动
     */
    private fun vibrate() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
        }
    }
    
    /**
     * 开始倒计时
     */
    private fun startCountdown(impact: EarthquakeImpact) {
        alertJob?.cancel()
        alertJob = viewModelScope.launch {
            var secondsRemaining = impact.secondsUntilArrival
            while (secondsRemaining > 0) {
                delay(1000)
                secondsRemaining--
                
                // 更新当前影响的剩余时间
                _currentImpact.value = impact.copy(secondsUntilArrival = secondsRemaining)
                
                // 如果剩余时间为0，停止警报
                if (secondsRemaining <= 0) {
                    stopAlerts()
                }
            }
        }
    }
    
    /**
     * 停止警报
     */
    private fun stopAlerts() {
        _isAlertActive.value = false
        alertJob?.cancel()
        alertJob = null
        
        // 停止震动
        vibrator?.cancel()
        
        // 停止警报声音
        mediaPlayer?.stop()
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        // 释放媒体播放器资源
        mediaPlayer?.release()
        mediaPlayer = null
        
        // 取消所有警报任务
        alertJob?.cancel()
        alertJob = null
        
        // 停止定位更新
        locationService.stopLocationUpdates()
        
        // 调用仓库的清理方法
        repository.cleanup()
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
    
    /**
     * 显式刷新地震数据（用于用户手动刷新操作）
     */
    fun refreshEarthquakes() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // 打印明确的日志，表明正在尝试获取中国地震台网和四川地震局数据
                Log.d("EarthquakeViewModel", "===== 用户触发刷新：开始获取中国地震台网和四川地震局数据 =====")
                
                // 清空现有数据，以确保能看到新数据的来源
                Log.d("EarthquakeViewModel", "清空现有数据...")
                (repository as? com.example.eewapp.repository.EarthquakeRepository)?.clearExistingData()
                
                // 首先尝试获取中国地震台网数据
                Log.d("EarthquakeViewModel", "开始获取中国地震台网数据...")
                val cencSuccess = (repository as? com.example.eewapp.repository.EarthquakeRepository)?.fetchChinaEarthquakeNetworkData() ?: false
                Log.d("EarthquakeViewModel", "中国地震台网数据刷新${if (cencSuccess) "成功" else "失败"}")
                
                // 稍微延迟，确保数据写入
                delay(1000)
                
                // 然后使用直接 HTTP 方法获取四川地震局数据
                Log.d("EarthquakeViewModel", "开始获取四川地震局数据...")
                val sichuanSuccess = (repository as? com.example.eewapp.repository.EarthquakeRepository)?.fetchSichuanDataDirectly() ?: false
                Log.d("EarthquakeViewModel", "四川地震局数据刷新${if (sichuanSuccess) "成功" else "失败"}")
                
                // 等待足够长的时间，让数据有机会加载和刷新到UI
                Log.d("EarthquakeViewModel", "等待数据处理完成...")
                delay(5000)
                
                // 检查是否成功获取到数据
                val earthquakesCount = recentEarthquakes.value.size
                Log.d("EarthquakeViewModel", "刷新后，当前地震数量: $earthquakesCount")
                
                if (earthquakesCount == 0) {
                    Log.w("EarthquakeViewModel", "刷新后未获取到任何地震数据")
                    _error.value = "未能获取到任何地震数据，请稍后再试"
                } else {
                    val cencCount = recentEarthquakes.value.count { it.title.contains("[中国地震台网") }
                    val sichuanCount = recentEarthquakes.value.count { 
                        it.title.contains("[四川地震局]") || 
                        it.title.contains("[四川地震局数据]") || 
                        it.title.contains("[四川地震局测试]")
                    }
                    
                    Log.d("EarthquakeViewModel", "刷新成功，获取到 $earthquakesCount 条地震数据" +
                            "（中国地震台网: $cencCount 条，四川地震局: $sichuanCount 条）")
                    
                    // 打印前三条数据的标题以便调试
                    if (recentEarthquakes.value.isNotEmpty()) {
                        Log.d("EarthquakeViewModel", "第一条地震: ${recentEarthquakes.value.firstOrNull()?.title}")
                        if (recentEarthquakes.value.size > 1) {
                            Log.d("EarthquakeViewModel", "第二条地震: ${recentEarthquakes.value[1].title}")
                        }
                        if (recentEarthquakes.value.size > 2) {
                            Log.d("EarthquakeViewModel", "第三条地震: ${recentEarthquakes.value[2].title}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EarthquakeViewModel", "刷新地震数据失败: ${e.message}", e)
                _error.value = "刷新地震数据失败: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.d("EarthquakeViewModel", "===== 用户触发刷新完成 =====")
            }
        }
    }
    
    fun simulateEarthquake(epicenter: LatLng, magnitude: Double, depth: Double) {
        // Simplified: just set _currentImpact for the card to show
        // Actual simulation logic creating an Earthquake and EarthquakeImpact object is needed here
        // For now, assuming it correctly populates _currentImpact.value
        // Example (needs proper implementation):
        /*
        viewModelScope.launch {
            val simulatedEarthquake = Earthquake(
                id = "simulated-" + System.currentTimeMillis(),
                time = Date(),
                latitude = epicenter.latitude,
                longitude = epicenter.longitude,
                depth = depth,
                magnitude = magnitude,
                place = "Simulated Earthquake",
                title = "[Simulated] Magnitude $magnitude event"
            )
            val impact = calculateImpact(simulatedEarthquake, userLocation.value) // calculateImpact would be a helper
            _currentImpact.value = impact
            if (impact != null && impact.intensity.level >= ShakingIntensity.LEVEL_4.level) {
                triggerAlert(impact)
            }
        }
        */
        Log.d("EarthquakeViewModel", "Simulate earthquake called, _currentImpact should be set to show card.")
        // Ensure your actual simulation logic correctly updates _currentImpact
    }
    
    fun cancelSimulation() {
        viewModelScope.launch {
            _currentImpact.value = null // This will hide the card
            stopAlerts() // Stop any ongoing alerts from simulation
            Log.d("EarthquakeViewModel", "Simulation cancelled, _currentImpact set to null.")
        }
    }
} 