package com.research.detectmind.service.collectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.research.detectmind.data.local.entity.LocationEntity
import com.research.detectmind.data.repository.SensorDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class LocationCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SensorDataRepository
) : SensorCollector {

    override val sensorType = "location"
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    override fun start(scope: CoroutineScope, participantId: String, intervalSeconds: Int?, configJson: String?) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val intervalMs = (intervalSeconds ?: 60).coerceAtLeast(1) * 1000L
        val movementThreshold = configJson?.let { json ->
            runCatching { JSONObject(json).getDouble("movement_threshold").toFloat() }.getOrNull()
        } ?: 0f

        fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(
            if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs
        )
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(movementThreshold)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    scope.launch {
                        repo.insertLocation(
                            LocationEntity(
                                participantId = participantId,
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                altitude = loc.altitude,
                                accuracy = loc.accuracy,
                                speed = if (loc.hasSpeed()) loc.speed else 0f,
                                provider = loc.provider ?: "fused",
                                recordedAt = Instant.now().toString()
                            )
                        )
                    }
                }
            }
        }
        locationCallback = callback

        runCatching {
            fusedClient?.requestLocationUpdates(request, callback, context.mainLooper)
        }
    }

    override fun stop() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        fusedClient = null
    }
}
