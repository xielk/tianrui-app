package xiaochao.com.feature.f2.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xiaochao.com.R

@Composable
fun F2StatusIconRow(
    signalStrengthLevel: Int,
    bleConnected: Boolean,
    bleSystemConnected: Boolean,
    bleConnecting: Boolean,
    show4gIcon: Boolean,
    onScanClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onBlePairedClick: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        CircleIcon(iconRes = R.drawable.f2_scan, onClick = onScanClick, imageSize = 22.dp)
        if (show4gIcon) {
            CircleIcon(iconRes = signalRes(signalStrengthLevel), onClick = {}, imageSize = 28.dp)
        }
        CircleIcon(
            iconRes = bleRes(bleConnected = bleConnected, bleConnecting = bleConnecting),
            onClick = onBluetoothClick,
            imageSize = 22.dp,
            blinking = bleConnecting,
        )
        if (bleSystemConnected) {
            CircleIcon(iconRes = R.drawable.ble_paired, onClick = onBlePairedClick, imageSize = 40.dp)
        }
    }
}

@Composable
private fun CircleIcon(iconRes: Int, onClick: () -> Unit, imageSize: Dp, blinking: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "icon_blink")
    val blinkAlpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_alpha"
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(imageSize)
                .alpha(if (blinking) blinkAlpha.value else 1f)
        )
    }
}

private fun bleRes(bleConnected: Boolean, bleConnecting: Boolean): Int {
    return when {
        bleConnecting -> R.drawable.ble_connecting
        bleConnected -> R.drawable.ble_successful
        else -> R.drawable.ble_wait
    }
}

private fun signalRes(level: Int): Int {
    return when (level.coerceIn(0, 5)) {
        0 -> R.drawable.f2_signal0
        1 -> R.drawable.f2_signal1
        2 -> R.drawable.f2_signal2
        3 -> R.drawable.f2_signal3
        4 -> R.drawable.f2_signal4
        else -> R.drawable.f2_signal5
    }
}
