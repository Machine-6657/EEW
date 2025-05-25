package com.example.eewapp

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.eewapp.api.AMapWebRouteService
import com.example.eewapp.ui.screens.MainScreen
import com.example.eewapp.ui.theme.EEWappTheme
import com.example.eewapp.utils.AMapHelper
import com.example.eewapp.viewmodel.EarthquakeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val viewModel: EarthquakeViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 确保内容不会延伸到导航栏区域
        window.setDecorFitsSystemWindows(true)
        
        // 初始化高德地图
        AMapHelper.init(this)
        
        // 测试Web API配置
        testWebApiConfiguration()
        
        setContent {
            EEWappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    private fun testWebApiConfiguration() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MainActivity", "开始测试Web API配置...")
                val webRouteService = AMapWebRouteService(this@MainActivity)
                val isConfigurationValid = webRouteService.testApiConfiguration()
                
                Log.d("MainActivity", "Web API配置测试结果: ${if (isConfigurationValid) "成功" else "失败"}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Web API配置测试异常", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
}