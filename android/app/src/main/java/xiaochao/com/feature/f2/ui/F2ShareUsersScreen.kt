package xiaochao.com.feature.f2.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xiaochao.com.R
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepositoryImpl
import xiaochao.com.data.api.SharedUserItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon

@Composable
fun F2ShareUsersScreen(
    deviceKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ApiRepositoryImpl() }

    val users = remember { mutableStateListOf<SharedUserItem>() }
    var loading by remember { mutableStateOf(false) }
    var addPhone by remember { mutableStateOf("") }
    var showOwnerDialog by remember { mutableStateOf(false) }
    var newOwnerPhone by remember { mutableStateOf("") }

    fun isValidPhone(value: String): Boolean = Regex("^1[3-9]\\d{9}$").matches(value)

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun formatPhone(phone: String): String {
        return if (phone.length >= 11) {
            phone.substring(0, 3) + "****" + phone.substring(7)
        } else {
            phone
        }
    }

    fun loadUsers() {
        scope.launch {
            loading = true
            when (val result = repo.fetchSharedUsers(deviceKey)) {
                is AppResult.Success -> {
                    users.clear()
                    users.addAll(result.data)
                }

                is AppResult.Error -> toast(result.message.ifBlank { "获取用户列表失败" })
            }
            loading = false
        }
    }

    LaunchedEffect(deviceKey) {
        loadUsers()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.clickable { onBack() }.size(20.dp)
                )
                Text(
                    text = "用车人列表",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text("添加用车用户", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = addPhone,
                        onValueChange = { addPhone = it.take(11) },
                        singleLine = true,
                        placeholder = { Text("请输入手机号码") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (!isValidPhone(addPhone)) {
                                toast("请输入正确的手机号码")
                                return@Button
                            }
                            scope.launch {
                                when (val result = repo.shareDevice(deviceKey, addPhone)) {
                                    is AppResult.Success -> {
                                        addPhone = ""
                                        toast("添加成功,请对方重新登录")
                                        loadUsers()
                                    }

                                    is AppResult.Error -> toast(result.message.ifBlank { "添加失败" })
                                }
                            }
                        },
                        enabled = !loading,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECEDEF), contentColor = Color(0xFF8E9299)),
                        modifier = Modifier
                            .height(56.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Text("添加", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text("用车人", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (loading) {
                    Text("加载中...", color = Color(0xFF8E9299), modifier = Modifier.padding(vertical = 12.dp))
                } else if (users.isEmpty()) {
                    Text("暂无用车人", color = Color(0xFF8E9299), modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(users, key = { it.memberId }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1485FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (user.isOwner) "主" else "用",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp
                                    )
                                }
                                Spacer(modifier = Modifier.size(14.dp))
                                Text(
                                    text = if (user.isOwner) formatPhone(user.phone) else user.phone,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp
                                )

                                if (!user.isOwner) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                when (val result = repo.removeSharedUser(deviceKey, user.memberId)) {
                                                    is AppResult.Success -> {
                                                        toast("删除成功")
                                                        loadUsers()
                                                    }

                                                    is AppResult.Error -> toast(result.message.ifBlank { "删除失败" })
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                                    ) {
                                        Text("删除", fontSize = 14.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { showOwnerDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F8BFF), contentColor = Color.White)
                                    ) {
                                        Text("更换车主", fontSize = 14.sp)
                                    }
                                }
                            }

                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFE7E9ED))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "分享后，对方可在APP中查看和控制此车辆",
                color = Color(0xFF9CA2A9),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showOwnerDialog) {
        AlertDialog(
            onDismissRequest = {
                showOwnerDialog = false
                newOwnerPhone = ""
            },
            title = { Text("更换车主") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newOwnerPhone,
                        onValueChange = { newOwnerPhone = it.take(11) },
                        singleLine = true,
                        placeholder = { Text("请输入新车主手机号") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.dp, Color.Transparent)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!isValidPhone(newOwnerPhone)) {
                        toast("请输入正确的手机号码")
                        return@TextButton
                    }
                    scope.launch {
                        when (val result = repo.changeOwner(deviceKey, newOwnerPhone)) {
                            is AppResult.Success -> {
                                toast("更换车主成功")
                                showOwnerDialog = false
                                newOwnerPhone = ""
                                loadUsers()
                            }

                            is AppResult.Error -> toast(result.message.ifBlank { "更换车主失败" })
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOwnerDialog = false
                    newOwnerPhone = ""
                }) {
                    Text("取消")
                }
            }
        )
    }
}
