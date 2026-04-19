package xiaochao.com.feature.f2.ui.components

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xiaochao.com.R
import kotlin.math.roundToInt

@Composable
fun F2MainControlRow(
    isLocked: Boolean,
    isMuteEnabled: Boolean,
    isAutoSenseEnabled: Boolean,
    hasAnyChannel: Boolean,
    isF1Mode: Boolean = false,
    onAutoSenseClick: () -> Unit,
    onLockChanged: (Boolean) -> Unit,
    onMuteClick: () -> Unit,
    onFindBikeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val sideWidth = 58.dp
                val centerWidth = (maxWidth - sideWidth - sideWidth - 20.dp).coerceAtLeast(148.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    IconLabel(
                        icon = if (isAutoSenseEnabled) R.drawable.f2_auto_on else R.drawable.f2_auto_off,
                        label = if (isAutoSenseEnabled) "感应解锁开" else "感应解锁关",
                        onClick = onAutoSenseClick,
                        darkBackground = true,
                        containerWidth = sideWidth,
                        active = isAutoSenseEnabled,
                    )

                    LockSlider(
                        locked = isLocked,
                        enabled = hasAnyChannel,
                        showOfflineHint = !isF1Mode,
                        onLockChanged = onLockChanged,
                        modifier = Modifier
                            .width(centerWidth)
                            .height(56.dp)
                            .offset(y = (-4).dp),
                    )

                    IconLabel(
                        icon = if (isMuteEnabled) R.drawable.f2_mute else R.drawable.f2_unmute,
                        label = if (isMuteEnabled) "静音设防" else "有声设防",
                        onClick = onMuteClick,
                        darkBackground = true,
                        containerWidth = sideWidth,
                        active = isMuteEnabled,
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFE6E8ED))
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconLabel(icon = R.drawable.f2_find_bike, label = "寻车功能", onClick = onFindBikeClick, darkBackground = false, containerWidth = 72.dp, active = false)
                IconLabel(icon = R.drawable.f2_set, label = "设置", onClick = onSettingsClick, darkBackground = false, containerWidth = 72.dp, active = false)
                IconLabel(icon = R.drawable.f2_user, label = "用车人", onClick = onShareClick, darkBackground = false, containerWidth = 72.dp, active = false)
            }
        }
    }
}

/** 滑动解锁 / 上锁控件 */
@Composable
private fun LockSlider(
    locked: Boolean,
    enabled: Boolean,          // false = 无可用通道（F1 蓝牙未连接）
    showOfflineHint: Boolean = true,
    onLockChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)
    val thumbSizeDp: Dp = 44.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val hPadPx = with(density) { 6.dp.toPx() }

    // 背景渐变：disabled 时用灰色
    val bgBrush = when {
        !enabled -> Brush.horizontalGradient(listOf(Color(0xFF8A8E9A), Color(0xFF5B5F6A)))
        locked   -> Brush.horizontalGradient(listOf(Color(0xFF31355E), Color(0xFF0A0E1A)))
        else     -> Brush.horizontalGradient(listOf(Color(0xFFD10AF0), Color(0xFF781AD7)))
    }

    // offsetX 状态由 Animatable 管理，支持弹簧动画
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 当 locked 外部变化时，重置滑块到正确一侧（动画过渡）
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(locked, trackWidthPx) {
        if (trackWidthPx <= 0f) return@LaunchedEffect
        val maxOffset = trackWidthPx - thumbSizePx - hPadPx * 2
        val targetOffset = if (locked) 0f else maxOffset
        offsetX.animateTo(targetOffset, animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f))
    }

    BoxWithConstraints(
        modifier = modifier
            .background(brush = bgBrush, shape = RoundedCornerShape(30.dp))
    ) {
        val trackWidth = constraints.maxWidth.toFloat()
        trackWidthPx = trackWidth
        val maxOffset = (trackWidth - thumbSizePx - hPadPx * 2).coerceAtLeast(0f)

        // 提示文字淡出进度
        val progress = if (maxOffset > 0f) (offsetX.value / maxOffset).coerceIn(0f, 1f) else 0f
        val hintAlpha = if (locked) (1f - progress * 2f).coerceIn(0f, 1f)
                        else (progress * 2f - 1f).coerceIn(0f, 1f)

        // 拇指滑块（先渲染 → z 轴低）
        Box(
            modifier = Modifier
                .padding(hPadPx.let { with(density) { it.toDp() } })
                .size(thumbSizeDp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(if (enabled) Color.White else Color(0xFFBBBEC6), CircleShape)
                .draggable(
                    enabled = enabled,   // disabled 时不接受手势
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newVal = (offsetX.value + delta).coerceIn(0f, maxOffset)
                            offsetX.snapTo(newVal)
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            val progress2 = offsetX.value / maxOffset.coerceAtLeast(1f)
                            if (locked) {
                                if (progress2 >= 0.55f) {
                                    offsetX.animateTo(maxOffset, spring(dampingRatio = 0.7f, stiffness = 400f))
                                    vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                                    onLockChanged(false)
                                } else {
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                }
                            } else {
                                if (progress2 <= 0.45f) {
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                    vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                                    onLockChanged(true)
                                } else {
                                    offsetX.animateTo(maxOffset, spring(dampingRatio = 0.6f, stiffness = 300f))
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = if (locked) R.drawable.f2_induc_unlock else R.drawable.f2_unlock_icon
                ),
                contentDescription = null,
                colorFilter = if (locked) null else ColorFilter.tint(Color(0xFF8B1BE8), BlendMode.SrcIn),
                modifier = Modifier.size(22.dp)
            )
        }

        // 提示文字（后渲染 → z 轴高于滑块，不被遮挡）
        val thumbEndDp = hPadPx.let { with(density) { it.toDp() } } + thumbSizeDp + 8.dp
        if (!enabled) {
            if (showOfflineHint) {
                Text(
                    text = "设备不在线",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(1f)
                )
            }
        } else if (locked) {
            Text(
                text = "滑动解锁 ›",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = thumbEndDp)
                    .alpha(hintAlpha)
            )
        } else {
            Text(
                text = "‹ 滑动锁车",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(end = thumbEndDp)
                    .alpha(hintAlpha)
            )
        }
    }
}

@Composable
private fun IconLabel(icon: Int, label: String, onClick: () -> Unit, darkBackground: Boolean, containerWidth: Dp, active: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(containerWidth)
            .clickable(onClick = onClick)
    ) {
        if (darkBackground) {
            Row(
                modifier = Modifier
                    .size(width = 40.dp, height = 40.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            if (active) listOf(Color(0xFF4E3D86), Color(0xFF171E36))
                            else listOf(Color(0xFF2F335C), Color(0xFF060A17))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = icon),
                    contentDescription = label,
                    modifier = Modifier.size(15.dp),
                    colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn)
                )
            }
        } else {
            Image(painter = painterResource(id = icon), contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = Color(0xFF6A7A90),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
