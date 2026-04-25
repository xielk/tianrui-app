package xiaochao.com.feature.splash

object SplashAnimationSpec {
    const val TotalDurationMs = 1400L
    const val BreatheUpMs = 350
    const val BreatheDownMs = 350
    const val BreatheCycles = 2
    const val SettleMs = 200
    const val BaseScale = 1.00f
    const val MaxScale = 1.06f
}

class StartupSplashGate(private val durationMs: Long = SplashAnimationSpec.TotalDurationMs) {
    private var startedAtMs: Long? = null

    fun markStarted(nowMs: Long) {
        if (startedAtMs == null) {
            startedAtMs = nowMs
        }
    }

    fun shouldEnterApp(nowMs: Long): Boolean {
        val start = startedAtMs ?: return false
        return nowMs - start >= durationMs
    }
}
