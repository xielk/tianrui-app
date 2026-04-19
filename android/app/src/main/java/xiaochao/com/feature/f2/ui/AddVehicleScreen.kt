package xiaochao.com.feature.f2.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.launch
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepositoryImpl
import xiaochao.com.data.session.AppSessionStore

@Composable
fun AddVehicleScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ApiRepositoryImpl() }
    val scope = rememberCoroutineScope()
    var deviceKey by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var autoLaunched by remember { mutableStateOf(false) }
    var showCameraPermissionIntro by remember { mutableStateOf(false) }
    var showCameraPermissionDenied by remember { mutableStateOf(false) }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED
    }

    fun bindDeviceKey(key: String) {
        if (key.isBlank()) {
            Toast.makeText(context, "请输入设备号", Toast.LENGTH_SHORT).show()
            return
        }
        if (loading) return
        loading = true
        scope.launch {
            when (val bindResult = repository.bindDevice(key)) {
                is AppResult.Success -> {
                    repository.setDefaultDevice(key)
                    AppSessionStore.setLastDeviceKey(key)
                    Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                    onDone()
                }
                is AppResult.Error -> {
                    Toast.makeText(context, bindResult.message.ifBlank { "添加失败" }, Toast.LENGTH_SHORT).show()
                }
            }
            loading = false
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val content = result.data?.getStringExtra("scan_result")?.trim().orEmpty()
            if (content.isNotEmpty()) {
                deviceKey = content
                bindDeviceKey(content)
            }
        } else {
            Toast.makeText(context, "已退出扫码，请手动输入设备号", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanLauncher.launch(Intent(context, ScannerActivity::class.java))
        } else {
            showCameraPermissionDenied = true
            Toast.makeText(context, "未授予摄像头权限，无法使用扫码功能", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchScannerWithPermission() {
        if (hasCameraPermission()) {
            scanLauncher.launch(Intent(context, ScannerActivity::class.java))
        } else {
            showCameraPermissionIntro = true
        }
    }

    LaunchedEffect(Unit) {
        if (!autoLaunched) {
            autoLaunched = true
            launchScannerWithPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "添加车辆", fontSize = 24.sp, color = Color.Black)
        Text(text = "请输入设备号进行绑定（与 UniApp 手动添加流程一致）", fontSize = 13.sp, color = Color(0xFF667085))

        OutlinedTextField(
            value = deviceKey,
            onValueChange = { deviceKey = it.trim() },
            singleLine = true,
            placeholder = { Text("请输入设备号") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                launchScannerWithPermission()
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101828)),
        ) {
            Text("扫码添加")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("返回")
            }
            Button(
                onClick = {
                    bindDeviceKey(deviceKey)
                },
                enabled = !loading,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            ) {
                Text(if (loading) "绑定中..." else "立即添加")
            }
        }
    }

    if (showCameraPermissionIntro) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionIntro = false },
            title = { Text("需要摄像头权限") },
            text = { Text("扫码添加车辆需要摄像头权限。确认后将弹出系统权限申请。") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraPermissionIntro = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraPermissionIntro = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCameraPermissionDenied) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDenied = false },
            title = { Text("摄像头权限被拒绝") },
            text = { Text("未授予摄像头权限，扫码功能不可用。可前往系统设置手动开启权限。") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraPermissionDenied = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraPermissionDenied = false }) {
                    Text("知道了")
                }
            }
        )
    }
}
