package xiaochao.com.feature.splash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashGateTest {
    @Test
    fun gateClosesAfterSplashDuration() {
        val gate = StartupSplashGate(durationMs = SplashAnimationSpec.TotalDurationMs)
        gate.markStarted(0L)

        assertFalse(gate.shouldEnterApp(1300L))
        assertTrue(gate.shouldEnterApp(1400L))
    }
}
