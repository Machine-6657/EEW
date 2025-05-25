plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "1.9.21"
}

android {
    namespace = "com.example.eewapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.eewapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    
    // 解决高德地图依赖冲突问题
    configurations.all {
        resolutionStrategy {
            force("com.amap.api:3dmap:9.7.0")
            force("com.amap.api:search:9.7.0")
            force("com.amap.api:location:6.4.0")
        }
    }
    
    // 允许重复类
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += setOf(
                "**/*.class",
                "**/libc++_shared.so"
            )
        }
        
        dex {
            useLegacyPackaging = true
        }
    }
    
    // Add this to handle duplicate classes
    configurations.all {
        exclude(group = "com.amap.api", module = "3dmap")
        exclude(group = "com.amap.api", module = "location")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    
    // 添加Material Icons扩展库，包含所有图标
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    
    // 高德地图依赖 - 使用具体版本号（注释掉，改用本地JAR文件）
    // implementation("com.amap.api:3dmap:9.7.0") {
    //     exclude(group = "com.amap.api", module = "location")
    // }
    // implementation("com.amap.api:search:9.7.0")
    // implementation("com.amap.api:location:6.4.0")
    
    // 添加高德地图的本地JAR文件
    implementation(files("libs/AMap3DMap_10.1.200_AMapSearch_9.7.4_AMapLocation_6.4.9_20241226.jar"))
    
    // JSON序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}