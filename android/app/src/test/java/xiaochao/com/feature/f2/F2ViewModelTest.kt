package xiaochao.com.feature.f2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import xiaochao.com.data.model.DeviceType
import xiaochao.com.feature.f2.presentation.F2Intent
import xiaochao.com.feature.f2.presentation.F2ViewModel
import xiaochao.com.feature.f2.presentation.shouldApplyRealtimeMute
import xiaochao.com.feature.f2.presentation.shouldSyncAutoSenseToApi
import xiaochao.com.feature.f2.presentation.resolveMuteTarget
import xiaochao.com.feature.f2.presentation.resolveAutoSenseTarget

class F2ViewModelTest {
    @Test
    fun resolveMuteTarget_invertsCurrentState() {
        assertTrue(resolveMuteTarget(false))
        assertFalse(resolveMuteTarget(true))
    }

    @Test
    fun resolveAutoSenseTarget_invertsCurrentState() {
        assertTrue(resolveAutoSenseTarget(false))
        assertFalse(resolveAutoSenseTarget(true))
    }

    @Test
    fun shouldApplyRealtimeMute_blocks_opposite_during_protection_window() {
        val now = 1000L
        assertFalse(
            shouldApplyRealtimeMute(
                pendingTarget = true,
                protectUntilMs = 3000L,
                nowMs = now,
                incoming = false,
            )
        )
        assertTrue(
            shouldApplyRealtimeMute(
                pendingTarget = true,
                protectUntilMs = 3000L,
                nowMs = now,
                incoming = true,
            )
        )
    }

    @Test
    fun shouldSyncAutoSenseToApi_f1_false_f2_true() {
        assertFalse(shouldSyncAutoSenseToApi(DeviceType.F1))
        assertTrue(shouldSyncAutoSenseToApi(DeviceType.F2))
    }

    @Test
    fun onLockSliderChange_updatesLockState() {
        val vm = F2ViewModel()
        vm.dispatch(F2Intent.OnLockSliderChange(locked = true))
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun onMuteClick_withoutOwnerOrDevice_showsPermissionTip() {
        val vm = F2ViewModel()
        vm.dispatch(F2Intent.OnMuteClick)
        assertEquals("设备不存在", vm.uiState.value.tipMessage)
    }

    @Test
    fun onAutoSenseClick_withoutOwnerOrDevice_showsPermissionTip() {
        val vm = F2ViewModel()
        vm.dispatch(F2Intent.OnAutoSenseClick)
        assertEquals("设备不存在", vm.uiState.value.tipMessage)
    }

    @Test
    fun onLockSliderChange_withoutChannel_showsOfflineTip() {
        val vm = F2ViewModel()

        vm.dispatch(F2Intent.OnLockSliderChange(locked = false))
        assertEquals("设备不在线", vm.uiState.value.tipMessage)
    }
}
