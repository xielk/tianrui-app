package xiaochao.com.feature.f2.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun F1MapCard(
    latitude: Double,
    longitude: Double,
    address: String,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidLocation = latitude != 0.0 && longitude != 0.0

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
                    TianDiTuCurrentLocationWebMap(
                        latitude = latitude,
                        longitude = longitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
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
