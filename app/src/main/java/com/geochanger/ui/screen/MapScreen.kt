package com.geochanger.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geochanger.model.GeoPoint
import com.geochanger.viewmodel.MapUiState
import com.geochanger.viewmodel.MapViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkMockPermission()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            selectedPoint = uiState.selectedPoint,
            onMapTap = { geoPoint -> viewModel.onMapTap(geoPoint) }
        )

        if (!uiState.hasMockPermission) {
            PermissionBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }

        BottomControlCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            uiState = uiState,
            onStart = { viewModel.startMocking() },
            onStop  = { viewModel.stopMocking() }
        )
    }

    if (uiState.showPermissionDialog) {
        MockPermissionDialog(
            onConfirm = { viewModel.openDeveloperOptions() },
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }
}

@Composable
private fun OsmMapView(
    modifier: Modifier,
    selectedPoint: GeoPoint?,
    onMapTap: (GeoPoint) -> Unit
) {
    val context = LocalContext.current
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().load(
                ctx,
                ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = "GeoChanger/1.0"

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                controller.setCenter(OsmGeoPoint(55.7558, 37.6173)) // Moscow default

                val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: OsmGeoPoint): Boolean {
                        onMapTap(GeoPoint(p.latitude, p.longitude))
                        return true
                    }
                    override fun longPressHelper(p: OsmGeoPoint): Boolean = false
                })
                overlays.add(0, eventsOverlay)
            }
        },
        update = { mapView ->
            selectedPoint?.let { target ->
                val osmPoint = OsmGeoPoint(target.latitude, target.longitude)
                if (markerRef == null) {
                    val marker = Marker(mapView).apply {
                        position = osmPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Mock Location"
                    }
                    mapView.overlays.add(marker)
                    markerRef = marker
                } else {
                    markerRef!!.position = osmPoint
                }
                mapView.controller.animateTo(osmPoint)
                mapView.invalidate()
            }
        }
    )
}

@Composable
private fun BottomControlCard(
    modifier: Modifier,
    uiState: MapUiState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.selectedPoint != null) {
                val pt = uiState.selectedPoint
                Text(
                    text = "%.6f,  %.6f".format(pt.latitude, pt.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Нажмите на карту, чтобы выбрать точку",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isMocking) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Остановить")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedPoint != null
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить")
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Не назначено приложением для тестирования позиции — нажмите «Запустить» для инструкций",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun MockPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        title = { Text("Требуется разрешение") },
        text = {
            Text(
                "Чтобы подменять GPS для других приложений, нужно назначить GeoChanger " +
                "в Параметрах разработчика.\n\n" +
                "Шаги:\n" +
                "1. Нажмите «Открыть параметры разработчика»\n" +
                "2. Найдите «Выбрать приложение для тестирования позиции»\n" +
                "3. Выберите «GeoChanger»\n" +
                "4. Вернитесь и нажмите «Запустить»"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Открыть параметры разработчика")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
