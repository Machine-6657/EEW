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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import com.example.eewapp.R

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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
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
                },
                onNavigateToGuide = { selectedTab = 2 }
            )
            1 -> ListTab(
                earthquakes = earthquakes,
                significantEarthquakes = significantEarthquakes,
                onEarthquakeClick = { /* NO-OP */ },
                onRefresh = { viewModel.refreshEarthquakes() },
                isLoading = isLoading
            )
            2 -> SafetyGuideScreen()
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
                .padding(bottom = 60.dp) // 避免被底部导航栏遮挡
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
    val BlueEmphasis = Color(0xFF6685EC) // 新的蓝色强调色
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color(0xFFF0F2F5) // 更明显的浅灰色背景

    BottomAppBar(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        containerColor = BackgroundPrimary,
        contentColor = TextPrimary
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { 
                Icon(
                    Icons.Default.Public, 
                    contentDescription = stringResource(id = R.string.bottom_nav_map),
                    modifier = Modifier.size(24.dp)
                ) 
            },
            label = { 
                Text(
                    stringResource(id = R.string.bottom_nav_map),
                    fontSize = 12.sp,
                    modifier = Modifier.offset(y = (-7).dp)
                ) 
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = BlueEmphasis,
                selectedTextColor = BlueEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { 
                Icon(
                    Icons.Default.Sort, 
                    contentDescription = stringResource(id = R.string.bottom_nav_earthquakes),
                    modifier = Modifier.size(24.dp)
                ) 
            },
            label = { 
                Text(
                    stringResource(id = R.string.bottom_nav_earthquakes),
                    fontSize = 12.sp,
                    modifier = Modifier.offset(y = (-7).dp)
                ) 
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = BlueEmphasis,
                selectedTextColor = BlueEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { 
                Icon(
                    Icons.Default.ImportContacts, 
                    contentDescription = stringResource(id = R.string.bottom_nav_guide),
                    modifier = Modifier.size(24.dp)
                ) 
            },
            label = { 
                Text(
                    stringResource(id = R.string.bottom_nav_guide),
                    fontSize = 12.sp,
                    modifier = Modifier.offset(y = (-7).dp)
                ) 
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = BlueEmphasis,
                selectedTextColor = BlueEmphasis,
                indicatorColor = Color.Transparent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { 
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = stringResource(id = R.string.bottom_nav_settings),
                    modifier = Modifier.size(24.dp)
                ) 
            },
            label = { 
                Text(
                    stringResource(id = R.string.bottom_nav_settings),
                    fontSize = 12.sp,
                    modifier = Modifier.offset(y = (-7).dp)
                ) 
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = BlueEmphasis,
                selectedTextColor = BlueEmphasis,
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
    onEscapeNavigationDismiss: () -> Unit,
    onNavigateToGuide: () -> Unit
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
            onNavigateToGuide = onNavigateToGuide,
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
 * 安全标签内容 - 调用外部的SafetyGuideScreen
 */

/**
 * 权限请求组件
 */
@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 定义颜色常量
    val BlueEmphasis = Color(0xFF6685EC) // 新的蓝色强调色
    val TextPrimary = Color.Black // 主要文本颜色
    val TextSecondary = Color.DarkGray // 次要文本颜色
    val BackgroundPrimary = Color(0xFFF0F2F5) // 更明显的浅灰色背景

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(BackgroundPrimary)
            .padding(16.dp)
    ) {
        Text(
            text = "需要位置权限",
            style = MaterialTheme.typography.titleLarge,
            color = BlueEmphasis,
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
                containerColor = BlueEmphasis
            )
        ) {
            Text("授予权限")
        }
    }
} 