package com.geochanger.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.geochanger.model.GeoPoint
import com.geochanger.service.MockLocationService
import com.geochanger.util.MockLocationPermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MapUiState(
    val selectedPoint: GeoPoint? = null,
    val isMocking: Boolean = false,
    val hasMockPermission: Boolean = false,
    val showPermissionDialog: Boolean = false
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun checkMockPermission() {
        val hasPerm = MockLocationPermissionHelper.isMockLocationEnabled(getApplication())
        _uiState.update { it.copy(hasMockPermission = hasPerm) }
    }

    fun onMapTap(point: GeoPoint) {
        _uiState.update { it.copy(selectedPoint = point) }
    }

    fun startMocking() {
        val state = _uiState.value
        if (!state.hasMockPermission) {
            _uiState.update { it.copy(showPermissionDialog = true) }
            return
        }
        val point = state.selectedPoint ?: return

        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LATITUDE, point.latitude)
            putExtra(MockLocationService.EXTRA_LONGITUDE, point.longitude)
            putExtra(MockLocationService.EXTRA_ALTITUDE, point.altitude)
        }
        getApplication<Application>().startForegroundService(intent)
        _uiState.update { it.copy(isMocking = true) }
    }

    fun stopMocking() {
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        _uiState.update { it.copy(isMocking = false) }
    }

    fun dismissPermissionDialog() {
        _uiState.update { it.copy(showPermissionDialog = false) }
    }

    fun openDeveloperOptions() {
        MockLocationPermissionHelper.openDeveloperOptions(getApplication())
        dismissPermissionDialog()
    }
}
