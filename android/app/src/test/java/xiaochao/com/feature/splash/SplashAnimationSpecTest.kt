package xiaochao.com.feature.splash

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashAnimationSpecTest {
    @Test
    fun breathingTimelineMatchesApprovedSpec() {
        assertEquals(1400L, SplashAnimationSpec.TotalDurationMs)
        assertEquals(350, SplashAnimationSpec.BreatheUpMs)
        assertEquals(350, SplashAnimationSpec.BreatheDownMs)
        assertEquals(2, SplashAnimationSpec.BreatheCycles)
        assertEquals(200, SplashAnimationSpec.SettleMs)
    }

    @Test
    fun breathingEnvelopeMatchesApprovedRange() {
        assertEquals(1.00f, SplashAnimationSpec.BaseScale, 0.001f)
        assertEquals(1.06f, SplashAnimationSpec.MaxScale, 0.001f)
    }
}
