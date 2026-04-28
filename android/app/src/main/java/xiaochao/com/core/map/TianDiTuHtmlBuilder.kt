package xiaochao.com.core.map

object TianDiTuHtmlBuilder {
    private const val apiKey = "aa26c88f9ffd85c001301709c4ca2557"

    fun currentLocationHtml(latitude: Double, longitude: Double): String {
        val hasPoint = latitude != 0.0 && longitude != 0.0
        val pointScript = if (hasPoint) {
            """
            const point = new T.LngLat($longitude, $latitude);
            map.centerAndZoom(point, 16);
            const marker = new T.Marker(point);
            map.addOverLay(marker);
            """.trimIndent()
        } else {
            "map.centerAndZoom(new T.LngLat(116.397428, 39.90923), 5);"
        }
        return baseHtml(pointScript)
    }

    fun trackHtml(points: List<Pair<Double, Double>>): String {
        val pointsJson = points.joinToString(prefix = "[", postfix = "]") { (lat, lng) ->
            "{\"latitude\":$lat,\"longitude\":$lng}"
        }
        val script = """
            const rawPoints = $pointsJson;
            const lngLats = rawPoints.map(p => new T.LngLat(p.longitude, p.latitude));

            if (lngLats.length === 0) {
              map.centerAndZoom(new T.LngLat(116.397428, 39.90923), 5);
            } else {
              map.centerAndZoom(lngLats[0], 14);
              const polyline = new T.Polyline(lngLats, {
                color: '#1C73F8',
                weight: 6,
                opacity: 0.92,
                lineStyle: 'solid'
              });
              map.addOverLay(polyline);

              const startMarker = new T.Marker(lngLats[0]);
              map.addOverLay(startMarker);
              if (lngLats.length > 1) {
                const endMarker = new T.Marker(lngLats[lngLats.length - 1]);
                map.addOverLay(endMarker);
              }
              map.setViewport(lngLats);
            }
        """.trimIndent()
        return baseHtml(script)
    }

    private fun baseHtml(scriptBody: String): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
              <style>
                html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; }
                body { background: #ffffff; }
              </style>
              <script src="https://api.tianditu.gov.cn/api?v=4.0&tk=$apiKey"></script>
            </head>
            <body>
              <div id="map"></div>
              <script>
                var map;
                function initMap() {
                  var mapDiv = document.getElementById('map');
                  if (mapDiv.clientWidth === 0 || mapDiv.clientHeight === 0) {
                    setTimeout(initMap, 100);
                    return;
                  }
                  try {
                    if (typeof T === 'undefined') {
                      document.title = 'tdt-sdk-missing';
                      return;
                    }
                    map = new T.Map('map');
                    $scriptBody
                    document.title = 'tdt-ok';
                    console.log('map-init-ok');
                  } catch (e) {
                    document.title = 'tdt-error:' + (e && e.message ? e.message : String(e));
                    console.log('map-init-error', e && e.message ? e.message : String(e));
                  }
                }
                window.onload = initMap;
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
