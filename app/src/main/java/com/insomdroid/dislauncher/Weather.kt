package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.Geocoder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.coroutines.resume

internal data class WeatherInfo(
    val temperature: String,
    val condition: String,
    val humidity: Int?,
    val wind: String?,
    val feelsLike: String? = null,
    val high: String? = null,
    val low: String? = null,
    val locationName: String? = null,
    val sunrise: LocalTime? = null,
    val sunset: LocalTime? = null,
    val timezone: String? = null
) {
    fun summary(): String {
        val extras = listOfNotNull(humidity?.let { "$it% humidity" }, wind)
        val conditions = listOf("$temperature · $condition", extras.takeIf { it.isNotEmpty() }?.joinToString(" · "))
            .filterNotNull().joinToString("   ")
        return listOfNotNull(locationName, conditions).joinToString(" · ")
    }
}

internal suspend fun fetchWeather(context: Context, unit: String): WeatherInfo? {
    val hasCoarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasFine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasCoarse && !hasFine) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val lastLocation = runCatching {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }
    }.getOrNull()?.takeIf { System.currentTimeMillis() - it.time < 30 * 60 * 1000L }
    val location = lastLocation ?: requestLocation(manager, hasFine) ?: return null

    return runCatching {
        val temperatureUnit = if (unit == "F") "fahrenheit" else "celsius"
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=1" +
                "&temperature_unit=$temperatureUnit&wind_speed_unit=kmh&timezone=auto"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val response = JSONObject(payload)
            val current = response.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val feels = current.optDouble("apparent_temperature").takeIf { !it.isNaN() }
            val humidityValue = current.optInt("relative_humidity_2m").takeIf { it in 0..100 }
            val windValue = current.optDouble("wind_speed_10m").takeIf { !it.isNaN() }?.toInt()
            val daily = response.optJSONObject("daily")
            val highValue = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() }
            val lowValue = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() }
            val sunriseValue = daily?.optJSONArray("sunrise")?.optString(0)?.takeIf(String::isNotBlank)
            val sunsetValue = daily?.optJSONArray("sunset")?.optString(0)?.takeIf(String::isNotBlank)
            val sunriseTime = runCatching { LocalDateTime.parse(sunriseValue).toLocalTime() }.getOrNull()
            val sunsetTime = runCatching { LocalDateTime.parse(sunsetValue).toLocalTime() }.getOrNull()
            val locality = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
            }.getOrNull()
            WeatherInfo(
                temperature = "${temp.toInt()}°$unit",
                condition = weatherCondition(current.getInt("weather_code")),
                humidity = humidityValue,
                wind = windValue?.let { "${it} km/h" },
                feelsLike = feels?.let { "${it.toInt()}°$unit" },
                high = highValue?.let { "${it.toInt()}°$unit" },
                low = lowValue?.let { "${it.toInt()}°$unit" },
                locationName = locality,
                sunrise = sunriseTime,
                sunset = sunsetTime,
                timezone = response.optString("timezone").takeIf(String::isNotBlank)
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private suspend fun requestLocation(manager: LocationManager, hasFine: Boolean): Location? {
    val providers = buildList {
        if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
        if (hasFine && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.GPS_PROVIDER)
        }
    }
    val provider = providers.firstOrNull() ?: return null
    return withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                    runCatching { manager.removeUpdates(this) }
                }
                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        }
    }
}

private fun weatherCondition(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Mostly clear"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm, hail"
    else -> "Weather"
}
