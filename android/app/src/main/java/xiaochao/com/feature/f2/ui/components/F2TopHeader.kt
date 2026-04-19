package xiaochao.com.feature.f2.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xiaochao.com.R

@Composable
fun F2TopHeader(
    frameNumber: String,
    historyMileKm: Int,
    sosPhone: String,
    showSos: Boolean = true,
    onVehicleClick: () -> Unit,
    onSosClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onVehicleClick)) {
            Text(
                text = frameNumber,
                color = Color(0xFF0C1222),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Image(
                painter = painterResource(id = R.drawable.f2_arrow_down),
                contentDescription = "down",
                modifier = Modifier.size(10.dp)
            )
        }
        Text(
            text = "历史总里程：${historyMileKm}KM",
            color = Color(0xFF2D3A53),
            fontSize = 16.sp,
        )
        if (showSos) {
            Text(
                text = "SOS联系人：${maskPhone(sosPhone)}",
                color = Color(0xFF2D3A53),
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onSosClick),
            )
        }
    }
}

private fun maskPhone(phone: String): String {
    val clean = phone.trim()
    if (clean.length != 11) return clean.ifEmpty { "--" }
    return clean.replaceRange(3, 7, "****")
}
