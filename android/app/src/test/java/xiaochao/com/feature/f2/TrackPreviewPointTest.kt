package xiaochao.com.feature.f2

import org.junit.Assert.assertEquals
import org.junit.Test
import xiaochao.com.data.api.TrackPoint
import xiaochao.com.feature.f2.presentation.resolveTrackPreviewPoint

class TrackPreviewPointTest {
    @Test
    fun resolveTrackPreviewPoint_returnsLastTrackPoint() {
        val result = resolveTrackPreviewPoint(
            listOf(
                TrackPoint(latitude = 31.2304, longitude = 121.4737),
                TrackPoint(latitude = 31.2243, longitude = 121.4768),
            )
        )

        assertEquals(31.2243, result?.latitude ?: 0.0, 0.0)
        assertEquals(121.4768, result?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun resolveTrackPreviewPoint_returnsNullWhenTrackEmpty() {
        assertEquals(null, resolveTrackPreviewPoint(emptyList()))
    }

    @Test
    fun resolveTrackPreviewPoint_ignoresZeroCoordinateTail() {
        val result = resolveTrackPreviewPoint(
            listOf(
                TrackPoint(latitude = 31.2304, longitude = 121.4737),
                TrackPoint(latitude = 0.0, longitude = 0.0),
            )
        )

        assertEquals(31.2304, result?.latitude ?: 0.0, 0.0)
        assertEquals(121.4737, result?.longitude ?: 0.0, 0.0)
    }
}
