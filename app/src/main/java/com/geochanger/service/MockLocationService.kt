package com.geochanger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.geochanger.MainActivity
import com.geochanger.R
import com.geochanger.model.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "com.geochanger.action.START_MOCK"
        const val ACTION_STOP  = "com.geochanger.action.STOP_MOCK"

        const val EXTRA_LATITUDE  = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_ALTITUDE  = "extra_altitude"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "geo_changer_channel"

        private val MOCK_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
    }

    private lateinit var locationManager: LocationManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null

    @Volatile private var currentTarget: GeoPoint? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val alt = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0)
                currentTarget = GeoPoint(lat, lon, alt)
                startForeground(NOTIFICATION_ID, buildNotification(lat, lon))
                startMocking()
            }
            ACTION_STOP -> {
                stopMocking()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMocking()
        super.onDestroy()
    }

    private fun startMocking() {
        if (mockJob?.isActive == true) return

        MOCK_PROVIDERS.forEach { provider -> addTestProvider(provider) }

        mockJob = serviceScope.launch {
            while (isActive) {
                currentTarget?.let { target ->
                    MOCK_PROVIDERS.forEach { provider ->
                        pushLocation(provider, target)
                    }
                }
                delay(100L)
            }
        }
    }

    private fun stopMocking() {
        mockJob?.cancel()
        mockJob = null

        MOCK_PROVIDERS.forEach { provider ->
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: IllegalArgumentException) { }
        }
    }

    private fun addTestProvider(provider: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false,
                    true, true, true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false,
                    true, true, true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
            }
            locationManager.setTestProviderEnabled(provider, true)
        } catch (e: SecurityException) {
            stopSelf()
        } catch (_: IllegalArgumentException) { }
    }

    private fun pushLocation(provider: String, target: GeoPoint) {
        val location = Location(provider).apply {
            latitude = target.latitude
            longitude = target.longitude
            altitude = target.altitude
            accuracy = 3.0f
            speed = 0.0f
            bearing = 0.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        try {
            locationManager.setTestProviderLocation(provider, location)
        } catch (_: IllegalArgumentException) { }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while GeoChanger is spoofing your location"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(lat: Double, lon: Double): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("%.5f, %.5f".format(lat, lon))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .addAction(0, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
