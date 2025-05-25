#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
高德地图Web API测试脚本
参考：https://www.cnblogs.com/yangxiao-/p/14585450.html
"""

import requests
import json

# 配置区域 - Web服务API密钥 (EEW-地震预警，无安全密钥)
API_KEY = "16efc0a1537f18feac7c4d6a5f45d5a7"  # Web服务API密钥 (无安全密钥)

# 测试用的起点和终点（成都天府广场到四川大学）
ORIGIN = "104.0658,30.6598"  # 成都天府广场
DESTINATION = "104.0831,30.6303"  # 四川大学

def test_simple_walking_route():
    """
    测试简单的步行路径规划API (v3版本，无数字签名，最基础参数)
    """
    print("=== 高德地图步行路径规划API测试 (最简化版本，无数字签名) ===")
    print(f"起点: {ORIGIN}")
    print(f"终点: {DESTINATION}")
    print(f"API密钥: {API_KEY[:8]}...{API_KEY[-4:]}")
    print()
    
    # 使用v3版本API，最基础的参数
    url = "https://restapi.amap.com/v3/direction/walking"
    params = {
        "key": API_KEY,
        "origin": ORIGIN,
        "destination": DESTINATION,
        "output": "json"
        # 不添加任何其他参数，包括数字签名
    }
    
    print(f"请求URL: {url}")
    print(f"请求参数: {params}")
    print()
    
    try:
        print("正在发送API请求...")
        response = requests.get(url, params=params, timeout=30)
        
        print(f"HTTP状态码: {response.status_code}")
        print()
        
        if response.status_code == 200:
            data = response.json()
            print("=== API响应内容 ===")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            
            # 分析响应
            status = data.get("status")
            if status == "1":
                print("\n✅ API调用成功！")
                
                # 解析路径信息
                route = data.get("route", {})
                paths = route.get("paths", [])
                
                if paths:
                    path = paths[0]
                    distance = path.get("distance", "未知")
                    duration = path.get("duration", "未知")
                    steps = path.get("steps", [])
                    
                    print(f"📏 总距离: {distance}米")
                    print(f"⏱️ 预计时间: {int(duration)//60}分钟{int(duration)%60}秒")
                    print(f"🚶 路径步骤: {len(steps)}个")
                    
                    # 显示详细步骤
                    for i, step in enumerate(steps[:5]):  # 只显示前5个步骤
                        instruction = step.get("instruction", "无指令")
                        step_distance = step.get("distance", "0")
                        print(f"   步骤{i+1}: {instruction} ({step_distance}米)")
                    
                    if len(steps) > 5:
                        print(f"   ... 还有{len(steps)-5}个步骤")
                        
                    return True
                else:
                    print("⚠️ 未找到路径数据")
                    return False
            else:
                print("❌ API调用失败")
                info = data.get("info", "未知错误")
                infocode = data.get("infocode", "未知")
                print(f"错误信息: {info}")
                print(f"错误代码: {infocode}")
                
                # 常见错误解释
                if infocode == "10001":
                    print("💡 解决方案: 检查Web服务API密钥是否正确")
                elif infocode == "10002":
                    print("💡 解决方案: 检查密钥是否有步行路径规划权限")
                elif infocode == "10007":
                    print("💡 解决方案: 可能需要在控制台关闭数字签名验证")
                
                return False
        else:
            print(f"❌ HTTP请求失败: {response.status_code}")
            print(f"响应内容: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ 请求异常: {e}")
        return False

def test_geocoding():
    """
    测试地理编码API (将地址转换为坐标)
    """
    print("\n=== 地理编码API测试 ===")
    
    url = "https://restapi.amap.com/v3/geocode/geo"
    params = {
        "key": API_KEY,
        "address": "成都市天府广场",
        "output": "json"
    }
    
    try:
        response = requests.get(url, params=params, timeout=30)
        if response.status_code == 200:
            data = response.json()
            print("地理编码响应:")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            
            if data.get("status") == "1":
                geocodes = data.get("geocodes", [])
                if geocodes:
                    location = geocodes[0].get("location", "")
                    print(f"✅ 地理编码成功: {location}")
                    return True
            
            print(f"❌ 地理编码失败: {data.get('info', '未知错误')}")
            return False
        else:
            print(f"❌ HTTP请求失败: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ 请求异常: {e}")
        return False

if __name__ == "__main__":
    print("高德地图Web API测试")
    print("参考: https://www.cnblogs.com/yangxiao-/p/14585450.html")
    print("=" * 50)
    
    # 先测试地理编码
    geocoding_success = test_geocoding()
    
    # 再测试路径规划
    routing_success = test_simple_walking_route()
    
    print("\n" + "=" * 50)
    print("测试结果总结:")
    print(f"地理编码API: {'✅ 成功' if geocoding_success else '❌ 失败'}")
    print(f"路径规划API: {'✅ 成功' if routing_success else '❌ 失败'}")
    
    if not (geocoding_success or routing_success):
        print("\n可能的解决方案:")
        print("1. 检查Web服务API密钥权限")
        print("2. 在控制台关闭数字签名验证")
        print("3. 确认选择了正确的服务平台类型")
        print("4. 检查API调用配额是否用完") 