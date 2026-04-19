package xiaochao.com.feature.f2.ui

import android.util.Log
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xiaochao.com.feature.f2.presentation.MoveDeviceViewModel
import xiaochao.com.feature.f2.ui.components.F2StatusIconRow

@Composable
fun MoveDeviceScreen(
    vm: MoveDeviceViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateBleCalibration: () -> Unit,
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val vibrator = remember(context) { resolveMoveVibrator(context) }

    LaunchedEffect(uiState.tipMessage) {
        uiState.tipMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeTip()
        }
    }

    LaunchedEffect(Unit) {
        Log.d("move-device", "vibration collector started vibrator=${vibrator != null} hasVibrator=${vibrator?.hasVibrator() == true}")
        vm.vibrationEvents.collect { durationMs ->
            val hasVibrator = vibrator?.hasVibrator() == true
            if (hasVibrator) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs.toLong())
                }
            }
            Log.d("move-device", "vibrate event=${durationMs}ms hasVibrator=$hasVibrator")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F6FA), Color(0xFFECEFF5))))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = Color(0xFF0C1222),
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(2.dp)
                )
                Text(
                    text = "挪车",
                    color = Color(0xFF0C1222),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .background(Color.Transparent)
                        .padding(vertical = 6.dp)
                )
            }
            F2StatusIconRow(
                signalStrengthLevel = uiState.signalStrengthLevel,
                bleConnected = uiState.bleConnected,
                bleSystemConnected = uiState.bleSystemConnected,
                bleConnecting = uiState.bleConnecting,
                show4gIcon = false,
                onScanClick = {},
                onBluetoothClick = { vm.toggleBleConnection() },
                onBlePairedClick = onNavigateBleCalibration,
            )
        }

        MoveDirectionPanel(
            title = "前进",
            subtitle = "FORWARD CONTROL",
            blockColor = Color(0xFF8AA9D7),
            arrow = "↑",
            onPressStart = {
                Log.d("move-device", "forward pressed")
                vm.pressForwardStart()
            },
            onPressEnd = {
                Log.d("move-device", "forward released")
                vm.pressEnd()
            },
        )

        Button(
            onClick = { vm.toggleMoveMode() },
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.modeEnabled) Color(0xFF2F9B63) else Color(0xFFE1E5EA),
                contentColor = if (uiState.modeEnabled) Color.White else Color(0xFF2D3445),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = if (uiState.modeEnabled) "关闭挪车模式" else "启动挪车模式",
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
            )
        }

        Text(
            text = "开启挪车模式后，按住前进或后退按钮即可微移车辆。\n松手立即停止。请确保周围环境安全。\n。",
            color = Color(0xFF5B667A),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )

        MoveDirectionPanel(
            title = "后退",
            subtitle = "REVERSE CONTROL",
            blockColor = Color(0xFFD2B79F),
            arrow = "↓",
            onPressStart = {
                Log.d("move-device", "backward pressed")
                vm.pressBackwardStart()
            },
            onPressEnd = {
                Log.d("move-device", "backward released")
                vm.pressEnd()
            },
        )
    }
}

private fun resolveMoveVibrator(context: android.content.Context): Vibrator? {
    @Suppress("DEPRECATION")
    return context.getSystemService(Vibrator::class.java)
}

@Composable
private fun MoveDirectionPanel(
    title: String,
    subtitle: String,
    blockColor: Color,
    arrow: String,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "move_direction_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .scale(scale)
            .background(blockColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPressStart()
                        tryAwaitRelease()
                        pressed = false
                        onPressEnd()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = arrow, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(text = title, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = Color(0xFFE8EEF8), fontSize = 12.sp)
        }
    }
}
