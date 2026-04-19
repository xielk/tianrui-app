package xiaochao.com.feature.f2.ui

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import xiaochao.com.R
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps2d.MapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xiaochao.com.core.map.MapManagerRegistry
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepositoryImpl
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable

@Composable
fun F2CurrentLocationMapScreen(deviceKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapManager = remember { MapManagerRegistry.current() }
    val repo = remember { ApiRepositoryImpl() }
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(deviceKey) {
        loading = true
        errorMessage = null
        when (val result = withContext(Dispatchers.IO) { repo.fetchVehicleStatus(deviceKey) }) {
            is AppResult.Success -> {
                val lat = result.data.latitude
                val lng = result.data.longitude
                if (lat == 0.0 || lng == 0.0) {
                    errorMessage = "无法获取设备位置"
                } else {
                    location = lat to lng
                }
            }
            is AppResult.Error -> errorMessage = result.message.ifBlank { "加载地图失败" }
        }
        loading = false
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            errorMessage = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RowTopBar(title = "当前位置", onBack = onBack)
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
                location?.let { (lat, lng) -> mapManager.renderCurrentLocation(view.map, lat, lng) }
            })
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun F2TrackMapScreen(deviceKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapManager = remember { MapManagerRegistry.current() }
    val repo = remember { ApiRepositoryImpl() }
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }

    var loading by remember { mutableStateOf(true) }
    var points by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(deviceKey) {
        loading = true
        errorMessage = null
        when (val result = withContext(Dispatchers.IO) { repo.fetchDeviceTrackPoints(deviceKey, 20) }) {
            is AppResult.Success -> {
                points = result.data.map { it.latitude to it.longitude }
            }
            is AppResult.Error -> errorMessage = result.message.ifBlank { "获取轨迹失败" }
        }
        loading = false
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            errorMessage = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RowTopBar(title = "历史轨迹", onBack = onBack)
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
                mapManager.renderTrack(view.map, points)
            })
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            if (!loading && points.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("暂无轨迹数据")
                    Button(onClick = onBack) { Text("返回") }
                }
            }
        }
    }
}

@Composable
private fun RowTopBar(title: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Icons.Filled.ArrowBack,
            contentDescription = "返回",
            modifier = Modifier.clickable { onBack() }.size(20.dp)
        )
        Text(text = title)
        Text(text = "  ")
    }
}
