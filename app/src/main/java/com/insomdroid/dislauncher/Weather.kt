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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.coroutines.resume

internal data class WeatherInfo(
    val temperature: String,
    val condition: String,
    val feelsLike: String? = null,
    val high: String? = null,
    val low: String? = null,
    val locationName: String? = null
)


private const val WEATHER_CACHE_KEY = "cached_weather_info"
private const val WEATHER_CACHE_TIMESTAMP_KEY = "cached_weather_timestamp"
private const val WEATHER_CACHE_MAX_AGE_MS = 30 * 60 * 1000L
private const val WEATHER_FAILURE_TIMESTAMP_KEY = "weather_last_failure_timestamp"
private const val WEATHER_FAILURE_COUNT_KEY = "weather_consecutive_failures"
private const val MAX_WEATHER_RESPONSE_BYTES = 64 * 1024
private val WEATHER_RETRY_DELAYS_MS = longArrayOf(
    60_000L, 120_000L, 240_000L, 480_000L, 960_000L, 1_800_000L
)

internal fun shouldBackoffWeatherFetch(context: Context): Boolean {
    val prefs = context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
    val failedAt = prefs.getLong(WEATHER_FAILURE_TIMESTAMP_KEY, 0L)
    if (failedAt <= 0L) return false
    val age = System.currentTimeMillis() - failedAt
    if (age < 0L) return false
    val failureCount = prefs.getInt(WEATHER_FAILURE_COUNT_KEY, 1)
        .coerceIn(1, WEATHER_RETRY_DELAYS_MS.size)
    return age < WEATHER_RETRY_DELAYS_MS[failureCount - 1]
}

internal fun recordWeatherFetchResult(context: Context, succeeded: Boolean) {
    val prefs = context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    if (succeeded) {
        editor.remove(WEATHER_FAILURE_TIMESTAMP_KEY)
            .remove(WEATHER_FAILURE_COUNT_KEY)
    } else {
        val failureCount = (prefs.getInt(WEATHER_FAILURE_COUNT_KEY, 0) + 1)
            .coerceIn(1, WEATHER_RETRY_DELAYS_MS.size)
        editor.putLong(WEATHER_FAILURE_TIMESTAMP_KEY, System.currentTimeMillis())
            .putInt(WEATHER_FAILURE_COUNT_KEY, failureCount)
    }
    editor.apply()
}

internal fun isWeatherCacheFresh(context: Context): Boolean {
    val savedAt = context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
        .getLong(WEATHER_CACHE_TIMESTAMP_KEY, 0L)
    val age = System.currentTimeMillis() - savedAt
    return savedAt > 0L && age in 0..WEATHER_CACHE_MAX_AGE_MS
}

internal fun saveWeatherCache(context: Context, weather: WeatherInfo) {
    val json = JSONObject()
        .put("temperature", weather.temperature)
        .put("condition", weather.condition)
        .put("feelsLike", weather.feelsLike ?: JSONObject.NULL)
        .put("high", weather.high ?: JSONObject.NULL)
        .put("low", weather.low ?: JSONObject.NULL)
        .put("locationName", weather.locationName ?: JSONObject.NULL)
    context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
        .edit()
        .putString(WEATHER_CACHE_KEY, json.toString())
        .putLong(WEATHER_CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
        .apply()
}

internal fun loadWeatherCache(context: Context, unit: String): WeatherInfo? = runCatching {
    val raw = context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
        .getString(WEATHER_CACHE_KEY, null) ?: return null
    val json = JSONObject(raw)
    fun optionalString(key: String) = json.optString(key).takeIf { it.isNotBlank() && it != "null" }
    WeatherInfo(
        temperature = json.getString("temperature"),
        condition = json.optString("condition", "Weather"),
        feelsLike = optionalString("feelsLike"),
        high = optionalString("high"),
        low = optionalString("low"),
        locationName = optionalString("locationName")
    ).let { convertWeatherUnit(it, unit) }
}.getOrNull()


internal fun convertWeatherUnit(weather: WeatherInfo, unit: String): WeatherInfo {
    fun convert(value: String?): String? {
        if (value == null) return null
        val match = Regex("^(-?\\d+)°([CF])$").matchEntire(value) ?: return value
        val number = match.groupValues[1].toIntOrNull() ?: return value
        val sourceUnit = match.groupValues[2]
        if (sourceUnit == unit) return value
        val converted = if (sourceUnit == "C" && unit == "F") {
            (number * 9.0 / 5.0 + 32.0).roundToInt()
        } else if (sourceUnit == "F" && unit == "C") {
            ((number - 32.0) * 5.0 / 9.0).roundToInt()
        } else {
            number
        }
        return "${converted}°$unit"
    }
    return weather.copy(
        temperature = convert(weather.temperature) ?: weather.temperature,
        feelsLike = convert(weather.feelsLike),
        high = convert(weather.high),
        low = convert(weather.low)
    )
}

internal suspend fun fetchWeather(context: Context, unit: String): WeatherInfo? {
    val hasCoarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasCoarse) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val lastLocation = runCatching {
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } else {
            null
        }
    }.getOrNull()?.takeIf {
        val age = System.currentTimeMillis() - it.time
        age in 0..30 * 60 * 1000L
    }
    val location = lastLocation ?: requestApproximateLocation(manager) ?: return null

    return runCatching {
        val temperatureUnit = if (unit == "F") "fahrenheit" else "celsius"
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1" +
                "&temperature_unit=$temperatureUnit&timezone=auto"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= MAX_WEATHER_RESPONSE_BYTES) {
                        "Weather response exceeds the allowed size"
                    }
                    output.write(buffer, 0, count)
                }
            }
            val response = JSONObject(output.toString(Charsets.UTF_8.name()))
            val current = response.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val feels = current.optDouble("apparent_temperature").takeIf { !it.isNaN() }
            val daily = response.optJSONObject("daily")
            val highValue = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() }
            val lowValue = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() }
            val locality = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
            }.getOrNull()
            WeatherInfo(
                temperature = "${temp.roundToInt()}°$unit",
                condition = weatherCondition(current.getInt("weather_code")),
                feelsLike = feels?.roundToInt()?.let { "${it}°$unit" },
                high = highValue?.roundToInt()?.let { "${it}°$unit" },
                low = lowValue?.roundToInt()?.let { "${it}°$unit" },
                locationName = locality
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private suspend fun requestApproximateLocation(manager: LocationManager): Location? {
    val provider = LocationManager.NETWORK_PROVIDER
    if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) return null
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
