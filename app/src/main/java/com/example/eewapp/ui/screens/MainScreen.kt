package com.example.eewapp.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eewapp.data.Earthquake
import com.example.eewapp.ui.components.AlertBanner
import com.example.eewapp.ui.components.EarthquakeList
import com.example.eewapp.ui.components.EarthquakeAMap
import com.example.eewapp.viewmodel.EarthquakeViewModel
import com.example.eewapp.viewmodel.EscapeNavigationViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.foundation.layout.WindowInsets

/**
 * 应用主屏幕
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: EarthquakeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    
    // 逃生导航ViewModel
    val escapeNavigationViewModel = remember { EscapeNavigationViewModel(context) }
    
    // 状态
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val earthquakes by viewModel.recentEarthquakes.collectAsStateWithLifecycle()
    val significantEarthquakes by viewModel.significantEarthquakes.collectAsStateWithLifecycle()
    val currentImpact by viewModel.currentImpact.collectAsStateWithLifecycle()
    val isAlertActive by viewModel.isAlertActive.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    // 逃生导航状态
    val escapeNavigationState by escapeNavigationViewModel.navigationState.collectAsStateWithLifecycle()
    val escapeNavigationError by escapeNavigationViewModel.errorMessage.collectAsStateWithLifecycle()
    
    // UI状态
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 位置权限
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    // 显示错误消息（如果有）
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    
    // 显示逃生导航错误消息
    LaunchedEffect(escapeNavigationError) {
        escapeNavigationError?.let {
            snackbarHostState.showSnackbar("逃生导航：$it")
            escapeNavigationViewModel.clearError()
        }
    }
    
    // 主界面
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 根据选中的标签显示不同内容
        when (selectedTab) {
            0 -> MapTab(
                userLocation = userLocation,
                earthquakes = earthquakes,
                significantEarthquakes = significantEarthquakes,
                currentImpact = currentImpact,
                escapeNavigationState = escapeNavigationState,
                onEscapeNavigationStart = { userLoc ->
                    escapeNavigationViewModel.startEscapeNavigation(userLoc)
                },
                onSafetyLocationSelected = { location ->
                    escapeNavigationViewModel.selectSafetyLocation(location)
                },
                onNavigationStart = { userLoc, destination ->
                    escapeNavigationViewModel.startNavigation(userLoc, destination)
                },
                onNavigationStop = {
                    escapeNavigationViewModel.stopNavigation()
                },
                onEscapeNavigationDismiss = {
                    escapeNavigationViewModel.dismissEscapeNavigation()
                }
            )
            1 -> ListTab(
                earthquakes = earthquakes,
                significantEarthquakes = significantEarthquakes,
                onEarthquakeClick = { /* NO-OP */ },
                onRefresh = { viewModel.refreshEarthquakes() },
                isLoading = isLoading
            )
            2 -> SafetyTab()
            3 -> SettingsScreen()
        }
        
        // 权限请求UI
        if (!locationPermissions.allPermissionsGranted) {
            PermissionRequest(
                onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // 底部导航栏
        BottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = { newTab -> 
                selectedTab = newTab
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // 警告横幅
        if (isAlertActive && currentImpact != null) {
            AlertBanner(
                isActive = isAlertActive,
                impact = currentImpact,
                onDismiss = { /* 关闭警报 */ },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // 消息提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp) // 避免被底部导航栏遮挡
        )
    }
}

/**
 * 底部导航栏
 */
@Composable
fun BottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color.White // 主要背景色

    BottomAppBar(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        containerColor = BackgroundPrimary, // 白色背景
        contentColor = TextPrimary
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = "地图") },
            label = { Text("地图") },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = RedEmphasis,
                selectedTextColor = RedEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.List, contentDescription = "地震") },
            label = { Text("地震") },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = RedEmphasis,
                selectedTextColor = RedEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Info, contentDescription = "指南") },
            label = { Text("指南") },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = RedEmphasis,
                selectedTextColor = RedEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
            label = { Text("设置") },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = RedEmphasis,
                selectedTextColor = RedEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
    }
}

