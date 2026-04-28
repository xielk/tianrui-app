package xiaochao.com.feature.f2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xiaochao.com.R

@Composable
fun F2MapAndMetrics(
    gpsLocked: Boolean,
    currentLocationAddress: String,
    trackLocationAddress: String,
    timeText: String,
    mileageText: String,
    durationText: String,
    topSpeedText: String,
    avgSpeedText: String,
    latitude: Double,
    longitude: Double,
    trackLatitude: Double,
    trackLongitude: Double,
    showLocationCards: Boolean,
    onCurrentLocationClick: () -> Unit,
    onHistoryTrackClick: () -> Unit,
) {
    if (showLocationCards) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth - 12.dp) / 2
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapCard("当前位置", currentLocationAddress, latitude, longitude, gpsLocked, onCurrentLocationClick, modifier = Modifier.width(cardWidth))
                HistoryTrackCard("历史轨迹", trackLocationAddress, trackLatitude, trackLongitude, gpsLocked, onHistoryTrackClick, modifier = Modifier.width(cardWidth))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showLocationCards) 10.dp else 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = timeText,
                fontSize = 14.sp,
                color = Color(0xFF71839E),
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = (maxWidth - 24.dp) / 4
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricItem("里程", mileageText, modifier = Modifier.width(itemWidth))
                    MetricItem("耗时", durationText, modifier = Modifier.width(itemWidth))
                    MetricItem("极速", topSpeedText, modifier = Modifier.width(itemWidth))
                    MetricItem("匀速", avgSpeedText, modifier = Modifier.width(itemWidth))
                }
            }
        }
    }
}

@Composable
private fun MapCard(
    label: String,
    address: String,
    latitude: Double,
    longitude: Double,
    gpsLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .alpha(if (gpsLocked) 0.5f else 1f)
            .clickable(enabled = !gpsLocked, onClick = onClick)
            .height(146.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TianDiTuCurrentLocationWebMap(
                latitude = latitude,
                longitude = longitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(Color(0xFFDDE5F3), RoundedCornerShape(16.dp))
            )
            Text(text = address, color = Color(0xFF6E7F97), fontSize = 10.sp)
        }
    }
}

@Composable
private fun HistoryTrackCard(
    label: String,
    address: String,
    latitude: Double,
    longitude: Double,
    gpsLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasTrackPreview = latitude != 0.0 && longitude != 0.0
    Card(
        modifier = modifier
            .alpha(if (gpsLocked) 0.5f else 1f)
            .clickable(enabled = !gpsLocked, onClick = onClick)
            .height(146.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasTrackPreview) {
                TianDiTuCurrentLocationWebMap(
                    latitude = latitude,
                    longitude = longitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(Color(0xFFDDE5F3), RoundedCornerShape(16.dp))
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.f2_map_history),
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(Color(0xFFDDE5F3), RoundedCornerShape(16.dp))
                )
            }
            Text(text = address, color = Color(0xFF6E7F97), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MetricItem(title: String, value: String, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(14.dp), modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF141D2C))
            Text(text = title, fontSize = 10.sp, color = Color(0xFF6E7F97))
        }
    }
}
