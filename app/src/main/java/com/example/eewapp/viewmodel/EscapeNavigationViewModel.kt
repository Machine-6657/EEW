package com.example.eewapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eewapp.data.*
import com.example.eewapp.repository.SafetyLocationRepository
import com.example.eewapp.api.AMapSearchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 逃生导航ViewModel
 * 管理安全地点查找和导航功能
 */
class EscapeNavigationViewModel(context: Context) : ViewModel() {

    companion object {
        private const val TAG = "EscapeNavigationVM"
    }

    private val amapSearchService = AMapSearchService(context)
    private val safetyLocationRepository = SafetyLocationRepository(context, amapSearchService)

    // 逃生导航状态
    private val _navigationState = MutableStateFlow(EscapeNavigationState())
    val navigationState: StateFlow<EscapeNavigationState> = _navigationState.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误状态
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 启动逃生导航（查找附近安全地点）
     */
    fun startEscapeNavigation(userLocation: UserLocation) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                Log.d(
                    TAG,
                    "开始逃生导航，用户位置：${userLocation.latitude}, ${userLocation.longitude}"
                )

                // 查找附近的安全地点
                val safetyLocations =
                    safetyLocationRepository.findNearbySafetyLocations(userLocation)

                _navigationState.value = _navigationState.value.copy(
                    isActive = true,
                    safetyLocations = safetyLocations,
                    selectedDestination = null,
                    currentRoute = null,
                    isNavigating = false
                )

                Log.d(TAG, "找到 ${safetyLocations.size} 个安全地点")

            } catch (e: Exception) {
                Log.e(TAG, "启动逃生导航失败", e)
                _errorMessage.value = "查找安全地点失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 选择安全地点
     */
    fun selectSafetyLocation(location: SafetyLocation) {
        _navigationState.value = _navigationState.value.copy(
            selectedDestination = location
        )
        Log.d(TAG, "选择安全地点：${location.name}")
    }

    /**
     * 开始导航到指定安全地点
     */
    fun startNavigation(userLocation: UserLocation, destination: SafetyLocation) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                Log.d(TAG, "开始导航到：${destination.name}")

                // 计算导航路线
                val route = safetyLocationRepository.calculateRoute(userLocation, destination)

                if (route != null) {
                    _navigationState.value = _navigationState.value.copy(
                        selectedDestination = destination,
                        currentRoute = route,
                        isNavigating = true
                    )
                    Log.d(TAG, "导航路线计算成功，距离：${route.distanceInMeters}米")
                } else {
                    _errorMessage.value = "无法计算到 ${destination.name} 的路线"
                }

            } catch (e: Exception) {
                Log.e(TAG, "开始导航失败", e)
                _errorMessage.value = "导航启动失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 停止导航
     */
    fun stopNavigation() {
        _navigationState.value = _navigationState.value.copy(
            currentRoute = null,
            isNavigating = false
        )
        Log.d(TAG, "导航已停止")
    }

    /**
     * 关闭逃生导航
     */
    fun dismissEscapeNavigation() {
        _navigationState.value = EscapeNavigationState()
        _errorMessage.value = null
        Log.d(TAG, "逃生导航已关闭")
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 刷新安全地点列表
     */
    fun refreshSafetyLocations(userLocation: UserLocation) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                Log.d(TAG, "刷新安全地点列表")

                val safetyLocations =
                    safetyLocationRepository.findNearbySafetyLocations(userLocation)

                _navigationState.value = _navigationState.value.copy(
                    safetyLocations = safetyLocations
                )

                Log.d(TAG, "安全地点列表已刷新，找到 ${safetyLocations.size} 个地点")

            } catch (e: Exception) {
                Log.e(TAG, "刷新安全地点列表失败", e)
                _errorMessage.value = "刷新失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 更新用户位置（用于导航过程中的位置更新）
     */
    fun updateUserLocation(userLocation: UserLocation) {
        val currentRoute = _navigationState.value.currentRoute
        val destination = _navigationState.value.selectedDestination

        // 如果正在导航，重新计算路线
        if (currentRoute != null && destination != null) {
            viewModelScope.launch {
                try {
                    val updatedRoute =
                        safetyLocationRepository.calculateRoute(userLocation, destination)
                    if (updatedRoute != null) {
                        _navigationState.value = _navigationState.value.copy(
                            currentRoute = updatedRoute
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新导航路线失败", e)
                }
            }
        }
    }

    /**
     * 获取推荐的安全地点（返回距离最近的地点）
     */
    fun getRecommendedSafetyLocation(): SafetyLocation? {
        val safetyLocations = _navigationState.value.safetyLocations
        // 由于安全地点列表已经按距离排序，直接返回第一个（最近的）
        return safetyLocations.firstOrNull()
    }

    /**
     * 检查是否有紧急避难所
     */
    fun hasEmergencyShelter(): Boolean {
        return _navigationState.value.safetyLocations.any {
            it.type == SafetyLocationType.EMERGENCY_SHELTER
        }
    }
}
 