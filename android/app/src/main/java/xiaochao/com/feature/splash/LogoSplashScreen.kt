package xiaochao.com.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import xiaochao.com.R

@Composable
fun LogoSplashScreen(onFinished: () -> Unit) {
    val logoSize = dimensionResource(id = R.dimen.splash_logo_size)
    val scale = remember { Animatable(SplashAnimationSpec.BaseScale) }

    LaunchedEffect(Unit) {
        repeat(SplashAnimationSpec.BreatheCycles) {
            scale.animateTo(
                targetValue = SplashAnimationSpec.MaxScale,
                animationSpec = tween(durationMillis = SplashAnimationSpec.BreatheUpMs, easing = LinearEasing),
            )
            scale.animateTo(
                targetValue = SplashAnimationSpec.BaseScale,
                animationSpec = tween(durationMillis = SplashAnimationSpec.BreatheDownMs, easing = LinearEasing),
            )
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}