/**
 * 地图标签内容
 */
@Composable
fun MapTab(
    userLocation: com.example.eewapp.data.UserLocation?,
    earthquakes: List<Earthquake>,
    significantEarthquakes: List<com.example.eewapp.data.EarthquakeImpact>,
    currentImpact: com.example.eewapp.data.EarthquakeImpact?,
    escapeNavigationState: com.example.eewapp.data.EscapeNavigationState,
    onEscapeNavigationStart: (com.example.eewapp.data.UserLocation) -> Unit,
    onSafetyLocationSelected: (com.example.eewapp.data.SafetyLocation) -> Unit,
    onNavigationStart: (com.example.eewapp.data.UserLocation, com.example.eewapp.data.SafetyLocation) -> Unit,
    onNavigationStop: () -> Unit,
    onEscapeNavigationDismiss: () -> Unit
) {
    Log.d("MapTab", "Rendering MapTab")
    Log.d("MapTab", "UserLocation: $userLocation")
    Log.d("MapTab", "Earthquakes count: ${earthquakes.size}")
    
    Box(modifier = Modifier.fillMaxSize()) {
        EarthquakeAMap(
            userLocation = userLocation,
            earthquakes = earthquakes,
            significantEarthquakes = significantEarthquakes,
            currentImpact = currentImpact,
            escapeNavigationState = escapeNavigationState,
            onEscapeNavigationStart = onEscapeNavigationStart,
            onSafetyLocationSelected = onSafetyLocationSelected,
            onNavigationStart = onNavigationStart,
            onNavigationStop = onNavigationStop,
            onEscapeNavigationDismiss = onEscapeNavigationDismiss,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 列表标签内容
 */
@Composable
fun ListTab(
    earthquakes: List<Earthquake>,
    significantEarthquakes: List<com.example.eewapp.data.EarthquakeImpact>,
    onEarthquakeClick: (Earthquake) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    EarthquakeList(
        earthquakes = earthquakes,
        significantEarthquakes = significantEarthquakes,
        onEarthquakeClick = onEarthquakeClick,
        onRefresh = onRefresh,
        isLoading = isLoading,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 安全标签内容
 */
@Composable
fun SafetyTab() {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color.White // 主要背景色

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 72.dp)
    ) {
        Text(
            text = "地震安全指南",
            style = MaterialTheme.typography.headlineMedium,
            color = RedEmphasis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "地震前准备：",
            style = MaterialTheme.typography.titleMedium,
            color = RedEmphasis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "• 制定应急计划并与家人一起练习\n" +
                   "• 准备应急包，包含食物、水和必要物品\n" +
                   "• 固定重家具和可能坠落的物品\n" +
                   "• 了解如何关闭燃气、水和电",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "地震发生时：",
            style = MaterialTheme.typography.titleMedium,
            color = RedEmphasis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "• 趴下、掩护、抓牢\n" +
                   "• 躲在坚固的桌子下或靠近内墙\n" +
                   "• 远离窗户、外墙和可能坠落的物品\n" +
                   "• 如果在户外，移动到远离建筑物和电线的开阔区域",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "地震后处理：",
            style = MaterialTheme.typography.titleMedium,
            color = RedEmphasis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "• 检查自己和他人是否受伤\n" +
                   "• 警惕余震\n" +
                   "• 收听紧急广播获取信息\n" +
                   "• 检查燃气泄漏、水和电力损坏\n" +
                   "• 如果建筑物不安全，请撤离",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

/**
 * 权限请求组件
 */
@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val RedEmphasis = Color(0xFF68C29F) // 绿色强调色，原为红色(0xFFD32F2F)
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color.White // 主要背景色

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(BackgroundPrimary)
            .padding(16.dp)
    ) {
        Text(
            text = "需要位置权限",
            style = MaterialTheme.typography.titleLarge,
            color = RedEmphasis,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "此应用需要位置权限才能显示地震与您的相对位置。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        androidx.compose.material3.Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 8.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = RedEmphasis
            )
        ) {
            Text("授予权限")
        }
    }
} 