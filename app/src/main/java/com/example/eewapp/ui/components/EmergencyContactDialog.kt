package com.example.eewapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 紧急联系人数据类
 */
data class EmergencyContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val type: EmergencyContactType
)

/**
 * 紧急联系人类型
 */
enum class EmergencyContactType(val displayName: String) {
    FIRE_RESCUE("消防救援"),
    POLICE("公安部门"),
    MEDICAL("医疗急救"),
    EARTHQUAKE_BUREAU("地震局"),
    EMERGENCY_MANAGEMENT("应急管理"),
    RED_CROSS("红十字会"),
    CIVIL_DEFENSE("民防部门")
}

/**
 * 紧急联系弹窗组件
 */
@Composable
fun EmergencyContactDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 预定义的紧急联系人列表
    val emergencyContacts = remember {
        listOf(
            EmergencyContact(
                id = "fire_rescue",
                name = "杭州人防指挥信息保障中心",
                phoneNumber = "0571-87265799",
                type = EmergencyContactType.FIRE_RESCUE
            ),
            EmergencyContact(
                id = "red_cross",
                name = "红十字救援队",
                phoneNumber = "0571-87265773",
                type = EmergencyContactType.RED_CROSS
            ),
            EmergencyContact(
                id = "civil_defense",
                name = "民间组织救援小组",
                phoneNumber = "13486660376",
                type = EmergencyContactType.CIVIL_DEFENSE
            ),
            EmergencyContact(
                id = "emergency_119",
                name = "消防救援",
                phoneNumber = "119",
                type = EmergencyContactType.FIRE_RESCUE
            ),
            EmergencyContact(
                id = "police_110",
                name = "公安报警",
                phoneNumber = "110",
                type = EmergencyContactType.POLICE
            ),
            EmergencyContact(
                id = "medical_120",
                name = "医疗急救",
                phoneNumber = "120",
                type = EmergencyContactType.MEDICAL
            ),
            EmergencyContact(
                id = "earthquake_bureau",
                name = "地震局应急热线",
                phoneNumber = "12322",
                type = EmergencyContactType.EARTHQUAKE_BUREAU
            )
        )
    }
    
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // 标题栏
                    EmergencyContactHeader(onDismiss = onDismiss)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 联系人列表
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(emergencyContacts) { contact ->
                            EmergencyContactItem(
                                contact = contact,
                                onCall = { makePhoneCall(context, contact.phoneNumber) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 取消按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E0),
                            contentColor = Color(0xFF666666)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧急联系弹窗标题栏
 */
@Composable
private fun EmergencyContactHeader(
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "选择呼叫单位",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(32.dp)
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 单个紧急联系人条目
 */
@Composable
private fun EmergencyContactItem(
    contact: EmergencyContact,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：联系人信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = contact.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = contact.phoneNumber,
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }
            
            // 右侧：拨打电话按钮
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(0xFF4285F4),
                        shape = CircleShape
                    )
                    .clickable { onCall() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "拨打电话",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 拨打电话功能
 */
private fun makePhoneCall(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // 处理异常，例如没有电话应用
        e.printStackTrace()
    }
} 