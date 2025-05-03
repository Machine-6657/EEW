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
import com.example.eewapp.ui.screens.MainScreen
import com.example.eewapp.ui.theme.EEWappTheme
import com.example.eewapp.utils.AMapHelper
import com.example.eewapp.viewmodel.EarthquakeViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: EarthquakeViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 确保内容不会延伸到导航栏区域
        window.setDecorFitsSystemWindows(true)
        
        // 初始化高德地图
        AMapHelper.init(this)
        
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
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
}