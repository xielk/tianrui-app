# Zongshen Logo Splash Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a logo-only animated splash sequence (slide-in, micro rebound, electric glow) before `AppNavGraph()` renders in the Android app.

**Architecture:** Keep Android system splash as a lightweight static window background, then run a Compose splash overlay for `1.8s` inside `MainActivity`. Use a small pure Kotlin spec module for timing and glow parameters so we can unit test animation rules without UI instrumentation. After animation completion, switch to the existing app navigation tree.

**Tech Stack:** Kotlin, Jetpack Compose animation APIs (`Animatable`, `keyframes`, `tween`), Android resources (`drawable`, `themes.xml`), JUnit4 unit tests, Gradle.

---

## File Structure

- Create: `android/app/src/main/java/xiaochao/com/feature/splash/SplashAnimationSpec.kt`  
  Responsibility: central source of durations, easing, and pure timing helpers for glow and phase boundaries.
- Create: `android/app/src/main/java/xiaochao/com/feature/splash/LogoSplashScreen.kt`  
  Responsibility: Compose UI and runtime animation playback using `app_logo` only.
- Modify: `android/app/src/main/java/xiaochao/com/MainActivity.kt`  
  Responsibility: gate initial content; show splash first, then existing `AppNavGraph()`.
- Modify: `android/app/src/main/res/drawable/launch_screen.xml`  
  Responsibility: align static startup background with splash palette to avoid visual flash.
- Create: `android/app/src/test/java/xiaochao/com/feature/splash/SplashAnimationSpecTest.kt`  
  Responsibility: unit tests for timing rules and glow parameter boundaries.

### Task 1: Build and test splash timing spec

**Files:**
- Create: `android/app/src/main/java/xiaochao/com/feature/splash/SplashAnimationSpec.kt`
- Create: `android/app/src/test/java/xiaochao/com/feature/splash/SplashAnimationSpecTest.kt`

- [ ] **Step 1: Write the failing unit test for phase boundaries and glow values**

```kotlin
package xiaochao.com.feature.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashAnimationSpecTest {
    @Test
    fun `phase windows match approved timeline`() {
        assertEquals(1800L, SplashAnimationSpec.TotalDurationMs)
        assertEquals(700L, SplashAnimationSpec.SlideInEndMs)
        assertEquals(950L, SplashAnimationSpec.ReboundEndMs)
        assertEquals(1550L, SplashAnimationSpec.GlowEndMs)
    }

    @Test
    fun `glow curve stays inside configured envelope`() {
        val start = SplashAnimationSpec.glowAt(950L)
        val peak = SplashAnimationSpec.glowAt(1250L)
        val end = SplashAnimationSpec.glowAt(1550L)

        assertEquals(0.70f, start.strength, 0.01f)
        assertEquals(1.00f, peak.strength, 0.01f)
        assertEquals(0.68f, end.strength, 0.01f)
        assertTrue(peak.radiusDp > start.radiusDp)
        assertTrue(end.radiusDp < peak.radiusDp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "xiaochao.com.feature.splash.SplashAnimationSpecTest"`

Expected: FAIL with unresolved references for `SplashAnimationSpec`.

- [ ] **Step 3: Write minimal implementation for spec constants and helper**

```kotlin
package xiaochao.com.feature.splash

import kotlin.math.abs

object SplashAnimationSpec {
    const val TotalDurationMs = 1800L
    const val SlideInEndMs = 700L
    const val ReboundEndMs = 950L
    const val GlowEndMs = 1550L

    data class GlowState(
        val strength: Float,
        val radiusDp: Float,
    )

    fun glowAt(timeMs: Long): GlowState {
        val clamped = timeMs.coerceIn(ReboundEndMs, GlowEndMs)
        val span = (GlowEndMs - ReboundEndMs).toFloat()
        val t = (clamped - ReboundEndMs) / span

        val strength = when {
            t <= 0.5f -> lerp(0.70f, 1.00f, t / 0.5f)
            else -> lerp(1.00f, 0.68f, (t - 0.5f) / 0.5f)
        }
        val radius = when {
            t <= 0.5f -> lerp(16f, 24f, t / 0.5f)
            else -> lerp(24f, 20f, (t - 0.5f) / 0.5f)
        }

        return GlowState(strength = strength, radiusDp = radius)
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return start + (end - start) * x
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "xiaochao.com.feature.splash.SplashAnimationSpecTest"`

Expected: PASS and report includes `SplashAnimationSpecTest` with 2 successful tests.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/xiaochao/com/feature/splash/SplashAnimationSpec.kt android/app/src/test/java/xiaochao/com/feature/splash/SplashAnimationSpecTest.kt
git commit -m "test: define splash timing spec and glow envelope"
```

### Task 2: Implement Compose logo splash animation UI

**Files:**
- Create: `android/app/src/main/java/xiaochao/com/feature/splash/LogoSplashScreen.kt`

- [ ] **Step 1: Write a failing compile check target by referencing a not-yet-created composable from MainActivity (temporary)**

```kotlin
// in MainActivity setContent block (temporary change before file exists)
LogoSplashScreen(onFinished = {})
```

- [ ] **Step 2: Run compile to verify it fails before implementation**

Run: `./gradlew :app:compileDebugKotlin`

Expected: FAIL with unresolved reference `LogoSplashScreen`.

- [ ] **Step 3: Write the minimal splash composable implementation**

```kotlin
package xiaochao.com.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import xiaochao.com.R

