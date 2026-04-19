package xiaochao.com.core.map

import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.PolylineOptions

enum class MapProvider {
    AMAP,
    TIANDITU,
}

interface MapManager {
    fun renderCurrentLocation(map: AMap, latitude: Double, longitude: Double)
    fun renderTrack(map: AMap, points: List<Pair<Double, Double>>)
}

class AMapMapManager : MapManager {
    override fun renderCurrentLocation(map: AMap, latitude: Double, longitude: Double) {
        val target = LatLng(latitude, longitude)
        map.clear()
        map.addMarker(
            MarkerOptions()
                .position(target)
                .title("当前位置")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f))
    }

    override fun renderTrack(map: AMap, points: List<Pair<Double, Double>>) {
        map.clear()
        if (points.isEmpty()) return

        val latLngPoints = points.map { LatLng(it.first, it.second) }
        map.addPolyline(
            PolylineOptions()
                .addAll(latLngPoints)
                .width(12f)
                .color(0xFF00B578.toInt())
        )

        val start = latLngPoints.first()
        val end = latLngPoints.last()
        map.addMarker(MarkerOptions().position(start).title("起点"))
        map.addMarker(MarkerOptions().position(end).title("终点"))

        if (latLngPoints.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 16f))
            return
        }

        val boundsBuilder = LatLngBounds.Builder()
        latLngPoints.forEach { boundsBuilder.include(it) }
        // 2D SDK 的 newLatLngBounds 参数可能不同，如果报错请调整
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
        } catch (e: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(end, 12f))
        }
    }
}

object MapManagerRegistry {
    var provider: MapProvider = MapProvider.AMAP

    fun current(): MapManager {
        return when (provider) {
            MapProvider.AMAP -> AMapMapManager()
            MapProvider.TIANDITU -> AMapMapManager()
        }
    }
}
