package xiaochao.com.feature.f2.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL

private val imageCache = object : LruCache<String, Bitmap>(20) {}

@Composable
fun F1MapCard(
    latitude: Double,
    longitude: Double,
    address: String,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidLocation = latitude != 0.0 && longitude != 0.0
    val staticMapUrl = if (hasValidLocation) {
        "https://restapi.amap.com/v3/staticmap?key=028f30294d904b08d1a7e1150a3d7c74&location=$longitude,$latitude&zoom=16&size=500*300&markers=mid,0xFF0000,A:$longitude,$latitude&scale=2"
    } else {
        ""
    }

    var bitmap by remember(staticMapUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(staticMapUrl) {
        if (staticMapUrl.isBlank()) return@LaunchedEffect
        val cached = imageCache.get(staticMapUrl)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val conn = URL(staticMapUrl).openConnection() as HttpURLConnection
                conn.doInput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val input = conn.inputStream
                val bmp = BitmapFactory.decodeStream(input)
                input.close()
                if (bmp != null) {
                    imageCache.put(staticMapUrl, bmp)
                    handler.post { bitmap = bmp }
                }
            } catch (_: Exception) {}
        }.start()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onMapClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFDDE5F3)),
                contentAlignment = Alignment.Center
            ) {
                if (hasValidLocation) {
                    bitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "设备位置",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Text(
                        text = "加载地图中...",
                        color = Color(0xFF6E7F97),
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "暂无位置信息",
                        color = Color(0xFF6E7F97),
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Text(
                text = "您的当前位置:",
                color = Color(0xFF8A95A8),
                fontSize = 14.sp
            )
            Text(
                text = address.ifBlank { if (hasValidLocation) "获取地址中" else "位置信息不可用" },
                color = Color(0xFF121A2A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}