@Composable
fun LogoSplashScreen(onFinished: () -> Unit) {
    val offsetX = remember { Animatable(-380f) }
    val scale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0f) }
    val glowRadius = remember { Animatable(0f) }
    var showSolidLogo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = SplashAnimationSpec.SlideInEndMs.toInt(),
                easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
            )
        )

        offsetX.animateTo(
            targetValue = -8f,
            animationSpec = keyframes {
                durationMillis = (SplashAnimationSpec.ReboundEndMs - SplashAnimationSpec.SlideInEndMs).toInt()
                -8f at 150
                -2f at 250
            }
        )

        scale.animateTo(1.02f, tween(120, easing = LinearEasing))
        scale.animateTo(1f, tween(130, easing = LinearEasing))

        showSolidLogo = true
        glowAlpha.animateTo(1f, tween(300))
        glowRadius.animateTo(24f, tween(300))
        glowAlpha.animateTo(0.68f, tween(300))
        glowRadius.animateTo(20f, tween(300))

        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF071426), Color(0xFF0B2A46))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showSolidLogo) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = offsetX.value.dp)
                    .blur(glowRadius.value.dp),
                alpha = glowAlpha.value,
            )
        }

        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.offset(x = offsetX.value.dp),
        )
    }
}
```

- [ ] **Step 4: Run compile to verify splash composable builds**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS with Kotlin compile success for app module.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/xiaochao/com/feature/splash/LogoSplashScreen.kt
git commit -m "feat: add logo-only compose splash animation"
```

### Task 3: Wire splash to startup flow in MainActivity

**Files:**
- Modify: `android/app/src/main/java/xiaochao/com/MainActivity.kt`

- [ ] **Step 1: Write failing behavior test as deterministic unit for startup gate state machine**

```kotlin
package xiaochao.com.feature.splash

import org.junit.Assert.assertTrue
import org.junit.Test

class SplashGateTest {
    @Test
    fun `gate closes after splash duration`() {
        val gate = StartupSplashGate(durationMs = SplashAnimationSpec.TotalDurationMs)
        gate.markStarted(0L)
        assertTrue(!gate.shouldEnterApp(1500L))
        assertTrue(gate.shouldEnterApp(1800L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails before gate implementation**

Run: `./gradlew :app:testDebugUnitTest --tests "xiaochao.com.feature.splash.SplashGateTest"`

Expected: FAIL with unresolved reference `StartupSplashGate`.

- [ ] **Step 3: Add gate helper + integrate MainActivity content switch**

```kotlin
// new helper in SplashAnimationSpec.kt
class StartupSplashGate(private val durationMs: Long) {
    private var startAtMs: Long? = null

    fun markStarted(nowMs: Long) {
        if (startAtMs == null) startAtMs = nowMs
    }

    fun shouldEnterApp(nowMs: Long): Boolean {
        val start = startAtMs ?: return false
        return nowMs - start >= durationMs
    }
}
```

```kotlin
// MainActivity setContent block
setContent {
    XiaochaoTheme {
        var showSplash by rememberSaveable { mutableStateOf(true) }
        if (showSplash) {
            LogoSplashScreen(onFinished = { showSplash = false })
        } else {
            AppNavGraph()
        }
    }
}
```

- [ ] **Step 4: Run tests and compile checks**

Run: `./gradlew :app:testDebugUnitTest --tests "xiaochao.com.feature.splash.SplashGateTest" :app:compileDebugKotlin`

Expected: PASS for `SplashGateTest` and debug Kotlin compile.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/xiaochao/com/MainActivity.kt android/app/src/main/java/xiaochao/com/feature/splash/SplashAnimationSpec.kt android/app/src/test/java/xiaochao/com/feature/splash/SplashGateTest.kt
git commit -m "feat: gate app navigation behind animated logo splash"
```

### Task 4: Align static launch background and run verification

**Files:**
- Modify: `android/app/src/main/res/drawable/launch_screen.xml`

- [ ] **Step 1: Write minimal resource verification test (build-level) by introducing expected color values in XML change**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="#071426" />
    <item>
        <bitmap
            android:gravity="center"
            android:src="@drawable/app_logo" />
    </item>
</layer-list>
```

- [ ] **Step 2: Run resource merge/build check**

Run: `./gradlew :app:assembleDebug`

Expected: PASS and APK generated under `android/app/build/outputs/apk/debug/`.

- [ ] **Step 3: Run complete verification suite for touched areas**

Run: `./gradlew :app:testDebugUnitTest --tests "xiaochao.com.feature.splash.*" :app:assembleDebug`

Expected: splash unit tests PASS and debug build succeeds.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/drawable/launch_screen.xml
git commit -m "style: align system launch background with animated splash"
```

- [ ] **Step 5: Final integration commit if batching is preferred instead of per-task commits**

```bash
git add android/app/src/main/java/xiaochao/com/feature/splash android/app/src/main/java/xiaochao/com/MainActivity.kt android/app/src/main/res/drawable/launch_screen.xml android/app/src/test/java/xiaochao/com/feature/splash
git commit -m "feat: implement logo-only animated startup splash"
```

## Notes for implementer

- Do not block existing TPNS or update initialization; keep those calls exactly as they are in `MainActivity.onCreate`.
- Keep animation deterministic and avoid random seeds.
- If `app_logo` includes extra whitespace, trim source image before tuning offset values.
- Prefer `rememberSaveable` for the splash visibility flag so configuration changes do not replay unexpectedly after entering app content.
