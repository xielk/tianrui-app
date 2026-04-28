package xiaochao.com.core.map

import org.junit.Assert.assertTrue
import org.junit.Test

class TianDiTuHtmlBuilderTest {

    @Test
    fun currentMapHtml_includesTiandituSdkAndCoordinates() {
        val html = TianDiTuHtmlBuilder.currentLocationHtml(31.2304, 121.4737)

        assertTrue(html.contains("https://api.tianditu.gov.cn/api?v=4.0&tk="))
        assertTrue(html.contains("new T.LngLat(121.4737, 31.2304)"))
        assertTrue(html.contains("map.centerAndZoom(point, 16)"))
    }

    @Test
    fun trackMapHtml_includesPolylineAndViewport() {
        val points = listOf(31.2304 to 121.4737, 31.2243 to 121.4768)
        val html = TianDiTuHtmlBuilder.trackHtml(points)

        assertTrue(html.contains("const rawPoints ="))
        assertTrue(html.contains("new T.Polyline"))
        assertTrue(html.contains("map.setViewport(lngLats)"))
    }
}
